package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.emit.IndentingWriter;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.source.recovery.TypeRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.utill.Modifiers;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decompiles a ClassFile to Java source code.
 * Produces properly formatted output with class declaration, fields, and methods.
 */
public class ClassDecompiler {

    private final ClassFile classFile;
    private final SSA ssa;
    private final SourceEmitterConfig config;
    private final TypeRecoverer typeRecoverer;

    public ClassDecompiler(ClassFile classFile) {
        this(classFile, SourceEmitterConfig.defaults());
    }

    public ClassDecompiler(ClassFile classFile, SourceEmitterConfig config) {
        this.classFile = classFile;
        this.ssa = new SSA(classFile.getConstPool());
        this.config = config;
        this.typeRecoverer = new TypeRecoverer();
    }

    /**
     * Decompiles the entire class to a Java source string.
     */
    public String decompile() {
        IndentingWriter writer = new IndentingWriter(new StringWriter());
        decompile(writer);
        return writer.toString();
    }

    /**
     * Decompiles the entire class to the given writer.
     */
    public void decompile(IndentingWriter writer) {
        // Package declaration
        String className = classFile.getClassName();
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash > 0) {
            String packageName = className.substring(0, lastSlash).replace('/', '.');
            writer.writeLine("package " + packageName + ";");
            writer.newLine();
        }

        // Import statements (only when using simple names)
        if (!config.isUseFullyQualifiedNames()) {
            emitImports(writer, className);
        }

        // Class declaration
        emitClassDeclaration(writer);
        writer.writeLine(" {");
        writer.newLine();
        writer.indent();

        // Fields
        List<FieldEntry> fields = classFile.getFields();
        if (!fields.isEmpty()) {
            for (FieldEntry field : fields) {
                emitField(writer, field);
            }
            writer.newLine();
        }

        // Static initializer
        MethodEntry clinit = findMethod("<clinit>");
        if (clinit != null && clinit.getCodeAttribute() != null) {
            emitStaticInitializer(writer, clinit);
            writer.newLine();
        }

        // Constructors and methods
        List<MethodEntry> constructors = new ArrayList<>();
        List<MethodEntry> methods = new ArrayList<>();

        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals("<clinit>")) {
                continue; // Already handled
            } else if (method.getName().equals("<init>")) {
                constructors.add(method);
            } else {
                methods.add(method);
            }
        }

        // Emit constructors
        for (MethodEntry ctor : constructors) {
            emitConstructor(writer, ctor);
            writer.newLine();
        }

        // Emit methods
        for (MethodEntry method : methods) {
            emitMethod(writer, method);
            writer.newLine();
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitClassDeclaration(IndentingWriter writer) {
        int access = classFile.getAccess();

        // Modifiers
        StringBuilder sb = new StringBuilder();
        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access)) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");
        if (Modifiers.isAbstract(access) && !Modifiers.isInterface(access)) sb.append("abstract ");
        if (Modifiers.isFinal(access)) sb.append("final ");

        // Type keyword
        if (Modifiers.isAnnotation(access)) {
            sb.append("@interface ");
        } else if (Modifiers.isInterface(access)) {
            sb.append("interface ");
        } else if (Modifiers.isEnum(access)) {
            sb.append("enum ");
        } else {
            sb.append("class ");
        }

        // Simple class name
        String fullName = classFile.getClassName();
        int lastSlash = fullName.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
        sb.append(simpleName);

        // Superclass
        String superName = classFile.getSuperClassName();
        if (superName != null && !superName.equals("java/lang/Object") && !Modifiers.isInterface(access)) {
            sb.append(" extends ").append(formatClassName(superName));
        }

        // Interfaces
        List<Integer> interfaces = classFile.getInterfaces();
        if (interfaces != null && !interfaces.isEmpty()) {
            sb.append(Modifiers.isInterface(access) ? " extends " : " implements ");
            for (int i = 0; i < interfaces.size(); i++) {
                if (i > 0) sb.append(", ");
                String ifaceName = resolveClassName(interfaces.get(i));
                sb.append(formatClassName(ifaceName));
            }
        }

        writer.write(sb.toString());
    }

    private void emitField(IndentingWriter writer, FieldEntry field) {
        int access = field.getAccess();
        StringBuilder sb = new StringBuilder();

        // Modifiers
        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access)) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");
        if (Modifiers.isStatic(access)) sb.append("static ");
        if (Modifiers.isFinal(access)) sb.append("final ");
        if (Modifiers.isVolatile(access)) sb.append("volatile ");
        if (Modifiers.isTransient(access)) sb.append("transient ");

        // Type
        String typeStr = typeRecoverer.recoverType(field.getDesc()).toJavaSource();
        sb.append(typeStr).append(" ");

        // Name
        sb.append(field.getName());

        // TODO: Could recover constant values from ConstantValue attribute

        sb.append(";");
        writer.writeLine(sb.toString());
    }

    private void emitStaticInitializer(IndentingWriter writer, MethodEntry clinit) {
        writer.writeLine("static {");
        writer.indent();

        try {
            IRMethod ir = ssa.lift(clinit);
            BlockStmt body = MethodRecoverer.recoverMethod(ir, clinit);
            emitBlockContents(writer, body);
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile static initializer: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitConstructor(IndentingWriter writer, MethodEntry ctor) {
        int access = ctor.getAccess();

        // Modifiers
        StringBuilder sb = new StringBuilder();
        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access)) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");

        // Simple class name as constructor name
        String fullName = classFile.getClassName();
        int lastSlash = fullName.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
        sb.append(simpleName);

        // Parameters
        sb.append("(");
        sb.append(formatParameters(ctor.getDesc()));
        sb.append(")");

        // Throws clause (TODO: could extract from Exceptions attribute)

        writer.write(sb.toString());

        if (ctor.getCodeAttribute() == null) {
            writer.writeLine(";");
            return;
        }

        writer.writeLine(" {");
        writer.indent();

        try {
            IRMethod ir = ssa.lift(ctor);
            BlockStmt body = MethodRecoverer.recoverMethod(ir, ctor);
            emitBlockContents(writer, body);
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile constructor: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitMethod(IndentingWriter writer, MethodEntry method) {
        int access = method.getAccess();

        // Modifiers
        StringBuilder sb = new StringBuilder();
        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access)) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");
        if (Modifiers.isStatic(access)) sb.append("static ");
        if (Modifiers.isFinal(access)) sb.append("final ");
        if (Modifiers.isAbstract(access)) sb.append("abstract ");
        if (Modifiers.isSynchronized(access)) sb.append("synchronized ");
        if (Modifiers.isNative(access)) sb.append("native ");

        // Return type
        String desc = method.getDesc();
        String returnType = extractReturnType(desc);
        sb.append(returnType).append(" ");

        // Method name
        sb.append(method.getName());

        // Parameters
        sb.append("(");
        sb.append(formatParameters(desc));
        sb.append(")");

        writer.write(sb.toString());

        // Abstract or native methods have no body
        if (Modifiers.isAbstract(access) || Modifiers.isNative(access) || method.getCodeAttribute() == null) {
            writer.writeLine(";");
            return;
        }

        writer.writeLine(" {");
        writer.indent();

        try {
            IRMethod ir = ssa.lift(method);
            BlockStmt body = MethodRecoverer.recoverMethod(ir, method);
            emitBlockContents(writer, body);
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitBlockContents(IndentingWriter writer, BlockStmt block) {
        // Use SourceEmitter to emit the block contents (without the outer braces)
        SourceEmitter emitter = new SourceEmitter(writer, config);
        for (var stmt : block.getStatements()) {
            stmt.accept(emitter);
        }
    }

    private MethodEntry findMethod(String name) {
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private String formatClassName(String internalName) {
        if (internalName == null) return "";
        if (config.isUseFullyQualifiedNames()) {
            return internalName.replace('/', '.');
        }
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private String resolveClassName(int classIndex) {
        try {
            var classRef = (com.tonic.parser.constpool.ClassRefItem) classFile.getConstPool().getItem(classIndex);
            return classRef.getClassName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String extractReturnType(String desc) {
        int parenEnd = desc.indexOf(')');
        if (parenEnd < 0) return "void";
        String returnDesc = desc.substring(parenEnd + 1);
        return typeRecoverer.recoverType(returnDesc).toJavaSource();
    }

    private String formatParameters(String desc) {
        int parenStart = desc.indexOf('(');
        int parenEnd = desc.indexOf(')');
        if (parenStart < 0 || parenEnd < 0) return "";

        String params = desc.substring(parenStart + 1, parenEnd);
        if (params.isEmpty()) return "";

        List<String> paramTypes = new ArrayList<>();
        int i = 0;
        while (i < params.length()) {
            int start = i;
            // Handle arrays
            while (i < params.length() && params.charAt(i) == '[') {
                i++;
            }
            if (i >= params.length()) break;

            char c = params.charAt(i);
            if (c == 'L') {
                // Object type
                int end = params.indexOf(';', i);
                if (end < 0) break;
                String typeDesc = params.substring(start, end + 1);
                paramTypes.add(typeRecoverer.recoverType(typeDesc).toJavaSource());
                i = end + 1;
            } else {
                // Primitive type
                String typeDesc = params.substring(start, i + 1);
                paramTypes.add(typeRecoverer.recoverType(typeDesc).toJavaSource());
                i++;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < paramTypes.size(); j++) {
            if (j > 0) sb.append(", ");
            sb.append(paramTypes.get(j));
            sb.append(" arg").append(j);
        }
        return sb.toString();
    }

    /**
     * Emits import statements by scanning the constant pool for referenced classes.
     */
    private void emitImports(IndentingWriter writer, String thisClassName) {
        Set<String> referencedTypes = collectReferencedTypes();
        String thisPackage = getPackageName(thisClassName);

        List<String> imports = referencedTypes.stream()
                .filter(name -> !isJavaLangClass(name))
                .filter(name -> !getPackageName(name).equals(thisPackage))
                .filter(name -> !name.equals(thisClassName)) // Skip self
                .map(name -> name.replace('/', '.'))
                .sorted()
                .toList();

        if (!imports.isEmpty()) {
            for (String imp : imports) {
                writer.writeLine("import " + imp + ";");
            }
            writer.newLine();
        }
    }

    /**
     * Collects all referenced class types from the constant pool.
     */
    private Set<String> collectReferencedTypes() {
        Set<String> types = new TreeSet<>();

        for (Item<?> item : classFile.getConstPool().getItems()) {
            if (item instanceof ClassRefItem classRef) {
                String className = classRef.getClassName();
                if (className != null && !className.startsWith("[")) {
                    types.add(className);
                }
            }
        }

        return types;
    }

    /**
     * Extracts the package name from an internal class name.
     */
    private String getPackageName(String internalName) {
        if (internalName == null) return "";
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(0, lastSlash) : "";
    }

    /**
     * Checks if a class is in the java.lang package (implicitly imported).
     */
    private boolean isJavaLangClass(String internalName) {
        if (!internalName.startsWith("java/lang/")) {
            return false;
        }
        // java.lang.* is implicit, but java.lang.reflect.*, java.lang.invoke.* etc. are not
        String afterLang = internalName.substring("java/lang/".length());
        return !afterLang.contains("/");
    }

    /**
     * Convenience method to decompile a ClassFile to a string.
     */
    public static String decompile(ClassFile classFile) {
        return new ClassDecompiler(classFile).decompile();
    }

    /**
     * Convenience method to decompile a ClassFile with custom config.
     */
    public static String decompile(ClassFile classFile, SourceEmitterConfig config) {
        return new ClassDecompiler(classFile, config).decompile();
    }
}
