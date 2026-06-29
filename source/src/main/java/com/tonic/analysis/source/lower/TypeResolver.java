package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.FieldDecl;
import com.tonic.analysis.source.ast.decl.ImportDecl;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.ast.decl.ParameterDecl;
import com.tonic.analysis.frame.TypeState;
import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.util.Modifiers;

import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.List;

public class TypeResolver {

    private final ClassPool classPool;
    private final String currentClass;

    public TypeResolver(ClassPool classPool, String currentClass) {
        this.classPool = classPool;
        this.currentClass = currentClass;
        // Frame generation merges reference types at control-flow joins; give it a real
        // common-superclass lookup (backed by the class pool) instead of collapsing to Object.
        TypeState.setSuperclassResolver(this::getSuperclassName);
    }

    public String getCurrentClass() {
        return currentClass;
    }

    public void setCurrentClassDecl(ClassDecl currentClassDecl) {
        this.currentClassDecl = currentClassDecl;
    }

    public void setImports(List<ImportDecl> imports) {
        this.imports = imports;
    }

    /**
     * Returns the direct superclass (internal name) of {@code internalName}, or null for
     * {@code java/lang/Object}, unresolvable classes, or on any lookup failure. Resolves user
     * classes from the pool and falls back to loading JDK/system classes.
     */
    public String getSuperclassName(String internalName) {
        if (internalName == null || internalName.isEmpty() || internalName.equals("java/lang/Object")) {
            return null;
        }
        try {
            ClassFile cf = classPool.get(internalName);
            if (cf == null) {
                cf = classPool.loadSystemClass(internalName);
            }
            if (cf == null) {
                return null;
            }
            String superClass = cf.getSuperClassName();
            if (superClass == null || superClass.isEmpty() || superClass.startsWith("Invalid")) {
                return null;
            }
            return superClass;
        } catch (Exception e) {
            return null;
        }
    }
    private ClassDecl currentClassDecl;
    private List<ImportDecl> imports = new ArrayList<>();

    public SourceType resolveFieldType(String ownerClass, String fieldName) {
        if (currentClassDecl != null && isCurrentClass(ownerClass)) {
            for (FieldDecl field : currentClassDecl.getFields()) {
                if (field.getName().equals(fieldName)) {
                    return resolveDeclaredType(field.getType());
                }
            }
            throw new LoweringException("Cannot resolve field: " + ownerClass + "." + fieldName + " in current class");
        }

        ownerClass = normalizeNestedName(ownerClass);
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return reflectFieldType(ownerClass, fieldName);
        }

        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(fieldName)) {
                return parseDescriptor(field.getDesc());
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid")) {
            SourceType inherited = resolveFieldType(superClass, fieldName);
            if (inherited != null) {
                return inherited;
            }
        }

        return reflectFieldType(ownerClass, fieldName);
    }

    /**
     * Resolves a field's declared type, returning null when the field cannot be found
     * instead of throwing. Searches the current class declaration first, then the field
     * tables of the owner class and its superclasses via the ClassPool.
     */
    public SourceType findFieldType(String ownerClass, String fieldName) {
        if (currentClassDecl != null && isCurrentClass(ownerClass)) {
            for (FieldDecl field : currentClassDecl.getFields()) {
                if (field.getName().equals(fieldName)) {
                    return resolveDeclaredType(field.getType());
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return reflectFieldType(ownerClass, fieldName);
        }

        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(fieldName)) {
                return parseDescriptor(field.getDesc());
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid")) {
            SourceType inherited = findFieldType(superClass, fieldName);
            if (inherited != null) {
                return inherited;
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
    private SourceType resolveDeclaredType(SourceType type) {
        if (type instanceof GenericSourceType) {
            // Descriptors carry no generics: resolve the raw type (e.g. DefaultListModel<String> -> the FQN of
            // DefaultListModel). Without this the raw name stays unqualified -> LDefaultListModel; -> NoClassDefFound.
            return resolveDeclaredType(((GenericSourceType) type).getRawType());
        }
        if (type instanceof ReferenceSourceType) {
            String name = ((ReferenceSourceType) type).getInternalName();
            String resolved = resolveInternalName(name);
            return resolved == null || resolved.equals(name) ? type : new ReferenceSourceType(resolved);
        }
        if (type instanceof ArraySourceType) {
            ArraySourceType array = (ArraySourceType) type;
            SourceType element = resolveDeclaredType(array.getElementType());
            return element == array.getElementType() ? type
                    : new ArraySourceType(element, array.getTotalDimensions());
        }
        return type;
    }

    /**
     * Returns whether a field is declared static, searching the current class declaration
     * first, then the owner class and its superclasses. Returns false when the field
     * cannot be located.
     */
    public boolean isStaticField(String ownerClass, String fieldName) {
        if (currentClassDecl != null && isCurrentClass(ownerClass)) {
            for (FieldDecl field : currentClassDecl.getFields()) {
                if (field.getName().equals(fieldName)) {
                    return field.isStatic();
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return false;
        }

        for (FieldEntry field : cf.getFields()) {
            if (field.getName().equals(fieldName)) {
                return (field.getAccess() & 0x0008) != 0;
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object") && !superClass.startsWith("Invalid")) {
            return isStaticField(superClass, fieldName);
        }

        return false;
    }

    /**
     * Resolves the single abstract method (SAM) of a functional interface to its name and
     * descriptor. Looks the interface up in the ClassPool first (handles user/custom interfaces),
     * then falls back to a table of common JDK functional interfaces for types not in the pool.
     * Returns {@code [name, descriptor]} or null when it cannot be determined.
     */
    public String[] resolveSamMethod(String interfaceName) {
        if (interfaceName == null || interfaceName.isEmpty()) {
            return null;
        }

        ClassFile cf = classPool.get(interfaceName);
        if (cf != null) {
            for (MethodEntry method : cf.getMethods()) {
                if (Modifiers.isAbstract(method.getAccess()) && !Modifiers.isStatic(method.getAccess())) {
                    return new String[]{method.getName(), method.getDesc()};
                }
            }
        }

        return jdkSamMethod(interfaceName);
    }

    private String[] jdkSamMethod(String interfaceName) {
        String simple = interfaceName.contains("/")
            ? interfaceName.substring(interfaceName.lastIndexOf('/') + 1)
            : interfaceName;
        switch (simple) {
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
     * Parses the return type of a method descriptor into a SourceType.
     */
    public SourceType returnTypeFromDescriptor(String methodDescriptor) {
        int paren = methodDescriptor.indexOf(')');
        return parseDescriptor(methodDescriptor.substring(paren + 1));
    }

    /**
     * The descriptor of a method declared directly in {@code ownerClass} with the given (assumed unique, e.g.
     * a synthetic {@code lambda$...}) name, or null if the class or method is not found. Used to recover a
     * lambda's specific parameter types on round trip, which the source's untyped {@code x ->} form drops.
     */
    public String descriptorOfMethod(String ownerClass, String methodName) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                return String.valueOf(method.getDesc());
            }
        }
        return null;
    }

    /**
     * The {@code [name, descriptor]} of the {@code index}-th synthetic lambda method enclosed by
     * {@code enclosingMethod} in {@code ownerClass}, ordered by the trailing counter, or null if none. The
     * compiler numbers lambdas with a per-class counter (e.g. {@code lambda$showError$3}) that a per-method
     * regenerated name can't reproduce, so on round trip we match by enclosing method + in-method index
     * instead, recovering the real name and parameter types of the lambda the class already declares.
     */
    public String[] findLambdaMethod(String ownerClass, String enclosingMethod, int index) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }
        String prefix = "lambda$" + enclosingMethod + "$";
        List<MethodEntry> matches = new ArrayList<>();
        for (MethodEntry method : cf.getMethods()) {
            String suffix = method.getName().startsWith(prefix)
                    ? method.getName().substring(prefix.length()) : null;
            if (suffix != null && !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                matches.add(method);
            }
        }
        matches.sort(java.util.Comparator.comparingInt(
                m -> Integer.parseInt(m.getName().substring(prefix.length()))));
        if (index < 0 || index >= matches.size()) {
            return null;
        }
        MethodEntry chosen = matches.get(index);
        return new String[]{chosen.getName(), String.valueOf(chosen.getDesc())};
    }

    /**
     * Parses the parameter types of a method descriptor into a list of SourceTypes.
     */
    public List<SourceType> paramTypesFromDescriptor(String methodDescriptor) {
        List<SourceType> result = new ArrayList<>();
        int[] pos = {1};
        while (pos[0] < methodDescriptor.length() && methodDescriptor.charAt(pos[0]) != ')') {
            result.add(parseDescriptor(methodDescriptor, pos));
        }
        return result;
    }

    /**
     * Returns whether a method declared on the class currently being lowered is static.
     * Consults the parsed class declaration (which may contain methods not yet present on
     * the ClassFile), so unqualified self-calls can be resolved as static or virtual.
     * Returns false when no such method is declared.
     */
    public boolean isStaticMethodInCurrentClass(String methodName) {
        if (currentClassDecl != null) {
            for (MethodDecl method : currentClassDecl.getMethods()) {
                if (method.getName().equals(methodName)) {
                    return method.isStatic();
                }
            }
        }
        return false;
    }

    private boolean isCurrentClass(String ownerClass) {
        if (ownerClass.equals(currentClass)) {
            return true;
        }
        String simpleCurrentClass = currentClass.contains("/")
            ? currentClass.substring(currentClass.lastIndexOf('/') + 1)
            : currentClass;
        return ownerClass.equals(simpleCurrentClass);
    }

    public SourceType resolveMethodReturnType(String ownerClass, String methodName, List<SourceType> argTypes) {
        if (currentClassDecl != null && isCurrentClass(ownerClass)) {
            for (MethodDecl method : currentClassDecl.getMethods()) {
                if (method.getName().equals(methodName) && parametersMatch(method.getParameters(), argTypes)) {
                    return resolveDeclaredType(method.getReturnType());
                }
            }
        }

        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return resolveJdkMethodReturnType(ownerClass, methodName, argTypes);
        }

        String expectedParamDesc = buildParamDescriptor(argTypes);

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                String desc = method.getDesc();
                int parenEnd = desc.indexOf(')');
                String paramPart = desc.substring(1, parenEnd);
                if (paramPart.equals(expectedParamDesc)) {
                    String returnPart = desc.substring(parenEnd + 1);
                    return parseDescriptor(returnPart);
                }
            }
        }

        String superClass = cf.getSuperClassName();
        if (superClass != null) {
            SourceType result = resolveMethodReturnType(superClass, methodName, argTypes);
            if (result != null) {
                return result;
            }
        }

        for (int ifaceIdx : cf.getInterfaces()) {
            String iface = cf.resolveClassName(ifaceIdx);
            SourceType result = resolveMethodReturnType(iface, methodName, argTypes);
            if (result != null) {
                return result;
            }
        }

        // The pool search matches the parameter descriptor exactly, so a call with a SUBTYPE argument (e.g.
        // LocalDateTime.isAfter(ChronoLocalDateTime) invoked with a LocalDateTime) finds no match. Fall back to
        // reflection, which matches by name + arity over the full inherited method set - otherwise the return
        // defaults to Object and an ifeq/areturn on it fails verification.
        return reflectMethodReturnType(ownerClass, methodName, argTypes.size());
    }

    private SourceType resolveJdkMethodReturnType(String ownerClass, String methodName, List<SourceType> argTypes) {
        if ("java/lang/Object".equals(ownerClass)) {
            switch (methodName) {
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
        } else if ("java/lang/String".equals(ownerClass)) {
            switch (methodName) {
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
    private SourceType reflectMethodReturnType(String ownerClass, String methodName, int paramCount) {
        if (ownerClass == null || ownerClass.isEmpty()) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(ownerClass.replace('/', '.'), false, getClass().getClassLoader());
            Class<?> returnType = null;
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == paramCount) {
                    if (returnType == null) {
                        returnType = m.getReturnType();
                    } else if (!returnType.equals(m.getReturnType())) {
                        return null;
                    }
                }
            }
            return returnType == null ? null : sourceTypeFromClass(returnType);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Resolves a field's declared type by reflecting a classpath-available class - the fallback for JDK/library fields
     * not in the {@link ClassPool} (e.g. {@code java.awt.Color.DARK_GRAY}). Returns null when the class or field is
     * absent. Uses getField so inherited public fields resolve too.
     */
    private SourceType reflectFieldType(String ownerClass, String fieldName) {
        if (ownerClass == null || ownerClass.isEmpty()) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(ownerClass.replace('/', '.'), false, getClass().getClassLoader());
            return sourceTypeFromClass(cls.getField(fieldName).getType());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Maps a reflected {@link Class} to the equivalent {@link SourceType} (void, primitive, array, or reference). */
    private SourceType sourceTypeFromClass(Class<?> c) {
        if (c == void.class) {
            return VoidSourceType.INSTANCE;
        }
        if (c.isPrimitive()) {
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
        if (c.isArray()) {
            int dims = 0;
            Class<?> component = c;
            while (component.isArray()) {
                dims++;
                component = component.getComponentType();
            }
            SourceType element = sourceTypeFromClass(component);
            return element == null ? null : new ArraySourceType(element, dims);
        }
        return new ReferenceSourceType(c.getName().replace('.', '/'));
    }

    private boolean parametersMatch(List<ParameterDecl> params, List<SourceType> argTypes) {
        if (params.size() != argTypes.size()) {
            return false;
        }
        for (int i = 0; i < params.size(); i++) {
            SourceType paramType = params.get(i).getType();
            SourceType argType = argTypes.get(i);
            if (argType == null || argType == ReferenceSourceType.OBJECT) {
                continue;
            }
            if (!paramType.equals(argType)) {
                return false;
            }
        }
        return true;
    }

    public SourceType resolveArrayElementType(SourceType arrayType) {
        if (arrayType instanceof ArraySourceType) {
            return ((ArraySourceType) arrayType).getElementType();
        }
        throw new LoweringException("Not an array type: " + arrayType);
    }

    private String buildParamDescriptor(List<SourceType> argTypes) {
        StringBuilder sb = new StringBuilder();
        for (SourceType t : argTypes) {
            sb.append(t.toIRType().getDescriptor());
        }
        return sb.toString();
    }

    private SourceType parseDescriptor(String desc) {
        return parseDescriptor(desc, new int[]{0});
    }

    private SourceType parseDescriptor(String desc, int[] pos) {
        if (pos[0] >= desc.length()) {
            throw new LoweringException("Invalid descriptor: " + desc);
        }

        char c = desc.charAt(pos[0]++);
        switch (c) {
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
                if (semi < 0) {
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
     * Resolves the DECLARED descriptor of the best-matching overload of {@code methodName} on {@code ownerClass},
     * choosing among same-arity candidates by argument-type compatibility (exact descriptor, then primitive/reference
     * kind). This yields the method's real signature for the emitted invoke descriptor (e.g. Map.put(Object,Object),
     * not the caller's (String,String)), which the verifier requires. Searches superclass + interfaces. Returns null
     * when the class or a compatible method is not in the pool, so the caller falls back to the argument types.
     */
    public String resolveMethodDescriptor(String ownerClass, String methodName, List<IRType> argTypes) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }
        String best = null;
        int bestScore = -1;
        for (MethodEntry method : cf.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            int score = scoreMethodMatch(method, argTypes);
            if (score > bestScore) {
                bestScore = score;
                best = method.getDesc();
            }
        }
        if (best != null) {
            return best;
        }
        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object")) {
            String r = resolveMethodDescriptor(superClass, methodName, argTypes);
            if (r != null) {
                return r;
            }
        }
        for (int ifaceIdx : cf.getInterfaces()) {
            String r = resolveMethodDescriptor(cf.resolveClassName(ifaceIdx), methodName, argTypes);
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    /**
     * Scores how well {@code method} matches the call argument types: exact descriptor (+2) beats same-kind (+1); any
     * incompatible parameter disqualifies (-1). A varargs method is also considered in its EXPANDED form (fixed
     * parameters + the array component repeated for the trailing args), with a small penalty so a non-varargs exact
     * match wins ties. The exact-arity (direct-array) interpretation of a varargs method only applies when the last
     * argument is actually an array.
     */
    private int scoreMethodMatch(MethodEntry method, List<IRType> argTypes) {
        List<String> params = splitParamDescriptors(method.getDesc());
        boolean varargs = (method.getAccess() & 0x0080) != 0
                && !params.isEmpty() && params.get(params.size() - 1).startsWith("[");

        if (params.size() == argTypes.size()) {
            boolean lastArgArray = !argTypes.isEmpty()
                    && argTypes.get(argTypes.size() - 1).getDescriptor().startsWith("[");
            if (!varargs || lastArgArray) {
                int s = scoreParamDescriptors(params, argTypes, params.size());
                if (s >= 0) {
                    return s;
                }
            }
        }

        if (varargs) {
            int fixedCount = params.size() - 1;
            if (argTypes.size() >= fixedCount) {
                int score = scoreParamDescriptors(params, argTypes, fixedCount);
                if (score >= 0) {
                    String component = params.get(params.size() - 1).substring(1);
                    boolean ok = true;
                    for (int i = fixedCount; i < argTypes.size(); i++) {
                        int p = scoreParam(component, argTypes.get(i).getDescriptor());
                        if (p < 0) {
                            ok = false;
                            break;
                        }
                        score += p;
                    }
                    if (ok) {
                        return score - 1;
                    }
                }
            }
        }
        return -1;
    }

    private int scoreParamDescriptors(List<String> params, List<IRType> argTypes, int count) {
        int score = 0;
        for (int i = 0; i < count; i++) {
            int p = scoreParam(params.get(i), argTypes.get(i).getDescriptor());
            if (p < 0) {
                return -1;
            }
            score += p;
        }
        return score;
    }

    private static int scoreParam(String paramDesc, String argDesc) {
        if (paramDesc.equals(argDesc)) {
            return 2;
        }
        return isReferenceDescriptor(paramDesc) == isReferenceDescriptor(argDesc) ? 1 : -1;
    }

    /** Whether the method with this exact descriptor on the owner (or a supertype) is declared {@code ACC_VARARGS}. */
    public boolean isVarargsMethod(String ownerClass, String methodName, String descriptor) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return false;
        }
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor)) {
                return (method.getAccess() & 0x0080) != 0;
            }
        }
        String superClass = cf.getSuperClassName();
        if (superClass != null && !superClass.equals("java/lang/Object")) {
            if (isVarargsMethod(superClass, methodName, descriptor)) {
                return true;
            }
        }
        for (int ifaceIdx : cf.getInterfaces()) {
            if (isVarargsMethod(cf.resolveClassName(ifaceIdx), methodName, descriptor)) {
                return true;
            }
        }
        return false;
    }

    public String resolveMethodDescriptor(String ownerClass, String methodName, int expectedParamCount) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                candidates.add(method.getDesc());
            }
        }

        if (candidates.isEmpty()) {
            String superClass = cf.getSuperClassName();
            if (superClass != null && !superClass.equals("java/lang/Object")) {
                String result = resolveMethodDescriptor(superClass, methodName, expectedParamCount);
                if (result != null) {
                    return result;
                }
            }
            for (int ifaceIdx : cf.getInterfaces()) {
                String iface = cf.resolveClassName(ifaceIdx);
                String result = resolveMethodDescriptor(iface, methodName, expectedParamCount);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (expectedParamCount >= 0) {
            for (String desc : candidates) {
                if (countParams(desc) == expectedParamCount) {
                    return desc;
                }
            }
        }

        return candidates.get(0);
    }

    /**
     * Picks the constructor whose parameters best match the given argument IR types (exact descriptor preferred, then
     * same primitive/reference kind), disambiguating same-arity overloads such as {@code ArrayList(int)} vs
     * {@code ArrayList(Collection)}. Returns null when the class is absent from the pool or no kind-compatible
     * constructor exists, so the caller can fall back to building a descriptor from the argument value types.
     */
    public String resolveConstructorDescriptor(String ownerClass, List<IRType> argTypes) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return null;
        }
        String best = null;
        int bestScore = -1;
        for (MethodEntry method : cf.getMethods()) {
            if (!method.getName().equals("<init>")) {
                continue;
            }
            List<String> params = splitParamDescriptors(method.getDesc());
            if (params.size() != argTypes.size()) {
                continue;
            }
            int score = 0;
            boolean ok = true;
            for (int i = 0; i < params.size(); i++) {
                String p = params.get(i);
                String a = argTypes.get(i).getDescriptor();
                if (p.equals(a)) {
                    score += 2;
                } else if (isReferenceDescriptor(p) == isReferenceDescriptor(a)) {
                    score += 1;
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok && score > bestScore) {
                bestScore = score;
                best = method.getDesc();
            }
        }
        return best;
    }

    private static boolean isReferenceDescriptor(String desc) {
        return !desc.isEmpty() && (desc.charAt(0) == 'L' || desc.charAt(0) == '[');
    }

    private static List<String> splitParamDescriptors(String methodDesc) {
        List<String> out = new ArrayList<>();
        int i = methodDesc.indexOf('(') + 1;
        int end = methodDesc.indexOf(')');
        while (i >= 1 && i < end) {
            int start = i;
            while (methodDesc.charAt(i) == '[') {
                i++;
            }
            if (methodDesc.charAt(i) == 'L') {
                i = methodDesc.indexOf(';', i) + 1;
            } else {
                i++;
            }
            out.add(methodDesc.substring(start, i));
        }
        return out;
    }

    public String resolveConstructorDescriptor(String ownerClass, int expectedParamCount) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return "()V";
        }

        List<String> candidates = new ArrayList<>();
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals("<init>")) {
                candidates.add(method.getDesc());
            }
        }

        if (candidates.isEmpty()) {
            return "()V";
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (expectedParamCount >= 0) {
            for (String desc : candidates) {
                if (countParams(desc) == expectedParamCount) {
                    return desc;
                }
            }
        }

        for (String desc : candidates) {
            if (desc.equals("()V")) {
                return desc;
            }
        }

        return candidates.get(0);
    }

    public List<String> findAllMethodDescriptors(String ownerClass, String methodName) {
        List<String> results = new ArrayList<>();
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return results;
        }

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName)) {
                results.add(method.getDesc());
            }
        }

        if (results.isEmpty()) {
            String superClass = cf.getSuperClassName();
            if (superClass != null && !superClass.equals("java/lang/Object")) {
                results.addAll(findAllMethodDescriptors(superClass, methodName));
            }
            for (int ifaceIdx : cf.getInterfaces()) {
                String iface = cf.resolveClassName(ifaceIdx);
                results.addAll(findAllMethodDescriptors(iface, methodName));
            }
        }

        return results;
    }

    public boolean isStaticMethod(String ownerClass, String methodName, String descriptor) {
        ClassFile cf = classPool.get(ownerClass);
        if (cf == null) {
            return false;
        }

        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(methodName) && method.getDesc().equals(descriptor)) {
                return (method.getAccess() & 0x0008) != 0;
            }
        }

        return false;
    }

    private int countParams(String descriptor) {
        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                while (i < descriptor.length() && descriptor.charAt(i) != ';') {
                    i++;
                }
                i++;
                count++;
            } else if (c == '[') {
                i++;
            } else {
                i++;
                count++;
            }
        }
        return count;
    }

    public String resolveClassName(String simpleName) {
        if (simpleName.contains("/")) {
            return simpleName;
        }
        if (simpleName.contains(".")) {
            return simpleName.replace('.', '/');
        }

        for (ImportDecl imp : imports) {
            if (!imp.isStatic() && !imp.isWildcard()) {
                String importName = imp.getName();
                String simpleImport = imp.getSimpleName();
                if (simpleImport.equals(simpleName)) {
                    return importName.replace('.', '/');
                }
            }
        }

        for (ImportDecl imp : imports) {
            if (!imp.isStatic() && imp.isWildcard()) {
                String packageName = imp.getName().replace('.', '/');
                String candidate = packageName + "/" + simpleName;
                // classExists (not just classPool.get) so a wildcard-imported JDK type whose module isn't loaded
                // into the pool - e.g. java.awt.Frame via `import java.awt.*` - still resolves to its FQN instead
                // of staying a bare simple name (which produces a bad descriptor -> ClassNotFoundException).
                if (classExists(candidate)) {
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
        if (simpleName.equals(ownerSimpleName)) {
            return currentClass;
        }

        String resolved = resolveFromLoadedClasses(simpleName);
        if (resolved != null) {
            return resolved;
        }

        return simpleName;
    }

    /**
     * Resolves a parsed type name to its fully-qualified internal name: applies imports (via
     * {@link #resolveClassName}) for a simple name, then repairs nested-class boundaries that the source
     * spelled with a dot - the decompiler renders {@code Outer.Inner} which naively becomes {@code Outer/Inner},
     * but the JVM internal name is {@code Outer$Inner}. The correct boundary is found by consulting the pool.
     */
    public String resolveInternalName(String rawName) {
        return normalizeNestedName(resolveClassName(rawName));
    }

    /**
     * A JVM type descriptor for {@code type} with reference types fully resolved (imports + nested {@code $}).
     * Unlike {@code type.toIRType().getDescriptor()} - which emits the unresolved, possibly mis-nested name as
     * written in source - this yields the descriptor that matches the compiled class, so method signatures
     * built from decompiled source line up with the originals.
     */
    public String descriptorOf(SourceType type) {
        if (type instanceof GenericSourceType) {
            return descriptorOf(((GenericSourceType) type).getRawType());
        }
        if (type instanceof ReferenceSourceType) {
            return "L" + resolveInternalName(((ReferenceSourceType) type).getInternalName()) + ";";
        }
        if (type instanceof ArraySourceType) {
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
    private String normalizeNestedName(String internalName) {
        if (internalName == null || internalName.isEmpty() || classExists(internalName)) {
            return internalName;
        }
        char[] chars = internalName.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--) {
            if (chars[i] == '/') {
                chars[i] = '$';
                String candidate = new String(chars);
                if (classExists(candidate)) {
                    return candidate;
                }
            }
        }
        return internalName;
    }

    /**
     * Returns whether the named class is an interface, consulting the ClassPool. Used to choose
     * invokeinterface over invokevirtual for calls on interface-typed receivers.
     */
    public boolean isInterface(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return false;
        }
        ClassFile cf = classPool.get(internalName);
        return cf != null && (cf.getAccess() & 0x0200) != 0;
    }

    /** Whether {@code internalName} names a class resolvable via the pool — already loaded, or loadable
     * from the system class path (so e.g. implicitly-imported {@code java.lang} exceptions resolve). */
    public boolean classExists(String internalName) {
        if (classPool.get(internalName) != null) {
            return true;
        }
        try {
            if (classPool.loadSystemClass(internalName) != null) {
                return true;
            }
        } catch (Exception ignored) {
        }
        // Modular JDK classes (e.g. java.desktop's java.awt.Frame) aren't readable via getResourceAsStream, so fall
        // back to reflection, which resolves them regardless of module/resource visibility.
        try {
            Class.forName(internalName.replace('/', '.'), false, getClass().getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String resolveFromLoadedClasses(String simpleName) {
        int currentSlash = currentClass.lastIndexOf('/');
        if (currentSlash > 0) {
            String samePackage = currentClass.substring(0, currentSlash + 1) + simpleName;
            if (classPool.get(samePackage) != null) {
                return samePackage;
            }
        }

        String javaLang = "java/lang/" + simpleName;
        if (classExists(javaLang)) {
            return javaLang;
        }

        String fallback = null;
        for (ClassFile cf : classPool.getClasses()) {
            String name = cf.getClassName();
            int slash = name.lastIndexOf('/');
            String simple = slash < 0 ? name : name.substring(slash + 1);
            if (!simple.equals(simpleName)) {
                continue;
            }
            if (name.startsWith("java/lang/") || name.startsWith("java/util/")) {
                return name;
            }
            if (fallback == null) {
                fallback = name;
            }
        }
        return fallback;
    }
}
