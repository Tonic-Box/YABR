package com.tonic.analysis.source.lower;

import com.tonic.analysis.frame.TypeState;
import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.FieldDecl;
import com.tonic.analysis.source.ast.decl.ImportDecl;
import com.tonic.analysis.source.ast.decl.InterfaceDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.source.ast.decl.TypeDecl;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.util.Modifiers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves source-level type, field and method references to JVM names and descriptors, consulting the
 * class being lowered, the class pool and the running JVM in that order.
 */
public class TypeResolver
{

    private final ClassPool classPool;
    private final String currentClass;

    /**
     * @return the class pool
     */
    public ClassPool getClassPool()
    {
        return classPool;
    }

    /**
     * Creates a resolver over a class pool and installs its superclass lookup as the frame-generation
     * merge resolver.
     *
     * @param classPool the pool holding the classes being lowered
     * @param currentClass the internal name of the class being lowered
     */
    public TypeResolver(ClassPool classPool, String currentClass)
    {
        this.classPool = classPool;
        this.currentClass = currentClass;
        // Frame generation merges reference types at control-flow joins; give it a real
        // common-superclass lookup (backed by the class pool) instead of collapsing to Object.
        TypeState.setSuperclassResolver(this::getSuperclassName);
    }

    /**
     * @return the current class
     */
    public String getCurrentClass()
    {
        return currentClass;
    }

    /**
     * Sets the source declaration consulted before the class pool.
     *
     * @param currentClassDecl the declaration being lowered
     */
    public void setCurrentClassDecl(TypeDecl currentClassDecl)
    {
        this.currentClassDecl = currentClassDecl;
    }

    /**
     * Sets the imports used to qualify simple type names.
     *
     * @param imports the import declarations in source order
     */
    public void setImports(List<ImportDecl> imports)
    {
        this.imports = imports;
    }

    /**
     * Resolves the direct superclass, taking user classes from the pool and falling back to
     * loading JDK and system classes.
     *
     * @param internalName the class to look up
     * @return the superclass internal name, or null for {@code java/lang/Object}, an unresolvable
     *         class, or any lookup failure
     */
    public String getSuperclassName(String internalName)
    {
        if (internalName == null || internalName.isEmpty() || internalName.equals("java/lang/Object"))
        {
            return null;
        }
        try
        {
            ClassFile cf = classPool.get(internalName);
            if (cf == null)
            {
                cf = classPool.loadSystemClass(internalName);
            }
            if (cf == null)
            {
                return null;
            }
            String superClass = cf.getSuperClassName();
            if (superClass == null || superClass.isEmpty() || superClass.startsWith("Invalid"))
            {
                return null;
            }
            return superClass;
        }
        catch (Exception e)
        {
            return null;
        }
    }
    private TypeDecl currentClassDecl;
    private List<ImportDecl> imports = new ArrayList<>();

    /**
     * Resolves a field's declared type, searching the source declaration, the pool class and its
     * supertypes and interfaces, then reflection and the current class's original constant pool.
     *
     * @param ownerClass the owner's class name, simple or internal
     * @param fieldName the field name
     * @return the field type, or null when nothing declares the field
     */
    public SourceType resolveFieldType(String ownerClass, String fieldName)
    {
        if (currentClassDecl != null && isCurrentClass(ownerClass))
        {
            for (FieldDecl field : currentClassDecl.getFields())
            {
                if (field.getName().equals(fieldName))
                {
                    return resolveDeclaredType(field.getType());
                }
            }
            // Not declared here does not mean not a field: an inherited `this.spatial` resolves on a
            // SUPERCLASS, so fall through to the pool walk below rather than giving up at the source view.
        }

        // The owner may arrive as a SIMPLE name (a receiver typed from source text); qualify it through
        // the imports before the pool lookup, else `Vector3f.x` resolves against a class that isn't there.
        ownerClass = normalizeNestedName(resolveClassName(ownerClass));
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            SourceType reflected = reflectFieldType(ownerClass, fieldName);
            return reflected != null ? reflected : fieldTypeFromOriginalPool(ownerClass, fieldName);
        }

        for (FieldEntry field : cf.getFields())
        {
            if (field.getName().equals(fieldName))
            {
                return parseDescriptor(field.getDesc());
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid"))
        {
            SourceType inherited = resolveFieldType(superClass, fieldName);
            if (inherited != null)
            {
                return inherited;
            }
        }
        // A `static final` constant may be declared on an implemented interface rather than a superclass, so the
        // interface set is part of the search - the same walk resolveMethodReturnType already does.
        for (int ifaceIdx : cf.getInterfaces())
        {
            SourceType declared = resolveFieldType(cf.resolveClassName(ifaceIdx), fieldName);
            if (declared != null)
            {
                return declared;
            }
        }

        SourceType reflected = reflectFieldType(ownerClass, fieldName);
        return reflected != null ? reflected : fieldTypeFromOriginalPool(ownerClass, fieldName);
    }

    /**
     * Last resort for a field on a class absent from both the pool and the running JVM (e.g. a binding
     * class the original code was compiled against a stub of): the CURRENT class's own constant pool
     * still carries the original FieldRef with its descriptor, which the original compilation proved.
     */
    private SourceType fieldTypeFromOriginalPool(String ownerClass, String fieldName)
    {
        ClassFile current = classPool.get(currentClass);
        if (current == null || ownerClass == null)
        {
            return null;
        }
        for (Item<?> item : current.getConstPool().getItems())
        {
            if (!(item instanceof FieldRefItem))
            {
                continue;
            }
            FieldRefItem ref = (FieldRefItem) item;
            if (ownerClass.replace('/', '.').equals(ref.getClassName()) && fieldName.equals(ref.getName()))
            {
                return parseDescriptor(ref.getDescriptor());
            }
        }
        return null;
    }

    /**
     * Erases an owner that names a type parameter of the current declaration to its first bound
     * (java/lang/Object when unbounded), so members are resolved against the erased type.
     */
    private String eraseTypeVariable(String ownerClass)
    {
        if (currentClassDecl == null || ownerClass == null
                || ownerClass.indexOf('.') >= 0 || ownerClass.indexOf('/') >= 0)
        {
            return ownerClass;
        }
        List<SourceType> typeParams;
        if (currentClassDecl instanceof ClassDecl)
        {
            typeParams = ((ClassDecl) currentClassDecl).getTypeParameters();
        }
        else if (currentClassDecl instanceof InterfaceDecl)
        {
            typeParams = ((InterfaceDecl) currentClassDecl).getTypeParameters();
        }
        else
        {
            return ownerClass;
        }
        for (SourceType tp : typeParams)
        {
            if (tp instanceof ReferenceSourceType && ((ReferenceSourceType) tp).getInternalName().equals(ownerClass))
            {
                List<SourceType> bounds = ((ReferenceSourceType) tp).getTypeArguments();
                SourceType bound = bounds.isEmpty() ? null : bounds.get(0);
                if (bound instanceof GenericSourceType)
                {
                    bound = ((GenericSourceType) bound).getRawType();
                }
                if (!(bound instanceof ReferenceSourceType))
                {
                    return "java/lang/Object";
                }
                return ((ReferenceSourceType) bound).getInternalName();
            }
        }
        return ownerClass;
    }

    /**
     * Resolves a field's declared type, searching the current class declaration first, then the
     * field tables of the owner class and its superclasses via the ClassPool.
     *
     * @param ownerClass internal name of the declaring class
     * @param fieldName the field to look up
     * @return the declared type, or null if the field cannot be found
     */
    public SourceType findFieldType(String ownerClass, String fieldName)
    {
        if (currentClassDecl != null && isCurrentClass(ownerClass))
        {
            for (FieldDecl field : currentClassDecl.getFields())
            {
                if (field.getName().equals(fieldName))
                {
                    return resolveDeclaredType(field.getType());
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return reflectFieldType(ownerClass, fieldName);
        }

        for (FieldEntry field : cf.getFields())
        {
            if (field.getName().equals(fieldName))
            {
                return parseDescriptor(field.getDesc());
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid"))
        {
            SourceType inherited = findFieldType(superClass, fieldName);
            if (inherited != null)
            {
                return inherited;
            }
        }
        // A `static final` constant may be declared on an implemented interface rather than a superclass, so the
        // interface set is part of the search - the same walk resolveMethodReturnType already does.
        for (int ifaceIdx : cf.getInterfaces())
        {
            SourceType declared = findFieldType(cf.resolveClassName(ifaceIdx), fieldName);
            if (declared != null)
            {
                return declared;
            }
        }

        return reflectFieldType(ownerClass, fieldName);
    }

    /**
     * Resolves a parsed declared type (a current-class field type taken from the source AST) to its fully-qualified
     * internal form via {@link #resolveInternalName} - imports, same package, nested {@code $}. The AST holds the
     * bare name as written ({@code AuthenticationAttemptTracker}); left unresolved, callers that use it as a method
     * owner miss the FQN-keyed {@link ClassPool} and the call's return type falls back to {@code Object}, producing
     * invalid bytecode (an {@code Object} where an {@code int}/return value is expected). Mirrors the FQN that the
     * ClassFile-descriptor branch already yields for non-current classes.
     */
    private SourceType resolveDeclaredType(SourceType type)
    {
        if (type instanceof GenericSourceType)
        {
            // Descriptors carry no generics: resolve the raw type (e.g. DefaultListModel<String> -> the FQN of
            // DefaultListModel). Without this the raw name stays unqualified -> LDefaultListModel; -> NoClassDefFound.
            return resolveDeclaredType(((GenericSourceType) type).getRawType());
        }
        if (type instanceof ReferenceSourceType)
        {
            String name = ((ReferenceSourceType) type).getInternalName();
            String resolved = resolveInternalName(name);
            return resolved == null || resolved.equals(name) ? type : new ReferenceSourceType(resolved);
        }
        if (type instanceof ArraySourceType)
        {
            ArraySourceType array = (ArraySourceType) type;
            SourceType element = resolveDeclaredType(array.getElementType());
            return element == array.getElementType() ? type
                    : new ArraySourceType(element, array.getTotalDimensions());
        }
        return type;
    }

    /**
     * Tests whether a field is declared static, searching the current class declaration first,
     * then the owner class and its superclasses.
     *
     * @param ownerClass internal name of the declaring class
     * @param fieldName the field to look up
     * @return true if the field is static; false if it is not, or cannot be located
     */
    public boolean isStaticField(String ownerClass, String fieldName)
    {
        if (currentClassDecl != null && isCurrentClass(ownerClass))
        {
            for (FieldDecl field : currentClassDecl.getFields())
            {
                if (field.getName().equals(fieldName))
                {
                    return field.isStatic();
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return false;
        }

        for (FieldEntry field : cf.getFields())
        {
            if (field.getName().equals(fieldName))
            {
                return (field.getAccess() & 0x0008) != 0;
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid"))
        {
            if (isStaticField(superClass, fieldName))
            {
                return true;
            }
        }

        // An interface field is implicitly static and final, and is in scope unqualified in every implementor,
        // so the interface set is searched too. Missing it reads the constant as an instance field, which then
        // needs a receiver the enclosing method may not even have.
        for (int ifaceIdx : cf.getInterfaces())
        {
            if (isStaticField(cf.resolveClassName(ifaceIdx), fieldName))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves the single abstract method of a functional interface, looking the interface up in
     * the ClassPool first, then falling back to a table of common JDK functional interfaces.
     *
     * @param interfaceName internal name of the functional interface
     * @return {@code [name, descriptor]} of the abstract method, or null if it cannot be determined
     */
    public String[] resolveSamMethod(String interfaceName)
    {
        if (interfaceName == null || interfaceName.isEmpty())
        {
            return null;
        }

        ClassFile cf = classPool.get(interfaceName);
        if (cf != null)
        {
            for (MethodEntry method : cf.getMethods())
            {
                if (Modifiers.isAbstract(method.getAccess()) && !Modifiers.isStatic(method.getAccess()))
                {
                    return new String[]{method.getName(), method.getDesc()};
                }
            }
        }

        return jdkSamMethod(interfaceName);
    }

    private String[] jdkSamMethod(String interfaceName)
    {
        String simple = interfaceName.contains("/")
            ? interfaceName.substring(interfaceName.lastIndexOf('/') + 1)
            : interfaceName;
        switch (simple)
        {
            case "Runnable":
                return new String[]{"run", "()V"};
            case "Callable":
                return new String[]{"call", "()Ljava/lang/Object;"};
            case "Supplier":
                return new String[]{"get", "()Ljava/lang/Object;"};
            case "Consumer":
                return new String[]{"accept", "(Ljava/lang/Object;)V"};
            case "BiConsumer":
                return new String[]{"accept", "(Ljava/lang/Object;Ljava/lang/Object;)V"};
            case "Function":
            case "UnaryOperator":
                return new String[]{"apply", "(Ljava/lang/Object;)Ljava/lang/Object;"};
            case "BiFunction":
            case "BinaryOperator":
                return new String[]{"apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"};
            case "Predicate":
                return new String[]{"test", "(Ljava/lang/Object;)Z"};
            case "BiPredicate":
                return new String[]{"test", "(Ljava/lang/Object;Ljava/lang/Object;)Z"};
            case "Comparator":
                return new String[]{"compare", "(Ljava/lang/Object;Ljava/lang/Object;)I"};
            default:
                return null;
        }
    }

    /**
     * Parses the return type of a method descriptor.
     *
     * @param methodDescriptor the method descriptor to parse
     * @return the return type
     */
    public SourceType returnTypeFromDescriptor(String methodDescriptor)
    {
        int paren = methodDescriptor.indexOf(')');
        return parseDescriptor(methodDescriptor.substring(paren + 1));
    }

    /**
     * Looks up the descriptor of a directly declared method, used to recover the specific parameter
     * types that a lambda's untyped {@code x ->} source form drops.
     *
     * @param ownerClass internal name of the declaring class
     * @param methodName the method name, assumed unique on the class (e.g. a synthetic {@code lambda$...})
     * @return the method descriptor, or null if the class or method is not found
     */
    public String descriptorOfMethod(String ownerClass, String methodName)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return null;
        }
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName))
            {
                return String.valueOf(method.getDesc());
            }
        }
        return null;
    }

    /**
     * Finds an already-declared synthetic lambda method by position. The compiler numbers lambdas with
     * a per-class counter (e.g. {@code lambda$showError$3}) that a regenerated per-method name cannot
     * reproduce, so a round trip matches on enclosing method plus in-method index instead, recovering
     * the real name and parameter types.
     *
     * @param ownerClass internal name of the class declaring the lambda
     * @param enclosingMethod the method the lambda appears in
     * @param index position among that method's lambdas, ordered by the trailing counter
     * @return {@code [name, descriptor]} of the lambda method, or null if the class or index has no match
     */
    public String[] findLambdaMethod(String ownerClass, String enclosingMethod, int index)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return null;
        }
        String prefix = "lambda$" + LoweringContext.lambdaEnclosingName(enclosingMethod) + "$";
        List<MethodEntry> matches = new ArrayList<>();
        for (MethodEntry method : cf.getMethods())
        {
            String suffix = method.getName().startsWith(prefix)
                    ? method.getName().substring(prefix.length()) : null;
            if (suffix != null && !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit))
            {
                matches.add(method);
            }
        }
        matches.sort(java.util.Comparator.comparingInt(m -> Integer.parseInt(m.getName().substring(prefix.length()))));
        if (index < 0 || index >= matches.size())
        {
            return null;
        }
        MethodEntry chosen = matches.get(index);
        return new String[]{chosen.getName(), String.valueOf(chosen.getDesc())};
    }

    /**
     * Parses the parameter types of a method descriptor.
     *
     * @param methodDescriptor the method descriptor to parse
     * @return the parameter types in declaration order
     */
    public List<SourceType> paramTypesFromDescriptor(String methodDescriptor)
    {
        List<SourceType> result = new ArrayList<>();
        int[] pos = {1};
        while (pos[0] < methodDescriptor.length() && methodDescriptor.charAt(pos[0]) != ')')
        {
            result.add(parseDescriptor(methodDescriptor, pos));
        }
        return result;
    }

    /**
     * Tests whether a method of the class being lowered is static, consulting the parsed class
     * declaration (which may hold methods not yet present on the ClassFile) so that unqualified
     * self-calls can be resolved as static or virtual.
     *
     * @param methodName the method to look up
     * @return true if the method is static; false if it is not, or is not declared
     */
    public boolean isStaticMethodInCurrentClass(String methodName)
    {
        if (currentClassDecl != null)
        {
            for (MethodDecl method : currentClassDecl.getMethods())
            {
                if (method.getName().equals(methodName))
                {
                    return method.isStatic();
                }
            }
        }
        return false;
    }

    private boolean isCurrentClass(String ownerClass)
    {
        if (ownerClass.equals(currentClass))
        {
            return true;
        }
        String simpleCurrentClass = currentClass.contains("/")
            ? currentClass.substring(currentClass.lastIndexOf('/') + 1)
            : currentClass;
        return ownerClass.equals(simpleCurrentClass);
    }

    /**
     * Resolves a call's return type, searching the source declaration, the pool class and its
     * supertypes, then reflection when the exact parameter descriptor does not match.
     *
     * @param ownerClass the receiver's class name, simple or internal
     * @param methodName the method name
     * @param argTypes the argument types at the call site
     * @return the return type, or null when nothing resolves the call
     */
    public SourceType resolveMethodReturnType(String ownerClass, String methodName, List<SourceType> argTypes)
    {
        // The owner may arrive as a SIMPLE name or a type variable; qualify (and erase) it the same
        // way the field paths do, else the pool lookup below misses.
        ownerClass = normalizeNestedName(resolveClassName(ownerClass));
        if (currentClassDecl != null && isCurrentClass(ownerClass))
        {
            for (MethodDecl method : currentClassDecl.getMethods())
            {
                if (method.getName().equals(methodName) && parametersMatch(method.getParameters(), argTypes))
                {
                    return resolveDeclaredType(method.getReturnType());
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return resolveJdkMethodReturnType(ownerClass, methodName, argTypes);
        }

        String expectedParamDesc = buildParamDescriptor(argTypes);

        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName))
            {
                String desc = method.getDesc();
                int parenEnd = desc.indexOf(')');
                String paramPart = desc.substring(1, parenEnd);
                if (paramPart.equals(expectedParamDesc))
                {
                    String returnPart = desc.substring(parenEnd + 1);
                    return parseDescriptor(returnPart);
                }
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null)
        {
            SourceType result = resolveMethodReturnType(superClass, methodName, argTypes);
            if (result != null)
            {
                return result;
            }
        }

        for (int ifaceIdx : cf.getInterfaces())
        {
            String iface = cf.resolveClassName(ifaceIdx);
            SourceType result = resolveMethodReturnType(iface, methodName, argTypes);
            if (result != null)
            {
                return result;
            }
        }

        // The pool search matches the parameter descriptor exactly, so a call with a SUBTYPE argument (e.g.
        // LocalDateTime.isAfter(ChronoLocalDateTime) invoked with a LocalDateTime) finds no match. Fall back to
        // reflection, which matches by name + arity over the full inherited method set - otherwise the return
        // defaults to Object and an ifeq/areturn on it fails verification.
        return reflectMethodReturnType(ownerClass, methodName, argTypes.size());
    }

    private SourceType resolveJdkMethodReturnType(String ownerClass, String methodName, List<SourceType> argTypes)
    {
        if ("java/lang/Object".equals(ownerClass))
        {
            switch (methodName)
            {
                case "hashCode":
                    if (argTypes.isEmpty()) return PrimitiveSourceType.INT;
                    break;
                case "equals":
                    if (argTypes.size() == 1) return PrimitiveSourceType.BOOLEAN;
                    break;
                case "toString":
                    if (argTypes.isEmpty()) return ReferenceSourceType.STRING;
                    break;
                case "getClass":
                    if (argTypes.isEmpty()) return new ReferenceSourceType("java/lang/Class");
                    break;
                case "clone":
                    if (argTypes.isEmpty()) return ReferenceSourceType.OBJECT;
                    break;
                case "notify":
                case "notifyAll":
                case "wait":
                    return VoidSourceType.INSTANCE;
            }
        }
        else if ("java/lang/String".equals(ownerClass))
        {
            switch (methodName)
            {
                case "length":
                    if (argTypes.isEmpty()) return PrimitiveSourceType.INT;
                    break;
                case "charAt":
                    if (argTypes.size() == 1) return PrimitiveSourceType.CHAR;
                    break;
                case "substring":
                    return ReferenceSourceType.STRING;
                case "equals":
                case "equalsIgnoreCase":
                case "startsWith":
                case "endsWith":
                case "contains":
                case "isEmpty":
                    return PrimitiveSourceType.BOOLEAN;
                case "toLowerCase":
                case "toUpperCase":
                case "trim":
                case "concat":
                case "replace":
                case "valueOf":
                    return ReferenceSourceType.STRING;
                case "indexOf":
                case "lastIndexOf":
                case "compareTo":
                case "compareToIgnoreCase":
                    return PrimitiveSourceType.INT;
            }
        }
        return reflectMethodReturnType(ownerClass, methodName, argTypes.size());
    }

    /**
     * Resolves a method's return type by reflecting a classpath-available class - the fallback for JDK/library
     * classes not loaded into the {@link ClassPool} (e.g. {@code javax.swing.SwingUtilities} from the java.desktop
     * module). Without this, an unresolved return defaults to {@code Object}, producing a wrong descriptor (e.g.
     * {@code invokeLater(Runnable)Object}) and a {@code NoSuchMethodError} at run time. Matches by name + parameter
     * count; bails (returns null) when overloads of that arity disagree on the return type, or the class is absent.
     */
    private SourceType reflectMethodReturnType(String ownerClass, String methodName, int paramCount)
    {
        if (ownerClass == null || ownerClass.isEmpty())
        {
            return null;
        }
        try
        {
            Class<?> cls = Class.forName(ownerClass.replace('/', '.'), false, getClass().getClassLoader());
            Class<?> returnType = null;
            for (Method m : cls.getMethods())
            {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount)
                {
                    if (returnType == null)
                    {
                        returnType = m.getReturnType();
                    }
                    else if (!returnType.equals(m.getReturnType()))
                    {
                        return null;
                    }
                }
            }
            return returnType == null ? null : sourceTypeFromClass(returnType);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Resolves a field's declared type by reflecting a classpath-available class - the fallback for JDK/library fields
     * not in the {@link ClassPool} (e.g. {@code java.awt.Color.DARK_GRAY}). Returns null when the class or field is
     * absent. Uses getField so inherited public fields resolve too.
     */
    private SourceType reflectFieldType(String ownerClass, String fieldName)
    {
        if (ownerClass == null || ownerClass.isEmpty())
        {
            return null;
        }
        try
        {
            Class<?> cls = Class.forName(ownerClass.replace('/', '.'), false, getClass().getClassLoader());
            return sourceTypeFromClass(cls.getField(fieldName).getType());
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    /**
     * Maps a reflected {@link Class} to the equivalent {@link SourceType} (void, primitive, array, or reference).
     */
    private SourceType sourceTypeFromClass(Class<?> c)
    {
        if (c == void.class)
        {
            return VoidSourceType.INSTANCE;
        }
        if (c.isPrimitive())
        {
            if (c == boolean.class) return PrimitiveSourceType.BOOLEAN;
            if (c == byte.class) return PrimitiveSourceType.BYTE;
            if (c == char.class) return PrimitiveSourceType.CHAR;
            if (c == short.class) return PrimitiveSourceType.SHORT;
            if (c == int.class) return PrimitiveSourceType.INT;
            if (c == long.class) return PrimitiveSourceType.LONG;
            if (c == float.class) return PrimitiveSourceType.FLOAT;
            if (c == double.class) return PrimitiveSourceType.DOUBLE;
            return null;
        }
        if (c.isArray())
        {
            int dims = 0;
            Class<?> component = c;
            while (component.isArray())
            {
                dims++;
                component = component.getComponentType();
            }
            SourceType element = sourceTypeFromClass(component);
            return element == null ? null : new ArraySourceType(element, dims);
        }
        return new ReferenceSourceType(c.getName().replace('.', '/'));
    }

    private boolean parametersMatch(List<ParameterDecl> params, List<SourceType> argTypes)
    {
        if (params.size() != argTypes.size())
        {
            return false;
        }
        for (int i = 0; i < params.size(); i++)
        {
            SourceType paramType = params.get(i).getType();
            SourceType argType = argTypes.get(i);
            if (argType == null || argType == ReferenceSourceType.OBJECT)
            {
                continue;
            }
            if (!paramType.equals(argType))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Strips one level off an array type.
     *
     * @param arrayType the array type
     * @return the element type
     * @throws LoweringException if the type is not an array
     */
    public SourceType resolveArrayElementType(SourceType arrayType)
    {
        if (arrayType instanceof ArraySourceType)
        {
            return ((ArraySourceType) arrayType).getElementType();
        }
        throw new LoweringException("Not an array type: " + arrayType);
    }

    private String buildParamDescriptor(List<SourceType> argTypes)
    {
        StringBuilder sb = new StringBuilder();
        for (SourceType t : argTypes)
        {
            sb.append(t.toIRType().getDescriptor());
        }
        return sb.toString();
    }

    private SourceType parseDescriptor(String desc)
    {
        return parseDescriptor(desc, new int[]{0});
    }

    private SourceType parseDescriptor(String desc, int[] pos)
    {
        if (pos[0] >= desc.length())
        {
            throw new LoweringException("Invalid descriptor: " + desc);
        }

        char c = desc.charAt(pos[0]++);
        switch (c)
        {
            case 'V':
                return VoidSourceType.INSTANCE;
            case 'Z':
                return PrimitiveSourceType.BOOLEAN;
            case 'B':
                return PrimitiveSourceType.BYTE;
            case 'C':
                return PrimitiveSourceType.CHAR;
            case 'S':
                return PrimitiveSourceType.SHORT;
            case 'I':
                return PrimitiveSourceType.INT;
            case 'J':
                return PrimitiveSourceType.LONG;
            case 'F':
                return PrimitiveSourceType.FLOAT;
            case 'D':
                return PrimitiveSourceType.DOUBLE;
            case 'L':
                int semi = desc.indexOf(';', pos[0]);
                if (semi < 0)
                {
                    throw new LoweringException("Invalid reference descriptor: " + desc);
                }
                String className = desc.substring(pos[0], semi);
                pos[0] = semi + 1;
                return new ReferenceSourceType(className);
            case '[':
                SourceType elementType = parseDescriptor(desc, pos);
                return new ArraySourceType(elementType);
            default:
                throw new LoweringException("Unknown descriptor character: " + c);
        }
    }

    /**
     * Resolves the declared descriptor of the best-matching overload, choosing among same-arity candidates by
     * argument-type compatibility (exact descriptor, then primitive/reference kind) and searching the superclass
     * and interfaces. This yields the real signature the verifier requires for the emitted invoke (e.g.
     * {@code Map.put(Object,Object)}, not the caller's {@code (String,String)}).
     *
     * @param ownerClass internal name of the receiver class
     * @param methodName the method name to match
     * @param argTypes the IR types of the call arguments, in order
     * @return the declared descriptor, or null when the class or a compatible method is not in the pool,
     *         leaving the caller to fall back to the argument types
     */
    public String resolveMethodDescriptor(String ownerClass, String methodName, List<IRType> argTypes)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return null;
        }
        String best = null;
        int bestScore = -1;
        for (MethodEntry method : cf.getMethods())
        {
            if (!method.getName().equals(methodName))
            {
                continue;
            }
            int score = scoreMethodMatch(method, argTypes);
            if (score > bestScore)
            {
                bestScore = score;
                best = method.getDesc();
            }
        }
        if (best != null)
        {
            return best;
        }
        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object"))
        {
            String r = resolveMethodDescriptor(superClass, methodName, argTypes);
            if (r != null)
            {
                return r;
            }
        }
        for (int ifaceIdx : cf.getInterfaces())
        {
            String r = resolveMethodDescriptor(cf.resolveClassName(ifaceIdx), methodName, argTypes);
            if (r != null)
            {
                return r;
            }
        }
        return null;
    }

    /**
     * Resolves the declared type of a constructor parameter that expects a functional argument. A lambda
     * argument cannot type itself, and the one interface-typed slot among same-arity overloads is where
     * it fits ({@code Thread(Runnable)} vs {@code Thread(String)}).
     *
     * @param ownerClass internal name of the class being constructed
     * @param arity the number of constructor arguments
     * @param index position of the parameter to type
     * @return the interface type at that position, or null when no candidate constructor has one or
     *         more than one candidate does
     */
    public SourceType functionalConstructorParamType(String ownerClass, int arity, int index)
    {
        Class<?> owner = loadRuntimeClass(ownerClass);
        if (owner == null)
        {
            return null;
        }
        Class<?> found = null;
        for (java.lang.reflect.Constructor<?> ctor : owner.getDeclaredConstructors())
        {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length != arity || index >= p.length || !p[index].isInterface())
            {
                continue;
            }
            if (found != null && !found.equals(p[index]))
            {
                return null;
            }
            found = p[index];
        }
        return found == null ? null : new ReferenceSourceType(found.getName().replace('.', '/'));
    }

    /**
     * Resolves a descriptor against the running JVM, picking the most specific overload the arguments
     * are assignable to.
     *
     * @param ownerClass the internal name of the declaring class
     * @param methodName the method name, or the JVM constructor name to match constructors instead
     * @param argTypes the argument types at the call site
     * @return the descriptor, or null when the class is not loadable or no overload accepts the arguments
     */
    public String resolveMethodDescriptorViaReflection(String ownerClass, String methodName, List<IRType> argTypes)
    {
        Class<?> owner = loadRuntimeClass(ownerClass);
        if (owner == null)
        {
            return null;
        }
        Class<?>[] args = new Class<?>[argTypes.size()];
        for (int i = 0; i < argTypes.size(); i++)
        {
            Class<?> c = descriptorToClass(argTypes.get(i).getDescriptor());
            if (c == null)
            {
                return null;
            }
            args[i] = c;
        }
        if ("<init>".equals(methodName))
        {
            Class<?>[] best = null;
            for (java.lang.reflect.Constructor<?> ctor : owner.getDeclaredConstructors())
            {
                Class<?>[] p = ctor.getParameterTypes();
                if (paramsAccept(p, args) && (best == null || isAtLeastAsSpecific(p, best)))
                {
                    best = p;
                }
            }
            return best == null ? null : buildRuntimeDescriptor(best, void.class);
        }
        Class<?>[] bestParams = null;
        Class<?> bestReturn = null;
        for (Method m : owner.getMethods())
        {
            if (!m.getName().equals(methodName))
            {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (paramsAccept(p, args) && (bestParams == null || isAtLeastAsSpecific(p, bestParams)))
            {
                bestParams = p;
                bestReturn = m.getReturnType();
            }
        }
        if (bestParams == null)
        {
            // No method takes this many parameters, so consider a varargs callee in its EXPANDED form - the
            // decompiler renders varargs as flat arguments, and the pool-based resolver already matches that
            // way. Second pass, so an exact-arity overload always wins. The DECLARED descriptor is returned
            // (trailing array parameter and all), which is what the invoke needs and what tells the caller to
            // pack the trailing arguments.
            for (Method m : owner.getMethods())
            {
                if (!m.getName().equals(methodName) || !m.isVarArgs())
                {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (!expandedVarargsAccepts(p, args))
                {
                    continue;
                }
                if (bestParams == null || isAtLeastAsSpecific(p, bestParams))
                {
                    bestParams = p;
                    bestReturn = m.getReturnType();
                }
            }
        }
        return bestParams == null ? null : buildRuntimeDescriptor(bestParams, bestReturn);
    }

    /**
     * Whether {@code args} fits {@code params} read as a varargs signature: the fixed parameters taken in
     * order, then every remaining argument accepted by the trailing array's component type.
     */
    private boolean expandedVarargsAccepts(Class<?>[] params, Class<?>[] args)
    {
        int fixed = params.length - 1;
        if (fixed < 0 || args.length < fixed || !params[fixed].isArray())
        {
            return false;
        }
        for (int i = 0; i < fixed; i++)
        {
            if (!accepts(params[i], args[i]))
            {
                return false;
            }
        }
        Class<?> component = params[fixed].getComponentType();
        for (int i = fixed; i < args.length; i++)
        {
            if (!accepts(component, args[i]))
            {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxed(Class<?> c)
    {
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        if (c == boolean.class) return Boolean.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        return c;
    }

    private Class<?> loadRuntimeClass(String internalName)
    {
        try
        {
            return Class.forName(internalName.replace('/', '.'), false, TypeResolver.class.getClassLoader());
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    /**
     * Maps a JVM type descriptor to a runtime Class, or null when it cannot be loaded.
     */
    private Class<?> descriptorToClass(String desc)
    {
        if (desc == null || desc.isEmpty())
        {
            return null;
        }
        switch (desc.charAt(0))
        {
            case 'V': return void.class;
            case 'Z': return boolean.class;
            case 'B': return byte.class;
            case 'C': return char.class;
            case 'S': return short.class;
            case 'I': return int.class;
            case 'J': return long.class;
            case 'F': return float.class;
            case 'D': return double.class;
            case 'L': return loadRuntimeClass(desc.substring(1, desc.length() - 1));
            case '[':
                try
                {
                    return Class.forName(desc.replace('/', '.'), false, TypeResolver.class.getClassLoader());
                }
                catch (Throwable t)
                {
                    return null;
                }
            default: return null;
        }
    }

    /**
     * True when every declared parameter accepts the argument: same primitive, or a reference the (boxed) arg fits.
     */
    private boolean paramsAccept(Class<?>[] params, Class<?>[] args)
    {
        if (params.length != args.length)
        {
            return false;
        }
        for (int i = 0; i < params.length; i++)
        {
            if (!accepts(params[i], args[i]))
            {
                return false;
            }
        }
        return true;
    }

    private boolean accepts(Class<?> param, Class<?> arg)
    {
        if (param.isPrimitive())
        {
            // A boolean value from a comparison is typed int in the IR (the JVM represents both the same on the
            // operand stack), so a boolean parameter accepts an int argument - needed to box `Boolean.valueOf(z)`.
            // char/byte/short are NOT conflated: their append(char)/append(int) overloads have distinct semantics.
            return param == arg || (param == boolean.class && arg == int.class);
        }
        return param.isAssignableFrom(arg.isPrimitive() ? boxed(arg) : arg);
    }

    /**
     * True when parameter list {@code a} is at least as specific as {@code b}, position by position.
     */
    private boolean isAtLeastAsSpecific(Class<?>[] a, Class<?>[] b)
    {
        for (int i = 0; i < a.length; i++)
        {
            if (!moreSpecificOrEqual(a[i], b[i]))
            {
                return false;
            }
        }
        return true;
    }

    private boolean moreSpecificOrEqual(Class<?> a, Class<?> b)
    {
        if (a == b)
        {
            return true;
        }
        if (a.isPrimitive())
        {
            return !b.isPrimitive();
        }
        if (b.isPrimitive())
        {
            return false;
        }
        return b.isAssignableFrom(a);
    }

    private String buildRuntimeDescriptor(Class<?>[] params, Class<?> ret)
    {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params)
        {
            sb.append(classDescriptor(p));
        }
        sb.append(')').append(classDescriptor(ret));
        return sb.toString();
    }

    private String classDescriptor(Class<?> c)
    {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return c.getName().replace('.', '/');
        return "L" + c.getName().replace('.', '/') + ";";
    }

    /**
     * Scores how well {@code method} matches the call argument types: exact descriptor (+2) beats same-kind (+1); any
     * incompatible parameter disqualifies (-1). A varargs method is also considered in its EXPANDED form (fixed
     * parameters + the array component repeated for the trailing args), with a small penalty so a non-varargs exact
     * match wins ties. The exact-arity (direct-array) interpretation of a varargs method only applies when the last
     * argument is actually an array.
     */
    private int scoreMethodMatch(MethodEntry method, List<IRType> argTypes)
    {
        List<String> params = splitParamDescriptors(method.getDesc());
        boolean varargs = (method.getAccess() & 0x0080) != 0
                && !params.isEmpty() && params.get(params.size() - 1).startsWith("[");

        if (params.size() == argTypes.size())
        {
            boolean lastArgArray = !argTypes.isEmpty()
                    && argTypes.get(argTypes.size() - 1).getDescriptor().startsWith("[");
            if (!varargs || lastArgArray)
            {
                int s = scoreParamDescriptors(params, argTypes, params.size());
                if (s >= 0)
                {
                    return s;
                }
            }
        }

        if (varargs)
        {
            int fixedCount = params.size() - 1;
            if (argTypes.size() >= fixedCount)
            {
                int score = scoreParamDescriptors(params, argTypes, fixedCount);
                if (score >= 0)
                {
                    String component = params.get(params.size() - 1).substring(1);
                    boolean ok = true;
                    for (int i = fixedCount; i < argTypes.size(); i++)
                    {
                        int p = scoreParam(component, argTypes.get(i).getDescriptor());
                        if (p < 0)
                        {
                            ok = false;
                            break;
                        }
                        score += p;
                    }
                    if (ok)
                    {
                        return score - 1;
                    }
                }
            }
        }
        return -1;
    }

    private int scoreParamDescriptors(List<String> params, List<IRType> argTypes, int count)
    {
        int score = 0;
        for (int i = 0; i < count; i++)
        {
            int p = scoreParam(params.get(i), argTypes.get(i).getDescriptor());
            if (p < 0)
            {
                return -1;
            }
            score += p;
        }
        return score;
    }

    private static int scoreParam(String paramDesc, String argDesc)
    {
        if (paramDesc.equals(argDesc))
        {
            return 2;
        }
        return isReferenceDescriptor(paramDesc) == isReferenceDescriptor(argDesc) ? 1 : -1;
    }

    /**
     * Tests whether the method with this exact descriptor on the owner (or a supertype) is declared
     * {@code ACC_VARARGS}, falling back to reflection for classes absent from the pool.
     *
     * @param ownerClass internal name of the declaring class
     * @param methodName the method name
     * @param descriptor the exact descriptor to match
     * @return true if that method is varargs
     */
    public boolean isVarargsMethod(String ownerClass, String methodName, String descriptor)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return reflectIsVarargsMethod(ownerClass, methodName, descriptor);
        }
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor))
            {
                return (method.getAccess() & 0x0080) != 0;
            }
        }
        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object"))
        {
            if (isVarargsMethod(superClass, methodName, descriptor))
            {
                return true;
            }
        }
        for (int ifaceIdx : cf.getInterfaces())
        {
            if (isVarargsMethod(cf.resolveClassName(ifaceIdx), methodName, descriptor))
            {
                return true;
            }
        }
        return reflectIsVarargsMethod(ownerClass, methodName, descriptor);
    }

    /**
     * Whether a classpath-available method with this descriptor is declared varargs - the fallback for a callee
     * the {@link ClassPool} does not hold, mirroring {@link #resolveMethodDescriptorViaReflection}. Without it
     * a varargs call resolved by reflection is never packed into its trailing array, so the invoke carries the
     * flat argument descriptor: the class still verifies and fails to link only when the method is called.
     */
    private boolean reflectIsVarargsMethod(String ownerClass, String methodName, String descriptor)
    {
        Class<?> owner = loadRuntimeClass(ownerClass);
        if (owner == null || descriptor == null)
        {
            return false;
        }
        for (Method m : owner.getMethods())
        {
            if (m.getName().equals(methodName)
                    && descriptor.equals(buildRuntimeDescriptor(m.getParameterTypes(), m.getReturnType())))
            {
                return m.isVarArgs();
            }
        }
        return false;
    }

    /**
     * Picks a method descriptor by arity, searching the superclass chain and interfaces when the class
     * declares no overload of that name.
     *
     * @param ownerClass the internal name of the class to search
     * @param methodName the method name
     * @param expectedParamCount the wanted parameter count, negative to skip arity matching
     * @return the descriptor, or null when nothing declares the method
     */
    public String resolveMethodDescriptor(String ownerClass, String methodName, int expectedParamCount)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName))
            {
                candidates.add(method.getDesc());
            }
        }

        if (candidates.isEmpty())
        {
            String superClass = cf.getSuperClassName();
            if (superClass != null && !superClass.equals("java/lang/Object"))
            {
                String result = resolveMethodDescriptor(superClass, methodName, expectedParamCount);
                if (result != null)
                {
                    return result;
                }
            }
            for (int ifaceIdx : cf.getInterfaces())
            {
                String iface = cf.resolveClassName(ifaceIdx);
                String result = resolveMethodDescriptor(iface, methodName, expectedParamCount);
                if (result != null)
                {
                    return result;
                }
            }
            return null;
        }

        if (candidates.size() == 1)
        {
            return candidates.get(0);
        }

        if (expectedParamCount >= 0)
        {
            for (String desc : candidates)
            {
                if (countParams(desc) == expectedParamCount)
                {
                    return desc;
                }
            }
        }

        return candidates.get(0);
    }

    /**
     * Picks the constructor whose parameters best match the given argument IR types (exact descriptor preferred, then
     * same primitive/reference kind), disambiguating same-arity overloads such as {@code ArrayList(int)} vs
     * {@code ArrayList(Collection)}.
     *
     * @param ownerClass internal name of the class being constructed
     * @param argTypes the IR types of the call arguments, in order
     * @return the declared constructor descriptor, or null when the class is absent from the pool or no
     *         kind-compatible constructor exists, leaving the caller to build one from the argument types
     */
    public String resolveConstructorDescriptor(String ownerClass, List<IRType> argTypes)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return null;
        }
        String best = null;
        int bestScore = -1;
        for (MethodEntry method : cf.getMethods())
        {
            if (!method.getName().equals("<init>"))
            {
                continue;
            }
            List<String> params = splitParamDescriptors(method.getDesc());
            if (params.size() != argTypes.size())
            {
                continue;
            }
            int score = 0;
            boolean ok = true;
            for (int i = 0; i < params.size(); i++)
            {
                String p = params.get(i);
                String a = argTypes.get(i).getDescriptor();
                if (p.equals(a))
                {
                    score += 2;
                }
                else if (isReferenceDescriptor(p) == isReferenceDescriptor(a))
                {
                    score += 1;
                }
                else
                {
                    ok = false;
                    break;
                }
            }
            if (ok && score > bestScore)
            {
                bestScore = score;
                best = method.getDesc();
            }
        }
        return best;
    }

    private static boolean isReferenceDescriptor(String desc)
    {
        return !desc.isEmpty() && (desc.charAt(0) == 'L' || desc.charAt(0) == '[');
    }

    private static List<String> splitParamDescriptors(String methodDesc)
    {
        List<String> out = new ArrayList<>();
        int i = methodDesc.indexOf('(') + 1;
        int end = methodDesc.indexOf(')');
        while (i >= 1 && i < end)
        {
            int start = i;
            while (methodDesc.charAt(i) == '[')
            {
                i++;
            }
            if (methodDesc.charAt(i) == 'L')
            {
                i = methodDesc.indexOf(';', i) + 1;
            }
            else
            {
                i++;
            }
            out.add(methodDesc.substring(start, i));
        }
        return out;
    }

    /**
     * Picks a constructor descriptor by arity, preferring the no-arg form when the count does not match.
     *
     * @param ownerClass the internal name of the class being constructed
     * @param expectedParamCount the wanted parameter count, negative to skip arity matching
     * @return the descriptor, or "()V" when the class is absent from the pool or declares no constructor
     */
    public String resolveConstructorDescriptor(String ownerClass, int expectedParamCount)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return "()V";
        }

        List<String> candidates = new ArrayList<>();
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals("<init>"))
            {
                candidates.add(method.getDesc());
            }
        }

        if (candidates.isEmpty())
        {
            return "()V";
        }

        if (candidates.size() == 1)
        {
            return candidates.get(0);
        }

        if (expectedParamCount >= 0)
        {
            for (String desc : candidates)
            {
                if (countParams(desc) == expectedParamCount)
                {
                    return desc;
                }
            }
        }

        for (String desc : candidates)
        {
            if (desc.equals("()V"))
            {
                return desc;
            }
        }

        return candidates.get(0);
    }

    /**
     * Collects every overload of a method name, falling back to the superclass and interfaces when the
     * class itself declares none.
     *
     * @param ownerClass the internal name of the class to search
     * @param methodName the method name
     * @return the matching descriptors, empty if the class is absent from the pool or declares no match
     */
    public List<String> findAllMethodDescriptors(String ownerClass, String methodName)
    {
        List<String> results = new ArrayList<>();
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return results;
        }

        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName))
            {
                results.add(method.getDesc());
            }
        }

        if (results.isEmpty())
        {
            String superClass = cf.getSuperClassName();
            if (superClass != null && !superClass.equals("java/lang/Object"))
            {
                results.addAll(findAllMethodDescriptors(superClass, methodName));
            }
            for (int ifaceIdx : cf.getInterfaces())
            {
                String iface = cf.resolveClassName(ifaceIdx);
                results.addAll(findAllMethodDescriptors(iface, methodName));
            }
        }

        return results;
    }

    /**
     * Checks the ACC_STATIC flag of a method declared on a pooled class.
     *
     * @param ownerClass the internal name of the declaring class
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @return true if the method is declared static, false if it is not or the class is absent from the pool
     */
    public boolean isStaticMethod(String ownerClass, String methodName, String descriptor)
    {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null)
        {
            return false;
        }

        for (MethodEntry method : cf.getMethods())
        {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor))
            {
                return (method.getAccess() & 0x0008) != 0;
            }
        }

        return false;
    }

    private int countParams(String descriptor)
    {
        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')')
        {
            char c = descriptor.charAt(i);
            if (c == 'L')
            {
                while (i < descriptor.length() && descriptor.charAt(i) != ';')
                {
                    i++;
                }
                i++;
                count++;
            }
            else if (c == '[')
            {
                i++;
            }
            else
            {
                i++;
                count++;
            }
        }
        return count;
    }

    /**
     * Resolves a source-qualified name whose separators are all slashes (a naive {@code '.'->'/'} of
     * {@code a.b.C.D}) to its true internal name, recovering the {@code $} nested-class separators. A source
     * dot means either a package boundary or a nested-class boundary, and the two are indistinguishable
     * syntactically; try the all-slash form, then convert trailing separators to {@code $} innermost-first until
     * a known class is found ({@code Outer/Inner} -&gt; {@code Outer$Inner}). Falls back to the all-slash form for a
     * name no loaded class matches, preserving the prior behavior for unresolvable external types.
     */
    private String resolveDottedName(String slashName)
    {
        if (classExists(slashName))
        {
            return slashName;
        }
        StringBuilder sb = new StringBuilder(slashName);
        for (int i = sb.length() - 1; i >= 0; i--)
        {
            if (sb.charAt(i) == '/')
            {
                sb.setCharAt(i, '$');
                if (classExists(sb.toString()))
                {
                    return sb.toString();
                }
            }
        }
        return conventionNestedName(slashName);
    }

    /**
     * The Java-naming-convention reading of a slash-separated name whose class the pool cannot verify:
     * lowercase segments are the package, the first capitalized segment is the outermost class, and every
     * later segment is a nested class - so a nested reference (a/b/Outer/Inner) still resolves to its
     * $-form binary name instead of an all-slash name that links to nothing. Returns the input unchanged
     * when no capitalized segment is followed by further segments.
     */
    private String conventionNestedName(String slashName)
    {
        String[] parts = slashName.split("/");
        int firstClass = -1;
        for (int i = 0; i < parts.length; i++)
        {
            if (!parts[i].isEmpty() && Character.isUpperCase(parts[i].charAt(0)))
            {
                firstClass = i;
                break;
            }
        }
        if (firstClass >= 0 && firstClass < parts.length - 1)
        {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length; i++)
            {
                if (i > 0)
                {
                    out.append(i <= firstClass ? '/' : '$');
                }
                out.append(parts[i]);
            }
            return out.toString();
        }
        return slashName;
    }

    /**
     * Qualifies a type name to its internal form, trying type-variable erasure, explicit and wildcard
     * imports, a java.lang shortlist, the current class, and finally the loaded classes.
     *
     * @param simpleName the name as written in source, simple, dotted or already internal
     * @return the internal name, or the input unchanged when nothing resolves it
     */
    public String resolveClassName(String simpleName)
    {
        if (simpleName.contains("/"))
        {
            return simpleName;
        }
        if (simpleName.contains("."))
        {
            return resolveDottedName(simpleName.replace('.', '/'));
        }

        // A type parameter of the current declaration SHADOWS any same-named class; erase it to its
        // bound before consulting imports or the pool, exactly as javac's erasure does.
        String erased = eraseTypeVariable(simpleName);
        if (!erased.equals(simpleName))
        {
            return resolveClassName(erased);
        }

        for (ImportDecl imp : imports)
        {
            if (!imp.isStatic() && !imp.isWildcard())
            {
                String importName = imp.getName();
                String simpleImport = imp.getSimpleName();
                if (simpleImport.equals(simpleName))
                {
                    return importName.replace('.', '/');
                }
            }
        }

        for (ImportDecl imp : imports)
        {
            if (!imp.isStatic() && imp.isWildcard())
            {
                String packageName = imp.getName().replace('.', '/');
                String candidate = packageName + "/" + simpleName;
                // classExists (not just classPool.get) so a wildcard-imported JDK type whose module isn't loaded
                // into the pool - e.g. java.awt.Frame via `import java.awt.*` - still resolves to its FQN instead
                // of staying a bare simple name (which produces a bad descriptor -> ClassNotFoundException).
                if (classExists(candidate))
                {
                    return candidate;
                }
            }
        }

        if (simpleName.equals("System")) return "java/lang/System";
        if (simpleName.equals("Math")) return "java/lang/Math";
        if (simpleName.equals("String")) return "java/lang/String";
        if (simpleName.equals("Object")) return "java/lang/Object";
        if (simpleName.equals("Integer")) return "java/lang/Integer";
        if (simpleName.equals("Long")) return "java/lang/Long";
        if (simpleName.equals("Double")) return "java/lang/Double";
        if (simpleName.equals("Float")) return "java/lang/Float";
        if (simpleName.equals("Boolean")) return "java/lang/Boolean";
        if (simpleName.equals("Character")) return "java/lang/Character";
        if (simpleName.equals("Byte")) return "java/lang/Byte";
        if (simpleName.equals("Short")) return "java/lang/Short";
        if (simpleName.equals("Class")) return "java/lang/Class";
        if (simpleName.equals("StringBuilder")) return "java/lang/StringBuilder";
        if (simpleName.equals("Thread")) return "java/lang/Thread";
        if (simpleName.equals("Throwable")) return "java/lang/Throwable";
        if (simpleName.equals("Exception")) return "java/lang/Exception";
        if (simpleName.equals("RuntimeException")) return "java/lang/RuntimeException";

        String ownerSimpleName = currentClass.contains("/")
            ? currentClass.substring(currentClass.lastIndexOf('/') + 1)
            : currentClass;
        if (simpleName.equals(ownerSimpleName))
        {
            return currentClass;
        }

        String resolved = resolveFromLoadedClasses(simpleName);
        if (resolved != null)
        {
            return resolved;
        }

        return simpleName;
    }

    /**
     * Resolves a parsed type name to its fully-qualified internal name: applies imports (via
     * {@link #resolveClassName}) for a simple name, then repairs nested-class boundaries that the source
     * spelled with a dot - the decompiler renders {@code Outer.Inner} which naively becomes {@code Outer/Inner},
     * but the JVM internal name is {@code Outer$Inner}. The correct boundary is found by consulting the pool.
     *
     * @param rawName the type name as written in source
     * @return the internal name
     */
    public String resolveInternalName(String rawName)
    {
        return normalizeNestedName(resolveClassName(rawName));
    }

    /**
     * Builds the JVM generic signature of a declared type. Type arguments recurse, and a name that is
     * a type parameter of the current declaration renders as a type-variable use.
     *
     * @param type the declared type
     * @return the signature, or null when the type carries no generic information (a plain reference,
     *         array of plain references, or primitive needs no LocalVariableTypeTable entry)
     */
    public String signatureOf(SourceType type)
    {
        if (!containsGenerics(type))
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendSignature(type, sb);
        return sb.toString();
    }

    private boolean containsGenerics(SourceType type)
    {
        if (type instanceof GenericSourceType)
        {
            return true;
        }
        if (type instanceof ArraySourceType)
        {
            return containsGenerics(((ArraySourceType) type).getElementType());
        }
        if (type instanceof ReferenceSourceType)
        {
            String name = ((ReferenceSourceType) type).getInternalName();
            return isTypeParameterName(name);
        }
        return false;
    }

    private boolean isTypeParameterName(String name)
    {
        if (name == null || name.indexOf('/') >= 0 || name.indexOf('.') >= 0)
        {
            return false;
        }
        return !eraseTypeVariable(name).equals(name);
    }

    private void appendSignature(SourceType type, StringBuilder sb)
    {
        if (type instanceof GenericSourceType)
        {
            GenericSourceType g = (GenericSourceType) type;
            sb.append('L').append(resolveInternalName(g.getRawType().getInternalName()));
            if (!g.getTypeArguments().isEmpty())
            {
                sb.append('<');
                for (SourceType arg : g.getTypeArguments())
                {
                    appendSignature(arg, sb);
                }
                sb.append('>');
            }
            sb.append(';');
            return;
        }
        if (type instanceof WildcardSourceType)
        {
            WildcardSourceType w = (WildcardSourceType) type;
            if (w.isUnbounded())
            {
                sb.append('*');
                return;
            }
            sb.append(w.hasUpperBound() ? '+' : '-');
            appendSignature(w.getBound(), sb);
            return;
        }
        if (type instanceof ArraySourceType)
        {
            ArraySourceType a = (ArraySourceType) type;
            sb.append("[".repeat(Math.max(0, a.getTotalDimensions())));
            appendSignature(a.getElementType(), sb);
            return;
        }
        if (type instanceof ReferenceSourceType)
        {
            String name = ((ReferenceSourceType) type).getInternalName();
            if (isTypeParameterName(name))
            {
                sb.append('T').append(name).append(';');
            }
            else
            {
                sb.append('L').append(resolveInternalName(name)).append(';');
            }
            return;
        }
        sb.append(descriptorOf(type));
    }

    /**
     * Builds the descriptor of a declared parameter. A varargs parameter carries its element type in
     * the declaration, so its descriptor is one array dimension up.
     *
     * @param param the declared parameter
     * @return the field descriptor of the parameter's type
     */
    public String descriptorOf(ParameterDecl param)
    {
        SourceType type = param.getType();
        if (param.isVarArgs())
        {
            type = type instanceof ArraySourceType
                    ? ((ArraySourceType) type).addDimension()
                    : new ArraySourceType(type);
        }
        return descriptorOf(type);
    }

    /**
     * Builds the JVM descriptor for a source type, erasing generics and resolving reference names
     * through the imports and the pool.
     *
     * @param type the source type
     * @return the descriptor
     */
    public String descriptorOf(SourceType type)
    {
        if (type instanceof GenericSourceType)
        {
            return descriptorOf(((GenericSourceType) type).getRawType());
        }
        if (type instanceof ReferenceSourceType)
        {
            return "L" + resolveInternalName(((ReferenceSourceType) type).getInternalName()) + ";";
        }
        if (type instanceof ArraySourceType)
        {
            ArraySourceType array = (ArraySourceType) type;
            return "[".repeat(Math.max(0, array.getTotalDimensions())) +
                    descriptorOf(array.getElementType());
        }
        return type.toIRType().getDescriptor();
    }

    /**
     * Repairs nested-class boundaries in an internal name. A name spelled with {@code /} for every separator
     * (e.g. {@code a/b/Outer/Inner}) is corrected to use {@code $} where a {@code /}-segment is actually a
     * nested class, identified by testing successive boundaries against the pool from the rightmost inward.
     * Returns the input unchanged when it already resolves or no nested form is found.
     */
    private String normalizeNestedName(String internalName)
    {
        if (internalName == null || internalName.isEmpty() || classExists(internalName))
        {
            return internalName;
        }
        char[] chars = internalName.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--)
        {
            if (chars[i] == '/')
            {
                chars[i] = '$';
                String candidate = new String(chars);
                if (classExists(candidate))
                {
                    return candidate;
                }
            }
        }
        return conventionNestedName(internalName);
    }

    /**
     * Tests whether a class is an interface, consulting the ClassPool and then reflection. Used to
     * choose invokeinterface over invokevirtual for calls on interface-typed receivers.
     *
     * @param internalName the class to look up
     * @return true if the class is an interface; false if it is not, or cannot be resolved
     */
    public boolean isInterface(String internalName)
    {
        if (internalName == null || internalName.isEmpty())
        {
            return false;
        }
        ClassFile cf = classPool.get(internalName);
        if (cf == null)
        {
            try
            {
                cf = classPool.loadSystemClass(internalName);
            }
            catch (Exception ignored)
            {
            }
        }
        if (cf != null)
        {
            return (cf.getAccess() & 0x0200) != 0;
        }
        // A JDK callee (e.g. java.util.List) isn't in the pool; without knowing it is an interface the call would
        // wrongly emit invokevirtual instead of invokeinterface (IncompatibleClassChangeError at run time).
        // Modular JDK classes aren't readable as resources, so resolve via reflection, which sees them regardless.
        try
        {
            return Class.forName(internalName.replace('/', '.'), false, getClass().getClassLoader()).isInterface();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    /**
     * Tests whether a class is resolvable via the pool - already loaded, or loadable from the system
     * class path (so e.g. implicitly-imported {@code java.lang} exceptions resolve).
     *
     * @param internalName the class to look up
     * @return true if the class resolves
     */
    public boolean classExists(String internalName)
    {
        if (classPool.get(internalName) != null)
        {
            return true;
        }
        try
        {
            if (classPool.loadSystemClass(internalName) != null)
            {
                return true;
            }
        }
        catch (Exception ignored)
        {
        }
        // Modular JDK classes (e.g. java.desktop's java.awt.Frame) aren't readable via getResourceAsStream, so fall
        // back to reflection, which resolves them regardless of module/resource visibility.
        try
        {
            Class.forName(internalName.replace('/', '.'), false, getClass().getClassLoader());
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private String resolveFromLoadedClasses(String simpleName)
    {
        int currentSlash = currentClass.lastIndexOf('/');
        if (currentSlash > 0)
        {
            String samePackage = currentClass.substring(0, currentSlash + 1) + simpleName;
            if (classPool.get(samePackage) != null)
            {
                return samePackage;
            }
        }

        String javaLang = "java/lang/" + simpleName;
        if (classExists(javaLang))
        {
            return javaLang;
        }

        String fallback = null;
        for (ClassFile cf : classPool.getClasses())
        {
            String name = cf.getClassName();
            int slash = name.lastIndexOf('/');
            String simple = slash < 0 ? name : name.substring(slash + 1);
            if (!simple.equals(simpleName))
            {
                continue;
            }
            if (name.startsWith("java/lang/") || name.startsWith("java/util/"))
            {
                return name;
            }
            if (fallback == null)
            {
                fallback = name;
            }
        }
        return fallback;
    }
}
