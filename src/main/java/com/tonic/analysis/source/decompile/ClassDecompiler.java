package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.transform.ASTTransform;
import com.tonic.analysis.source.ast.transform.ControlFlowSimplifier;
import com.tonic.analysis.source.emit.IndentingWriter;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.source.recovery.TypeRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.transform.ControlFlowReducibility;
import com.tonic.analysis.ssa.transform.DuplicateBlockMerging;
import com.tonic.analysis.ssa.transform.IRTransform;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.ConstantValueAttribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.RuntimeInvisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.attribute.anotation.ElementValue;
import com.tonic.parser.attribute.anotation.ElementValuePair;
import com.tonic.parser.attribute.anotation.EnumConst;
import com.tonic.parser.constpool.*;
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
    private final SourceEmitterConfig emitterConfig;
    private final DecompilerConfig decompilerConfig;
    private final TypeRecoverer typeRecoverer;
    private final ControlFlowReducibility reducibility;
    private final DuplicateBlockMerging duplicateMerging;
    private final ControlFlowSimplifier astSimplifier;

    public ClassDecompiler(ClassFile classFile) {
        this(classFile, DecompilerConfig.defaults());
    }

    public ClassDecompiler(ClassFile classFile, SourceEmitterConfig config) {
        this(classFile, DecompilerConfig.builder().emitterConfig(config).build());
    }

    public ClassDecompiler(ClassFile classFile, DecompilerConfig config) {
        this.classFile = classFile;
        this.ssa = new SSA(classFile.getConstPool());
        this.decompilerConfig = config != null ? config : DecompilerConfig.defaults();
        this.emitterConfig = decompilerConfig.getEmitterConfig();
        this.typeRecoverer = new TypeRecoverer();
        this.reducibility = new ControlFlowReducibility();
        this.duplicateMerging = new DuplicateBlockMerging();
        this.astSimplifier = new ControlFlowSimplifier();
    }

    /**
     * Creates a builder for configuring a ClassDecompiler.
     *
     * @param classFile the class file to decompile
     * @return a new builder instance
     */
    public static Builder builder(ClassFile classFile) {
        return new Builder(classFile);
    }

    /**
     * Builder for ClassDecompiler with fluent configuration API.
     */
    public static class Builder {
        private final ClassFile classFile;
        private final DecompilerConfig.Builder configBuilder = DecompilerConfig.builder();

        private Builder(ClassFile classFile) {
            this.classFile = classFile;
        }

        /**
         * Sets the source emitter configuration.
         */
        public Builder config(SourceEmitterConfig config) {
            configBuilder.emitterConfig(config);
            return this;
        }

        /**
         * Applies a transform preset.
         */
        public Builder preset(TransformPreset preset) {
            configBuilder.preset(preset);
            return this;
        }

        /**
         * Adds a single transform to the pipeline.
         */
        public Builder addTransform(IRTransform transform) {
            configBuilder.addTransform(transform);
            return this;
        }

        /**
         * Adds multiple transforms to the pipeline.
         */
        public Builder addTransforms(List<IRTransform> transforms) {
            configBuilder.addTransforms(transforms);
            return this;
        }

        /**
         * Builds the ClassDecompiler with the configured settings.
         */
        public ClassDecompiler build() {
            return new ClassDecompiler(classFile, configBuilder.build());
        }
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
        if (!emitterConfig.isUseFullyQualifiedNames()) {
            emitImports(writer, className);
        }

        // Class annotations
        emitClassAnnotations(writer);

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
        // Field annotations
        emitFieldAnnotations(writer, field);

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

        // Recover constant value from ConstantValue attribute if present
        String constantValue = getConstantValue(field);
        if (constantValue != null) {
            sb.append(" = ").append(constantValue);
        }

        sb.append(";");
        writer.writeLine(sb.toString());
    }

    /**
     * Extracts the constant value from a field's ConstantValue attribute if present.
     *
     * @param field the field entry
     * @return the constant value as a string, or null if no constant value
     */
    private String getConstantValue(FieldEntry field) {
        if (field.getAttributes() == null) return null;

        for (Attribute attr : field.getAttributes()) {
            if (attr instanceof ConstantValueAttribute) {
                ConstantValueAttribute cva = (ConstantValueAttribute) attr;
                int cpIndex = cva.getConstantValueIndex();
                Item item = classFile.getConstPool().getItem(cpIndex);

                if (item instanceof IntegerItem) {
                    IntegerItem intItem = (IntegerItem) item;
                    // Handle boolean type specially
                    if ("Z".equals(field.getDesc())) {
                        return intItem.getValue() != 0 ? "true" : "false";
                    }
                    // Handle char type
                    if ("C".equals(field.getDesc())) {
                        char c = (char) intItem.getValue().intValue();
                        if (c >= 32 && c < 127 && c != '\'' && c != '\\') {
                            return "'" + c + "'";
                        }
                        return "'" + String.format("\\u%04x", (int) c) + "'";
                    }
                    return String.valueOf(intItem.getValue().intValue());
                } else if (item instanceof LongItem) {
                    LongItem longItem = (LongItem) item;
                    return longItem.getValue() + "L";
                } else if (item instanceof FloatItem) {
                    FloatItem floatItem = (FloatItem) item;
                    float f = floatItem.getValue();
                    if (Float.isNaN(f)) return "Float.NaN";
                    if (f == Float.POSITIVE_INFINITY) return "Float.POSITIVE_INFINITY";
                    if (f == Float.NEGATIVE_INFINITY) return "Float.NEGATIVE_INFINITY";
                    return f + "f";
                } else if (item instanceof DoubleItem) {
                    DoubleItem doubleItem = (DoubleItem) item;
                    double d = doubleItem.getValue();
                    if (Double.isNaN(d)) return "Double.NaN";
                    if (d == Double.POSITIVE_INFINITY) return "Double.POSITIVE_INFINITY";
                    if (d == Double.NEGATIVE_INFINITY) return "Double.NEGATIVE_INFINITY";
                    return String.valueOf(d);
                } else if (item instanceof Utf8Item) {
                    Utf8Item utf8Item = (Utf8Item) item;
                    // String constant - escape special characters
                    return "\"" + escapeString(utf8Item.getValue()) + "\"";
                } else if (item instanceof StringRefItem) {
                    StringRefItem strItem = (StringRefItem) item;
                    Utf8Item strUtf8 = (Utf8Item) classFile.getConstPool().getItem(strItem.getValue());
                    if (strUtf8 != null) {
                        return "\"" + escapeString(strUtf8.getValue()) + "\"";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Escapes special characters in a string for Java source output.
     */
    private String escapeString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Applies baseline transforms (ControlFlowReducibility, DuplicateBlockMerging) to the IR method.
     * Used for static initializers and constructors where these transforms are known to work.
     *
     * @param ir the IR method to transform
     */
    private void applyBaselineTransforms(IRMethod ir) {
        reducibility.run(ir);
        duplicateMerging.run(ir);
    }

    /**
     * Applies additional transforms from the config to the IR method.
     *
     * @param ir the IR method to transform
     */
    private void applyAdditionalTransforms(IRMethod ir) {
        for (IRTransform transform : decompilerConfig.getAdditionalTransforms()) {
            transform.run(ir);
        }
    }

    private void emitStaticInitializer(IndentingWriter writer, MethodEntry clinit) {
        writer.writeLine("static {");
        writer.indent();

        try {
            IRMethod ir = ssa.lift(clinit);
            applyBaselineTransforms(ir);
            applyAdditionalTransforms(ir);
            BlockStmt body = MethodRecoverer.recoverMethod(ir, clinit);
            astSimplifier.transform(body);
            emitBlockContents(writer, body);
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile static initializer: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitConstructor(IndentingWriter writer, MethodEntry ctor) {
        // Constructor annotations
        emitMethodAnnotations(writer, ctor);

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
            applyBaselineTransforms(ir);
            applyAdditionalTransforms(ir);
            BlockStmt body = MethodRecoverer.recoverMethod(ir, ctor);
            astSimplifier.transform(body);
            emitBlockContents(writer, body);
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile constructor: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitMethod(IndentingWriter writer, MethodEntry method) {
        // Method annotations
        emitMethodAnnotations(writer, method);

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
            // NOTE: Baseline transforms (reducibility, duplicateMerging) are NOT applied
            // to regular methods as they can cause issues with complex control flow.
            // Only additional transforms from config are applied.
            applyAdditionalTransforms(ir);
            BlockStmt body = MethodRecoverer.recoverMethod(ir, method);
            astSimplifier.transform(body);
            emitBlockContents(writer, body);
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitBlockContents(IndentingWriter writer, BlockStmt block) {
        // Use SourceEmitter to emit the block contents (without the outer braces)
        SourceEmitter emitter = new SourceEmitter(writer, emitterConfig);
        for (com.tonic.analysis.source.ast.stmt.Statement stmt : block.getStatements()) {
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
        if (emitterConfig.isUseFullyQualifiedNames()) {
            return internalName.replace('/', '.');
        }
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private String resolveClassName(int classIndex) {
        try {
            com.tonic.parser.constpool.ClassRefItem classRef = (com.tonic.parser.constpool.ClassRefItem) classFile.getConstPool().getItem(classIndex);
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
                .collect(java.util.stream.Collectors.toList());

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
            if (item instanceof ClassRefItem) {
                ClassRefItem classRef = (ClassRefItem) item;
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

    // ========== Annotation Support ==========

    /**
     * Emits annotations for a class from its attributes.
     */
    private void emitClassAnnotations(IndentingWriter writer) {
        emitAnnotationsFromAttributes(writer, classFile.getClassAttributes());
    }

    /**
     * Emits annotations for a field.
     */
    private void emitFieldAnnotations(IndentingWriter writer, FieldEntry field) {
        if (field.getAttributes() != null) {
            emitAnnotationsFromAttributes(writer, field.getAttributes());
        }
    }

    /**
     * Emits annotations for a method or constructor.
     */
    private void emitMethodAnnotations(IndentingWriter writer, MethodEntry method) {
        if (method.getAttributes() != null) {
            emitAnnotationsFromAttributes(writer, method.getAttributes());
        }
    }

    /**
     * Emits annotations from a list of attributes.
     */
    private void emitAnnotationsFromAttributes(IndentingWriter writer, List<Attribute> attributes) {
        if (attributes == null) return;

        for (Attribute attr : attributes) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute annAttr = (RuntimeVisibleAnnotationsAttribute) attr;
                for (Annotation ann : annAttr.getAnnotations()) {
                    emitAnnotation(writer, ann);
                }
            } else if (attr instanceof RuntimeInvisibleAnnotationsAttribute) {
                RuntimeInvisibleAnnotationsAttribute annAttr = (RuntimeInvisibleAnnotationsAttribute) attr;
                for (Annotation ann : annAttr.getAnnotations()) {
                    emitAnnotation(writer, ann);
                }
            }
        }
    }

    /**
     * Emits a single annotation.
     */
    private void emitAnnotation(IndentingWriter writer, Annotation ann) {
        StringBuilder sb = new StringBuilder();
        sb.append("@");

        // Get annotation type name
        String typeName = resolveAnnotationType(ann.getTypeIndex());
        sb.append(formatAnnotationTypeName(typeName));

        // Emit element-value pairs if present
        List<ElementValuePair> pairs = ann.getElementValuePairs();
        if (pairs != null && !pairs.isEmpty()) {
            sb.append("(");
            if (pairs.size() == 1 && "value".equals(pairs.get(0).getElementName())) {
                // Single "value" element - use shorthand
                sb.append(formatElementValue(pairs.get(0).getValue()));
            } else {
                // Multiple elements or named element
                for (int i = 0; i < pairs.size(); i++) {
                    if (i > 0) sb.append(", ");
                    ElementValuePair pair = pairs.get(i);
                    sb.append(pair.getElementName());
                    sb.append(" = ");
                    sb.append(formatElementValue(pair.getValue()));
                }
            }
            sb.append(")");
        }

        writer.writeLine(sb.toString());
    }

    /**
     * Resolves an annotation type name from a constant pool index.
     */
    private String resolveAnnotationType(int typeIndex) {
        Item<?> item = classFile.getConstPool().getItem(typeIndex);
        if (item instanceof Utf8Item) {
            return ((Utf8Item) item).getValue();
        }
        return "Unknown";
    }

    /**
     * Formats an annotation type name for output.
     * Converts descriptor format (Ljava/lang/Override;) to simple name (Override).
     */
    private String formatAnnotationTypeName(String typeName) {
        // Remove leading 'L' and trailing ';' if present (descriptor format)
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
            typeName = typeName.substring(1, typeName.length() - 1);
        }

        // Convert slashes to dots
        typeName = typeName.replace('/', '.');

        // Use simple name if not using fully qualified names
        if (!emitterConfig.isUseFullyQualifiedNames()) {
            int lastDot = typeName.lastIndexOf('.');
            if (lastDot >= 0) {
                typeName = typeName.substring(lastDot + 1);
            }
        }

        return typeName;
    }

    /**
     * Formats an element value for annotation output.
     */
    private String formatElementValue(ElementValue ev) {
        int tag = ev.getTag();
        Object value = ev.getValue();

        switch (tag) {
            case 'B': // byte
            case 'S': // short
            case 'I': // int
                return formatConstantValue((Integer) value);

            case 'J': // long
                return formatConstantValue((Integer) value) + "L";

            case 'F': // float
                return formatConstantValue((Integer) value) + "f";

            case 'D': // double
                return formatConstantValue((Integer) value);

            case 'C': // char
                return formatCharConstant((Integer) value);

            case 'Z': // boolean
                return formatBooleanConstant((Integer) value);

            case 's': // String
                return formatStringConstant((Integer) value);

            case 'e': // enum
                EnumConst enumConst = (EnumConst) value;
                return formatEnumConstant(enumConst);

            case 'c': // class
                return formatClassConstant((Integer) value);

            case '@': // annotation
                return formatNestedAnnotation((Annotation) value);

            case '[': // array
                return formatArrayValue((List<ElementValue>) value);

            default:
                return "/* unknown element value tag: " + (char) tag + " */";
        }
    }

    /**
     * Formats a constant pool value.
     */
    private String formatConstantValue(int cpIndex) {
        Item<?> item = classFile.getConstPool().getItem(cpIndex);

        if (item instanceof IntegerItem) {
            return String.valueOf(((IntegerItem) item).getValue());
        } else if (item instanceof LongItem) {
            return String.valueOf(((LongItem) item).getValue());
        } else if (item instanceof FloatItem) {
            float f = ((FloatItem) item).getValue();
            if (Float.isNaN(f)) return "Float.NaN";
            if (f == Float.POSITIVE_INFINITY) return "Float.POSITIVE_INFINITY";
            if (f == Float.NEGATIVE_INFINITY) return "Float.NEGATIVE_INFINITY";
            return String.valueOf(f);
        } else if (item instanceof DoubleItem) {
            double d = ((DoubleItem) item).getValue();
            if (Double.isNaN(d)) return "Double.NaN";
            if (d == Double.POSITIVE_INFINITY) return "Double.POSITIVE_INFINITY";
            if (d == Double.NEGATIVE_INFINITY) return "Double.NEGATIVE_INFINITY";
            return String.valueOf(d);
        }

        return String.valueOf(cpIndex);
    }

    /**
     * Formats a char constant from constant pool index.
     */
    private String formatCharConstant(int cpIndex) {
        Item<?> item = classFile.getConstPool().getItem(cpIndex);
        if (item instanceof IntegerItem) {
            char c = (char) ((IntegerItem) item).getValue().intValue();
            if (c >= 32 && c < 127 && c != '\'' && c != '\\') {
                return "'" + c + "'";
            }
            return "'" + String.format("\\u%04x", (int) c) + "'";
        }
        return "'?'";
    }

    /**
     * Formats a boolean constant from constant pool index.
     */
    private String formatBooleanConstant(int cpIndex) {
        Item<?> item = classFile.getConstPool().getItem(cpIndex);
        if (item instanceof IntegerItem) {
            return ((IntegerItem) item).getValue() != 0 ? "true" : "false";
        }
        return "false";
    }

    /**
     * Formats a String constant from constant pool index.
     */
    private String formatStringConstant(int cpIndex) {
        Item<?> item = classFile.getConstPool().getItem(cpIndex);
        if (item instanceof Utf8Item) {
            return "\"" + escapeString(((Utf8Item) item).getValue()) + "\"";
        } else if (item instanceof StringRefItem) {
            StringRefItem strItem = (StringRefItem) item;
            Utf8Item utf8 = (Utf8Item) classFile.getConstPool().getItem(strItem.getValue());
            if (utf8 != null) {
                return "\"" + escapeString(utf8.getValue()) + "\"";
            }
        }
        return "\"\"";
    }

    /**
     * Formats an enum constant.
     */
    private String formatEnumConstant(EnumConst enumConst) {
        String typeName = resolveUtf8(enumConst.getTypeNameIndex());
        String constName = resolveUtf8(enumConst.getConstNameIndex());

        // Format type name (remove descriptor format)
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
            typeName = typeName.substring(1, typeName.length() - 1);
        }
        typeName = typeName.replace('/', '.');

        if (!emitterConfig.isUseFullyQualifiedNames()) {
            int lastDot = typeName.lastIndexOf('.');
            if (lastDot >= 0) {
                typeName = typeName.substring(lastDot + 1);
            }
        }

        return typeName + "." + constName;
    }

    /**
     * Formats a class constant (e.g., String.class).
     */
    private String formatClassConstant(int cpIndex) {
        String className = resolveUtf8(cpIndex);

        // Handle descriptor format
        if (className.startsWith("L") && className.endsWith(";")) {
            className = className.substring(1, className.length() - 1);
        }

        // Handle primitive type descriptors
        switch (className) {
            case "Z": return "boolean.class";
            case "B": return "byte.class";
            case "C": return "char.class";
            case "S": return "short.class";
            case "I": return "int.class";
            case "J": return "long.class";
            case "F": return "float.class";
            case "D": return "double.class";
            case "V": return "void.class";
        }

        // Handle array types
        if (className.startsWith("[")) {
            return typeRecoverer.recoverType(className).toJavaSource() + ".class";
        }

        className = className.replace('/', '.');
        if (!emitterConfig.isUseFullyQualifiedNames()) {
            int lastDot = className.lastIndexOf('.');
            if (lastDot >= 0) {
                className = className.substring(lastDot + 1);
            }
        }

        return className + ".class";
    }

    /**
     * Formats a nested annotation.
     */
    private String formatNestedAnnotation(Annotation ann) {
        StringBuilder sb = new StringBuilder();
        sb.append("@");

        String typeName = resolveAnnotationType(ann.getTypeIndex());
        sb.append(formatAnnotationTypeName(typeName));

        List<ElementValuePair> pairs = ann.getElementValuePairs();
        if (pairs != null && !pairs.isEmpty()) {
            sb.append("(");
            if (pairs.size() == 1 && "value".equals(pairs.get(0).getElementName())) {
                sb.append(formatElementValue(pairs.get(0).getValue()));
            } else {
                for (int i = 0; i < pairs.size(); i++) {
                    if (i > 0) sb.append(", ");
                    ElementValuePair pair = pairs.get(i);
                    sb.append(pair.getElementName());
                    sb.append(" = ");
                    sb.append(formatElementValue(pair.getValue()));
                }
            }
            sb.append(")");
        }

        return sb.toString();
    }

    /**
     * Formats an array value.
     */
    private String formatArrayValue(List<ElementValue> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatElementValue(values.get(i)));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Resolves a UTF-8 string from constant pool index.
     */
    private String resolveUtf8(int index) {
        Item<?> item = classFile.getConstPool().getItem(index);
        if (item instanceof Utf8Item) {
            return ((Utf8Item) item).getValue();
        }
        return "Unknown";
    }
}
