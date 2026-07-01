package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.FieldAccessExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.transform.ArrayInitializerReconstructor;
import com.tonic.analysis.source.ast.transform.ControlFlowSimplifier;
import com.tonic.analysis.source.ast.transform.DeadStoreEliminator;
import com.tonic.analysis.source.ast.transform.DeadVariableEliminator;
import com.tonic.analysis.source.ast.transform.DeclarationHoister;
import com.tonic.analysis.source.ast.transform.PatternInstanceOfReconstructor;
import com.tonic.analysis.source.ast.transform.SingleUseInliner;
import com.tonic.analysis.source.ast.transform.PatternSwitchReconstructor;
import com.tonic.analysis.source.ast.transform.SwitchExpressionReconstructor;
import com.tonic.analysis.source.ast.transform.VarargsReconstructor;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.emit.IndentingWriter;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.source.recovery.SyntheticLocalVariableTable;
import com.tonic.analysis.source.recovery.SwitchMapAnalyzer;
import com.tonic.analysis.source.recovery.TypeRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.transform.ControlFlowReducibility;
import com.tonic.analysis.ssa.transform.DuplicateBlockMerging;
import com.tonic.analysis.ssa.transform.IRTransform;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.attribute.ConstantValueAttribute;
import com.tonic.parser.attribute.ExceptionsAttribute;
import com.tonic.parser.attribute.PermittedSubclassesAttribute;
import com.tonic.parser.attribute.RecordAttribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.RuntimeInvisibleAnnotationsAttribute;
import com.tonic.parser.attribute.SignatureAttribute;
import com.tonic.parser.attribute.InnerClassesAttribute;
import com.tonic.parser.attribute.table.InnerClassEntry;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.parser.attribute.annotation.ElementValue;
import com.tonic.parser.attribute.annotation.ElementValuePair;
import com.tonic.parser.attribute.annotation.EnumConst;
import com.tonic.parser.constpool.*;
import com.tonic.util.ClassNameUtil;
import com.tonic.util.Modifiers;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
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
    private final DeadVariableEliminator deadVarEliminator;
    private final DeadStoreEliminator deadStoreEliminator;
    private final DeclarationHoister declarationHoister;
    private final SwitchExpressionReconstructor switchExprReconstructor;
    private final PatternSwitchReconstructor patternSwitchReconstructor;
    private final SingleUseInliner singleUseInliner;
    private final PatternInstanceOfReconstructor patternInstanceOf;
    private final VarargsReconstructor varargsReconstructor;
    private final ArrayInitializerReconstructor arrayInitReconstructor;
    private final Set<String> usedTypes = new TreeSet<>();
    private Map<String, NavigableMap<Integer, Integer>> lineMapsCollector;
    private Map<String, DecompileResult.MethodSpan> methodSpansCollector;
    private Map<String, DecompileResult.MemberSpan> fieldSpansCollector;
    private DecompileResult.MemberSpan classSpanCollector;
    private final boolean hasInnerClasses;

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
        this.deadVarEliminator = new DeadVariableEliminator();
        this.deadStoreEliminator = new DeadStoreEliminator();
        this.declarationHoister = new DeclarationHoister();
        this.switchExprReconstructor = new SwitchExpressionReconstructor();
        this.patternSwitchReconstructor = new PatternSwitchReconstructor();
        this.singleUseInliner = new SingleUseInliner();
        this.patternInstanceOf = new PatternInstanceOfReconstructor();
        this.varargsReconstructor = new VarargsReconstructor(classFile);
        this.arrayInitReconstructor = new ArrayInitializerReconstructor();
        this.hasInnerClasses = detectInnerClasses();
    }

    private boolean detectInnerClasses() {
        String thisClassName = classFile.getClassName().replace('/', '.');
        for (Attribute attr : classFile.getClassAttributes()) {
            if (attr instanceof InnerClassesAttribute) {
                InnerClassesAttribute innerAttr = (InnerClassesAttribute) attr;
                for (InnerClassEntry entry : innerAttr.getClasses()) {
                    String outerName = entry.getOuterClassName();
                    String innerName = entry.getInnerClassName();
                    if (thisClassName.equals(outerName)) {
                        return true;
                    }
                    if (innerName != null && innerName.startsWith(thisClassName + "$")) {
                        return true;
                    }
                }
            }
        }
        return false;
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
     * Builds a synthetic {@link LocalVariableTableAttribute} for {@code method} from the recovered slot names,
     * keyed to the method's original bytecode offsets - for injecting named locals into a stripped class (the
     * names match what {@link #decompile()} renders). Returns null when the method has no Code or nothing is
     * recoverable. Does not modify any bytecode; the caller attaches the attribute and writes the class.
     */
    public LocalVariableTableAttribute localVariableTableFor(MethodEntry method) {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null || code.getCode() == null) {
            return null;
        }
        try {
            IRMethod ir = ssa.lift(method);
            applyBaselineTransforms(ir);
            MethodRecoverer recoverer = new MethodRecoverer(ir, method);
            recoverer.analyze();
            recoverer.initializeRecovery();
            return SyntheticLocalVariableTable.build(ir, recoverer.getRecoveryContext(), method,
                    code.getCode().length, code.getMaxLocals(), classFile.getConstPool());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decompiles the entire class to a Java source string.
     */
    public String decompile() {
        IndentingWriter writer = new IndentingWriter(new StringWriter(), emitterConfig.getIndentString());
        decompile(writer);
        return writer.toString();
    }

    /**
     * Decompiles the class and additionally collects, per method, a map from bytecode offset to the
     * 1-based output line of the statement recovered from that offset. Provenance flows from the SSA
     * lifter through statement recovery and the AST transform pipeline; statements without surviving
     * provenance (synthesized or merged away) simply have no entry.
     */
    public DecompileResult decompileWithLineMap() {
        lineMapsCollector = new LinkedHashMap<>();
        methodSpansCollector = new LinkedHashMap<>();
        fieldSpansCollector = new LinkedHashMap<>();
        classSpanCollector = null;
        try {
            IndentingWriter writer = new IndentingWriter(new StringWriter(), emitterConfig.getIndentString());
            decompile(writer);
            return new DecompileResult(writer.toString(), lineMapsCollector, methodSpansCollector,
                    fieldSpansCollector, classSpanCollector);
        } finally {
            lineMapsCollector = null;
            methodSpansCollector = null;
            fieldSpansCollector = null;
            classSpanCollector = null;
        }
    }

    /**
     * Decompiles the entire class to the given writer.
     */
    public void decompile(IndentingWriter writer) {
        analyzeSwitchMaps();
        usedTypes.clear();

        String className = classFile.getClassName();
        String packageName = ClassNameUtil.getPackageNameAsSource(className);

        IndentingWriter bodyWriter = new IndentingWriter(new StringWriter(), emitterConfig.getIndentString());
        emitClassBody(bodyWriter);
        String bodyContent = bodyWriter.toString();

        if (!packageName.isEmpty()) {
            writer.writeLine("package " + packageName + ";");
            writer.newLine();
        }

        if (!emitterConfig.isUseFullyQualifiedNames()) {
            emitImports(writer, className);
        }

        if (lineMapsCollector != null) {
            int headerLines = writer.getCurrentLine() - 1;
            if (headerLines > 0) {
                for (NavigableMap<Integer, Integer> map : lineMapsCollector.values()) {
                    map.replaceAll((offset, line) -> line + headerLines);
                }
                methodSpansCollector.replaceAll((key, span) -> new DecompileResult.MethodSpan(
                        span.getStartLine() + headerLines, span.getEndLine() + headerLines));
                fieldSpansCollector.replaceAll((key, span) -> new DecompileResult.MemberSpan(
                        span.getStartLine() + headerLines, span.getEndLine() + headerLines));
                if (classSpanCollector != null) {
                    classSpanCollector = new DecompileResult.MemberSpan(
                            classSpanCollector.getStartLine() + headerLines,
                            classSpanCollector.getEndLine() + headerLines);
                }
            }
        }
        writer.writeRaw(bodyContent);
    }

    private void emitClassBody(IndentingWriter writer) {
        int classSpanStart = writer.getCurrentLine();
        emitClassAnnotations(writer);
        emitClassDeclaration(writer);
        writer.writeLine(" {");
        recordClassSpan(classSpanStart, writer.getCurrentLine() - 1);
        writer.newLine();
        writer.indent();

        boolean isEnum = Modifiers.isEnum(classFile.getAccess());
        RecordAttribute record = findRecordAttribute();
        List<FieldEntry> fields = classFile.getFields();

        if (isEnum) {
            emitEnumConstants(writer, fields);
        }

        List<FieldEntry> regularFields = isEnum ? getNonEnumConstantFields(fields) : fields;
        List<FieldEntry> nonSyntheticFields = new ArrayList<>();
        for (FieldEntry field : regularFields) {
            if (isSuppressedRecordField(record, field)) {
                continue;
            }
            if (!isEnum || !isSyntheticEnumField(field)) {
                nonSyntheticFields.add(field);
            }
        }
        ClinitHoist hoist = isEnum ? null : computeClinitHoist(nonSyntheticFields);

        if (!nonSyntheticFields.isEmpty()) {
            for (FieldEntry field : nonSyntheticFields) {
                emitField(writer, field, hoist);
            }
            writer.newLine();
        }

        if (!isEnum) {
            MethodEntry clinit = findMethod("<clinit>");
            if (clinit != null && clinit.getCodeAttribute() != null) {
                if (hoist == null) {
                    emitStaticInitializer(writer, clinit);   // recovery failed - emit the whole <clinit>
                    writer.newLine();
                } else if (hoist.hasRemaining()) {
                    emitStaticInitializerBlock(writer, hoist.remainingBody, hoist.clinitKey);
                    writer.newLine();
                }
                // else: every <clinit> assignment hoisted into field initializers - no static block needed
            }
        }

        List<MethodEntry> constructors = new ArrayList<>();
        List<MethodEntry> methods = new ArrayList<>();

        for (MethodEntry method : classFile.getMethods()) {
            String methodName = method.getName();
            if (isSuppressedRecordMethod(record, method)
                    || methodName.equals("<clinit>")
                    || isSyntheticLambdaMethod(methodName)
                    || (isEnum && isSyntheticEnumMethod(methodName))) {
                continue;
            }
            if (methodName.equals("<init>")) {
                if (!isEnum) {
                    constructors.add(method);
                }
            } else {
                methods.add(method);
            }
        }

        for (MethodEntry ctor : constructors) {
            emitConstructor(writer, ctor);
            writer.newLine();
        }

        for (MethodEntry method : methods) {
            emitMethod(writer, method);
            writer.newLine();
        }

        writer.dedent();
        writer.writeLine("}");
    }

    private void emitEnumConstants(IndentingWriter writer, List<FieldEntry> fields) {
        String enumType = "L" + classFile.getClassName() + ";";
        List<String> enumConstantNames = new ArrayList<>();

        for (FieldEntry field : fields) {
            if (isEnumConstantField(field, enumType)) {
                enumConstantNames.add(field.getName());
            }
        }

        if (!enumConstantNames.isEmpty()) {
            for (int i = 0; i < enumConstantNames.size(); i++) {
                String suffix = (i < enumConstantNames.size() - 1) ? "," : ";";
                writer.writeLine(enumConstantNames.get(i) + suffix);
            }
            writer.newLine();
        }
    }

    private List<FieldEntry> getNonEnumConstantFields(List<FieldEntry> fields) {
        String enumType = "L" + classFile.getClassName() + ";";
        List<FieldEntry> result = new ArrayList<>();
        for (FieldEntry field : fields) {
            if (!isEnumConstantField(field, enumType)) {
                result.add(field);
            }
        }
        return result;
    }

    private boolean isEnumConstantField(FieldEntry field, String enumType) {
        int access = field.getAccess();
        boolean isPublicStaticFinalEnum = Modifiers.isPublic(access)
                && Modifiers.isStatic(access)
                && Modifiers.isFinal(access)
                && Modifiers.isEnum(access);
        return isPublicStaticFinalEnum && enumType.equals(field.getDesc());
    }

    private boolean isSyntheticEnumField(FieldEntry field) {
        String name = field.getName();
        return name != null && name.equals("$VALUES");
    }

    private boolean isSyntheticEnumMethod(String methodName) {
        return "values".equals(methodName) || "valueOf".equals(methodName);
    }

    private void analyzeSwitchMaps() {
        analyzeOwnSwitchMap();
        analyzeSiblingSwitchMapHolders();
    }

    private void analyzeOwnSwitchMap() {
        if (!declaresSwitchMapField(classFile)) {
            return;
        }

        MethodEntry clinit = findMethod("<clinit>");
        if (clinit == null || clinit.getCodeAttribute() == null) {
            return;
        }

        try {
            IRMethod ir = ssa.lift(clinit);
            applyBaselineTransforms(ir);
            List<IRMethod> methods = new ArrayList<>();
            methods.add(ir);
            SwitchMapAnalyzer.analyzeClass(classFile.getFields(), methods);
        } catch (Exception e) {
            // Switch map analysis failed, continue without it
        }
    }

    /**
     * Analyzes the synthetic {@code $SwitchMap$} holder classes that javac generates alongside this class
     * for enum switches. The holder is a nested class of the class that contains the switch; its static
     * initializer maps each enum constant's ordinal to a dense case index. Resolving it from the default
     * {@link ClassPool} populates the switch-map registry so the switch emits constant-name case labels.
     * When the holder is not in the pool, the recovery falls back to a switch on {@code ordinal()}.
     */
    private void analyzeSiblingSwitchMapHolders() {
        // Prefer the pool that actually loaded this class (e.g. a host's project pool); fall back to the
        // default pool for standalone decompilation.
        ClassPool pool = classFile.getClassPool() != null ? classFile.getClassPool() : ClassPool.getDefault();
        if (pool == null) {
            return;
        }
        String thisInternal = classFile.getClassName();
        for (String holderInternal : nestedClassInternalNames()) {
            if (holderInternal.equals(thisInternal) || !holderInternal.startsWith(thisInternal + "$")) {
                continue;
            }
            ClassFile holder = pool.get(holderInternal);
            if (holder == null || !declaresSwitchMapField(holder)) {
                continue;
            }
            try {
                List<MethodEntry> clinits = holder.getMethods("<clinit>");
                if (clinits.isEmpty() || clinits.get(0).getCodeAttribute() == null) {
                    continue;
                }
                IRMethod ir = new SSA(holder.getConstPool()).lift(clinits.get(0));
                List<IRMethod> methods = new ArrayList<>();
                methods.add(ir);
                SwitchMapAnalyzer.analyzeClass(holder.getFields(), methods);
            } catch (Exception ignored) {
                // Holder unavailable or unliftable; the switch falls back to ordinal().
            }
        }
    }

    private boolean declaresSwitchMapField(ClassFile cf) {
        for (FieldEntry field : cf.getFields()) {
            String name = field.getName();
            if (name != null && name.startsWith("$SwitchMap$")) {
                return true;
            }
        }
        return false;
    }

    private List<String> nestedClassInternalNames() {
        List<String> names = new ArrayList<>();
        for (Attribute attr : classFile.getClassAttributes()) {
            if (attr instanceof InnerClassesAttribute) {
                for (InnerClassEntry entry : ((InnerClassesAttribute) attr).getClasses()) {
                    String inner = entry.getInnerClassName();
                    if (inner != null) {
                        names.add(inner.replace('.', '/'));
                    }
                }
            }
        }
        return names;
    }

    private void emitClassDeclaration(IndentingWriter writer) {
        int access = classFile.getAccess();
        PermittedSubclassesAttribute permits = findPermittedSubclasses();
        RecordAttribute record = findRecordAttribute();

        // Modifiers
        StringBuilder sb = new StringBuilder();
        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access)) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");
        if (Modifiers.isAbstract(access) && !Modifiers.isInterface(access)) sb.append("abstract ");
        // Records are implicitly final; never render `final record`.
        if (Modifiers.isFinal(access) && !Modifiers.isEnum(access) && record == null) sb.append("final ");
        // A class/interface is sealed iff it carries a PermittedSubclasses attribute.
        if (permits != null) sb.append("sealed ");

        // Type keyword
        if (record != null) {
            sb.append("record ");
        } else if (Modifiers.isAnnotation(access)) {
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
        String simpleName = ClassNameUtil.getSimpleNameWithInnerClasses(fullName);
        sb.append(simpleName);
        String classSig = getSignature(classFile.getClassAttributes());
        if (classSig != null) {
            sb.append(typeRecoverer.recoverFormalTypeParameters(classSig));
        }

        // Record component list: `record Name(T a, U b)`
        if (record != null) {
            sb.append("(");
            List<String[]> comps = record.getComponentNameAndDescriptors();
            for (int i = 0; i < comps.size(); i++) {
                if (i > 0) sb.append(", ");
                String compType = trackAndFormatType(typeRecoverer.recoverType(comps.get(i)[1]));
                sb.append(compType).append(" ").append(comps.get(i)[0]);
            }
            sb.append(")");
        }

        // Superclass - skip for interfaces, annotations, enums, and records (java.lang.Record implicit)
        String superName = classFile.getSuperClassName();
        boolean isAnnotation = Modifiers.isAnnotation(access);
        if (superName != null && !superName.equals("java/lang/Object")
                && !superName.equals("java/lang/Enum")
                && !superName.equals("java/lang/Record")
                && !Modifiers.isInterface(access) && !isAnnotation) {
            sb.append(" extends ").append(formatClassName(superName));
        }

        // Interfaces - skip implicit interfaces (Annotation for @interface, Enum-related for enum)
        List<Integer> interfaces = classFile.getInterfaces();
        if (interfaces != null && !interfaces.isEmpty()) {
            List<String> filteredInterfaces = new ArrayList<>();
            for (int idx : interfaces) {
                String ifaceName = resolveClassName(idx);
                // Skip implicit interfaces
                if (isAnnotation && "java/lang/annotation/Annotation".equals(ifaceName)) {
                    continue;
                }
                filteredInterfaces.add(ifaceName);
            }

            if (!filteredInterfaces.isEmpty()) {
                sb.append(Modifiers.isInterface(access) ? " extends " : " implements ");
                for (int i = 0; i < filteredInterfaces.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(formatClassName(filteredInterfaces.get(i)));
                }
            }
        }

        // permits clause for sealed types
        if (permits != null && !permits.getPermittedClassNames().isEmpty()) {
            sb.append(" permits ");
            List<String> names = permits.getPermittedClassNames();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatClassName(names.get(i)));
            }
        }

        writer.write(sb.toString());
    }

    private PermittedSubclassesAttribute findPermittedSubclasses() {
        for (Attribute a : classFile.getClassAttributes()) {
            if (a instanceof PermittedSubclassesAttribute) {
                return (PermittedSubclassesAttribute) a;
            }
        }
        return null;
    }

    private RecordAttribute findRecordAttribute() {
        for (Attribute a : classFile.getClassAttributes()) {
            if (a instanceof RecordAttribute) {
                return (RecordAttribute) a;
            }
        }
        return null;
    }

    /**
     * Names of members the compiler auto-generates for a record and which must be suppressed so the
     * decompiled {@code record} header is the canonical form: the component backing fields, the
     * canonical constructor, the component accessors, and the {@code ObjectMethods}-backed
     * {@code equals}/{@code hashCode}/{@code toString} (recognized by canonical signature + final, so
     * user overrides — which are not final — are kept).
     */
    private boolean isSuppressedRecordField(RecordAttribute record, FieldEntry field) {
        if (record == null || Modifiers.isStatic(field.getAccess())) {
            return false;
        }
        for (String[] comp : record.getComponentNameAndDescriptors()) {
            if (comp[0].equals(field.getName()) && comp[1].equals(field.getDesc())) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuppressedRecordMethod(RecordAttribute record, MethodEntry method) {
        if (record == null) {
            return false;
        }
        String name = method.getName();
        String desc = method.getDesc();
        List<String[]> comps = record.getComponentNameAndDescriptors();

        // Component accessor: `T name()`.
        for (String[] comp : comps) {
            if (comp[0].equals(name) && ("()" + comp[1]).equals(desc)) {
                return true;
            }
        }
        // Canonical constructor: parameter types equal the component types in order.
        if ("<init>".equals(name)) {
            StringBuilder canonical = new StringBuilder("(");
            for (String[] comp : comps) {
                canonical.append(comp[1]);
            }
            canonical.append(")V");
            if (canonical.toString().equals(desc)) {
                return true;
            }
        }
        // Auto-generated Object methods (final + canonical signature).
        if (Modifiers.isFinal(method.getAccess())) {
            return ("toString".equals(name) && "()Ljava/lang/String;".equals(desc))
                    || ("hashCode".equals(name) && "()I".equals(desc))
                    || ("equals".equals(name) && "(Ljava/lang/Object;)Z".equals(desc));
        }
        return false;
    }

    private void emitField(IndentingWriter writer, FieldEntry field, ClinitHoist hoist) {
        int spanStart = writer.getCurrentLine();
        emitFieldAnnotations(writer, field);

        int access = field.getAccess();
        StringBuilder sb = new StringBuilder();

        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access) && !hasInnerClasses) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");
        if (Modifiers.isStatic(access)) sb.append("static ");
        if (Modifiers.isFinal(access)) sb.append("final ");
        if (Modifiers.isVolatile(access)) sb.append("volatile ");
        if (Modifiers.isTransient(access)) sb.append("transient ");

        String typeStr = trackAndFormatType(getFieldType(field));
        sb.append(typeStr).append(" ");

        sb.append(field.getName());

        String initializer = hoist == null ? null : hoist.initializers.get(field.getName());
        if (initializer == null) {
            initializer = getConstantValue(field);
        }
        if (initializer != null) {
            sb.append(" = ").append(initializer);
        }

        sb.append(";");
        writer.writeLine(sb.toString());
        recordFieldSpan(field.getName() + field.getDesc(), spanStart, writer.getCurrentLine() - 1);
    }

    private com.tonic.analysis.source.ast.type.SourceType getFieldType(FieldEntry field) {
        String signature = getSignature(field.getAttributes());
        if (signature != null) {
            return typeRecoverer.recoverGenericType(signature);
        }
        return typeRecoverer.recoverType(field.getDesc());
    }

    private String getSignature(List<Attribute> attributes) {
        if (attributes == null) return null;
        for (Attribute attr : attributes) {
            if (attr instanceof SignatureAttribute) {
                SignatureAttribute sigAttr = (SignatureAttribute) attr;
                int sigIndex = sigAttr.getSignatureIndex();
                Item<?> item = classFile.getConstPool().getItem(sigIndex);
                if (item instanceof Utf8Item) {
                    return ((Utf8Item) item).getValue();
                }
            }
        }
        return null;
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
                Item<?> item = classFile.getConstPool().getItem(cpIndex);

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

    /** Lifts + recovers {@code <clinit>} to a fully-transformed AST body (no trailing return). Throws on failure. */
    private BlockStmt recoverClinitBody(MethodEntry clinit) {
        IRMethod ir = ssa.lift(clinit);
        applyBaselineTransforms(ir);
        applyAdditionalTransforms(ir);
        BlockStmt body = MethodRecoverer.recoverMethod(ir, clinit);
        astSimplifier.transform(body);
        arrayInitReconstructor.transform(body);
        patternInstanceOf.transform(body);
        singleUseInliner.transform(body);
        deadStoreEliminator.transform(body);
        deadVarEliminator.transform(body);
        varargsReconstructor.transform(body);
        declarationHoister.transform(body);
        // Re-inline a local the declaration-sink just merged into a single-use form (e.g. `Task task =
        // new Task()` sunk into its `if`, used once), matching the recompile which keeps such a value resident.
        singleUseInliner.transform(body);
        patternSwitchReconstructor.transform(body);
        switchExprReconstructor.transform(body);
        removeTrailingReturn(body); // Static initializers cannot have return statements
        return body;
    }

    /** Emits a {@code static { ... }} block from an already-recovered body. */
    private void emitStaticInitializerBlock(IndentingWriter writer, BlockStmt body, String key) {
        int spanStart = writer.getCurrentLine();
        writer.writeLine("static {");
        writer.indent();
        emitBlockContents(writer, body, key);
        writer.dedent();
        writer.writeLine("}");
        recordMethodSpan(key, spanStart, writer.getCurrentLine() - 1);
    }

    private void emitStaticInitializer(IndentingWriter writer, MethodEntry clinit) {
        int spanStart = writer.getCurrentLine();
        writer.writeLine("static {");
        writer.indent();
        try {
            emitBlockContents(writer, recoverClinitBody(clinit), clinit.getName() + clinit.getDesc());
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile static initializer: " + e.getMessage());
        }
        writer.dedent();
        writer.writeLine("}");
        recordMethodSpan(clinit.getName() + clinit.getDesc(), spanStart, writer.getCurrentLine() - 1);
    }

    /** A {@code <clinit>}'s leading static-field assignments hoisted into field initializers + the leftover block. */
    private static final class ClinitHoist {
        final Map<String, String> initializers;   // field name -> rendered initializer source
        final BlockStmt remainingBody;             // <clinit> body minus the hoisted prefix
        final String clinitKey;

        ClinitHoist(Map<String, String> initializers, BlockStmt remainingBody, String clinitKey) {
            this.initializers = initializers;
            this.remainingBody = remainingBody;
            this.clinitKey = clinitKey;
        }

        boolean hasRemaining() {
            return remainingBody != null && !remainingBody.getStatements().isEmpty();
        }
    }

    /**
     * Hoists the leading run of {@code <clinit>} statements that are simple {@code ThisClass.staticField = <expr>}
     * assignments into the field declarations (Java field-initializer sugar). Only a contiguous prefix is taken,
     * and only while the assigned fields are in declaration order, each assigned exactly once in {@code <clinit>},
     * with a right-hand side that references no local (a {@code <clinit>} temp). Under those rules the emitted layout
     * (field inits in declaration order, then the leftover {@code static {}} block) reproduces the original
     * {@code <clinit>} execution order exactly. Returns null if there is no {@code <clinit>} or recovery fails, so the
     * caller falls back to emitting the whole block.
     */
    private ClinitHoist computeClinitHoist(List<FieldEntry> emittedFields) {
        MethodEntry clinit = findMethod("<clinit>");
        if (clinit == null || clinit.getCodeAttribute() == null) {
            return null;
        }
        BlockStmt body;
        try {
            body = recoverClinitBody(clinit);
        } catch (Exception e) {
            return null;
        }
        String key = clinit.getName() + clinit.getDesc();

        Map<String, Integer> declIndex = new HashMap<>();
        for (int i = 0; i < emittedFields.size(); i++) {
            FieldEntry f = emittedFields.get(i);
            if (Modifiers.isStatic(f.getAccess())) {
                declIndex.putIfAbsent(f.getName(), i);
            }
        }

        List<Statement> stmts = body.getStatements();
        Map<String, Integer> assignCount = new HashMap<>();
        for (Statement s : stmts) {
            String name = staticFieldAssignmentName(s);
            if (name != null) {
                assignCount.merge(name, 1, Integer::sum);
            }
        }

        Map<String, String> hoisted = new LinkedHashMap<>();
        int maxIdx = -1;
        int prefixEnd = 0;
        for (Statement s : stmts) {
            String name = staticFieldAssignmentName(s);
            if (name == null) break;                              // not an own-static assignment - stop the prefix
            Integer idx = declIndex.get(name);
            if (idx == null || idx <= maxIdx) break;              // not an emitted field, or out of declaration order
            if (assignCount.getOrDefault(name, 0) != 1) break;    // assigned more than once in <clinit>
            Expression rhs = ((BinaryExpr) ((ExprStmt) s).getExpression()).getRight();
            if (referencesLocal(rhs)) break;                      // RHS uses a <clinit> temp (e.g. array build)
            hoisted.put(name, SourceEmitter.emit(rhs));
            maxIdx = idx;
            prefixEnd++;
        }

        BlockStmt remaining = prefixEnd == 0
                ? body
                : new BlockStmt(new ArrayList<>(stmts.subList(prefixEnd, stmts.size())));
        return new ClinitHoist(hoisted, remaining, key);
    }

    /** The field name if {@code s} is {@code ThisClass.staticField = <expr>} (simple {@code =}), else null. */
    private String staticFieldAssignmentName(Statement s) {
        if (!(s instanceof ExprStmt)) return null;
        Expression e = ((ExprStmt) s).getExpression();
        if (!(e instanceof BinaryExpr)) return null;
        BinaryExpr b = (BinaryExpr) e;
        if (b.getOperator() != BinaryOperator.ASSIGN || !(b.getLeft() instanceof FieldAccessExpr)) return null;
        FieldAccessExpr fa = (FieldAccessExpr) b.getLeft();
        if (!fa.isStatic() || !classFile.getClassName().equals(fa.getOwnerClass())) return null;
        return fa.getFieldName();
    }

    /** Whether the AST subtree references any local variable (a {@link VarRefExpr}). */
    private static boolean referencesLocal(ASTNode node) {
        if (node instanceof VarRefExpr) return true;
        for (ASTNode child : node.getChildren()) {
            if (referencesLocal(child)) return true;
        }
        return false;
    }

    /**
     * Removes a trailing void return statement from a block.
     * Used for static initializers where return statements are invalid in Java source.
     */
    private void removeTrailingReturn(BlockStmt body) {
        removeTrailingVoidReturn(body.getStatements());
    }

    /**
     * Removes a redundant trailing void {@code return;} - one with nothing executable after it - from a
     * statement list, recursing into the branches of a trailing {@code if} (which are themselves in tail
     * position). A void return in tail position is implicit in Java, so emitting one is non-idempotent: the
     * recompiled bytecode routes a try/catch's normal exit through a shared trailing return that recovery
     * then surfaces as an explicit `return;` inside the enclosing block.
     */
    private void removeTrailingVoidReturn(List<Statement> stmts) {
        if (stmts.isEmpty()) {
            return;
        }
        Statement last = stmts.get(stmts.size() - 1);
        if (last instanceof ReturnStmt && ((ReturnStmt) last).getValue() == null) {
            stmts.remove(stmts.size() - 1);
            // The statement now in last position (e.g. a trailing if) is itself in tail position; re-process so
            // its own redundant trailing void return is dropped too.
            removeTrailingVoidReturn(stmts);
        } else if (last instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) last;
            if (ifStmt.getThenBranch() instanceof BlockStmt) {
                removeTrailingVoidReturn(((BlockStmt) ifStmt.getThenBranch()).getStatements());
            }
            if (ifStmt.getElseBranch() instanceof BlockStmt) {
                removeTrailingVoidReturn(((BlockStmt) ifStmt.getElseBranch()).getStatements());
            }
        }
    }

    private void removeRedundantSuper(BlockStmt body) {
        List<Statement> stmts = body.getStatements();
        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            if (stmt instanceof ExprStmt) {
                Expression expr = ((ExprStmt) stmt).getExpression();
                if (expr instanceof MethodCallExpr) {
                    MethodCallExpr call = (MethodCallExpr) expr;
                    if ("super".equals(call.getMethodName()) &&
                        call.getReceiver() == null &&
                        call.getArguments().isEmpty()) {
                        stmts.remove(i);
                        break;
                    }
                }
            }
        }
    }

    private void emitConstructor(IndentingWriter writer, MethodEntry ctor) {
        int spanStart = writer.getCurrentLine();
        emitMethodAnnotations(writer, ctor);

        int access = ctor.getAccess();

        // Modifiers
        StringBuilder sb = new StringBuilder();
        if (Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access) && !hasInnerClasses) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");

        String fullName = classFile.getClassName();
        String simpleName = ClassNameUtil.getSimpleNameWithInnerClasses(fullName);
        sb.append(simpleName);

        String desc = ctor.getDesc();
        String signature = getSignature(ctor.getAttributes());
        String sigOrDesc = signature != null ? signature : desc;

        sb.append("(");
        sb.append(formatParameters(sigOrDesc, signature != null, Modifiers.isVarArgs(ctor.getAccess()),
                false, unambiguousLvtNames(ctor)));
        sb.append(")");

        // Throws clause (TODO: could extract from Exceptions attribute)

        writer.write(sb.toString());

        if (ctor.getCodeAttribute() == null) {
            writer.writeLine(";");
            recordMethodSpan(ctor.getName() + ctor.getDesc(), spanStart, writer.getCurrentLine() - 1);
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
            patternInstanceOf.transform(body);
            singleUseInliner.transform(body);
            deadStoreEliminator.transform(body);
            deadVarEliminator.transform(body);
            varargsReconstructor.transform(body);
            declarationHoister.transform(body);
        // Re-inline a local the declaration-sink just merged into a single-use form (e.g. `Task task =
        // new Task()` sunk into its `if`, used once), matching the recompile which keeps such a value resident.
        singleUseInliner.transform(body);
            patternSwitchReconstructor.transform(body);
            switchExprReconstructor.transform(body);
            removeRedundantSuper(body);
            removeTrailingReturn(body);
            emitBlockContents(writer, body, ctor.getName() + ctor.getDesc());
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile constructor: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
        recordMethodSpan(ctor.getName() + ctor.getDesc(), spanStart, writer.getCurrentLine() - 1);
    }

    private void emitMethod(IndentingWriter writer, MethodEntry method) {
        int spanStart = writer.getCurrentLine();
        emitMethodAnnotations(writer, method);

        int access = method.getAccess();
        int classAccess = classFile.getAccess();
        boolean isAnnotationType = Modifiers.isAnnotation(classAccess);

        // Modifiers - skip public/abstract for annotation type methods (they're implicit)
        StringBuilder sb = new StringBuilder();
        if (!isAnnotationType && Modifiers.isPublic(access)) sb.append("public ");
        if (Modifiers.isPrivate(access) && !hasInnerClasses) sb.append("private ");
        if (Modifiers.isProtected(access)) sb.append("protected ");
        if (Modifiers.isStatic(access)) sb.append("static ");
        if (Modifiers.isFinal(access)) sb.append("final ");
        if (!isAnnotationType && Modifiers.isAbstract(access)) sb.append("abstract ");
        if (Modifiers.isSynchronized(access)) sb.append("synchronized ");
        if (Modifiers.isNative(access)) sb.append("native ");

        String desc = method.getDesc();
        String signature = getSignature(method.getAttributes());
        String sigOrDesc = signature != null ? signature : desc;

        if (signature != null) {
            String formal = typeRecoverer.recoverFormalTypeParameters(signature);
            if (!formal.isEmpty()) {
                sb.append(formal).append(" ");
            }
        }
        String returnType = extractReturnType(sigOrDesc, signature != null);
        sb.append(returnType).append(" ");

        sb.append(method.getName());

        sb.append("(");
        sb.append(formatParameters(sigOrDesc, signature != null, Modifiers.isVarArgs(access),
                Modifiers.isStatic(access), unambiguousLvtNames(method)));
        sb.append(")");

        String throwsClause = buildThrowsClause(method.getAttributes());
        if (!throwsClause.isEmpty()) {
            sb.append(" throws ").append(throwsClause);
        }

        writer.write(sb.toString());

        // Abstract or native methods have no body
        if (Modifiers.isAbstract(access) || Modifiers.isNative(access) || method.getCodeAttribute() == null) {
            writer.writeLine(";");
            recordMethodSpan(method.getName() + method.getDesc(), spanStart, writer.getCurrentLine() - 1);
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
            patternInstanceOf.transform(body);
            singleUseInliner.transform(body);
            deadStoreEliminator.transform(body);
            deadVarEliminator.transform(body);
            // Re-simplify: the eliminators above can empty a then-branch (leaving
            // `if (c) {} else { ... }`), which the first pass could not see. A second pass
            // inverts/cleans those.
            astSimplifier.transform(body);
            varargsReconstructor.transform(body);
            declarationHoister.transform(body);
        // Re-inline a local the declaration-sink just merged into a single-use form (e.g. `Task task =
        // new Task()` sunk into its `if`, used once), matching the recompile which keeps such a value resident.
        singleUseInliner.transform(body);
            // Fold the array-build idiom (`T[] tmp = new T[N]; tmp[i]=...`) back into an array literal
            // (`new T[]{...}`). Run after hoisting+inlining so the temp declaration is formed and each element
            // value is inline; the synthetic temp otherwise carries an unstable slot-based name that drifts.
            arrayInitReconstructor.transform(body);
            patternSwitchReconstructor.transform(body);
            switchExprReconstructor.transform(body);
            removeTrailingReturn(body);
            emitBlockContents(writer, body, method.getName() + method.getDesc());
        } catch (Exception e) {
            writer.writeLine("// Failed to decompile: " + e.getMessage());
        }

        writer.dedent();
        writer.writeLine("}");
        recordMethodSpan(method.getName() + method.getDesc(), spanStart, writer.getCurrentLine() - 1);
    }

    private void recordMethodSpan(String methodKey, int startLine, int endLine) {
        if (methodSpansCollector != null) {
            methodSpansCollector.put(methodKey, new DecompileResult.MethodSpan(startLine, endLine));
        }
    }

    private void recordFieldSpan(String fieldKey, int startLine, int endLine) {
        if (fieldSpansCollector != null) {
            fieldSpansCollector.put(fieldKey, new DecompileResult.MemberSpan(startLine, endLine));
        }
    }

    private void recordClassSpan(int startLine, int endLine) {
        if (fieldSpansCollector != null) {
            classSpanCollector = new DecompileResult.MemberSpan(startLine, endLine);
        }
    }

    private void emitBlockContents(IndentingWriter writer, BlockStmt block, String methodKey) {
        SourceEmitter emitter = new SourceEmitter(writer, emitterConfig);
        emitter.setCurrentClassName(classFile.getClassName());
        if (lineMapsCollector != null && methodKey != null) {
            // Statements inside an inlined lambda are reported under the lambda's own impl-method key,
            // so each method (including synthetic lambda$ methods) gets its own offset→line map.
            emitter.setLineMapSink(methodKey, (key, stmt, line) ->
                    lineMapsCollector.computeIfAbsent(key, k -> new TreeMap<>())
                            .put(stmt.getLocation().bytecodeOffset(), line));
        }
        for (Statement stmt : block.getStatements()) {
            stmt.accept(emitter);
        }
        usedTypes.addAll(emitter.getUsedTypes());
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
        usedTypes.add(internalName);
        if (emitterConfig.isUseFullyQualifiedNames()) {
            return ClassNameUtil.toSourceName(internalName);
        }
        return ClassNameUtil.getSimpleNameWithInnerClasses(internalName);
    }

    /** Builds the {@code throws} clause from a member's Exceptions attribute (e.g. "IOException, SQLException"), or "". */
    private String buildThrowsClause(List<Attribute> attributes) {
        if (attributes == null) {
            return "";
        }
        for (Attribute a : attributes) {
            if (a instanceof ExceptionsAttribute) {
                List<Integer> indices = ((ExceptionsAttribute) a).getExceptionIndexTable();
                if (indices == null || indices.isEmpty()) {
                    return "";
                }
                List<String> names = new ArrayList<>();
                for (int idx : indices) {
                    names.add(trackAndFormatType(new ReferenceSourceType(resolveClassName(idx))));
                }
                return String.join(", ", names);
            }
        }
        return "";
    }

    private String resolveClassName(int classIndex) {
        try {
            ClassRefItem classRef = (ClassRefItem) classFile.getConstPool().getItem(classIndex);
            return classRef.getClassName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String extractReturnType(String desc, boolean isSignature) {
        String workDesc = desc;
        if (isSignature && workDesc.startsWith("<")) {
            int depth = 1;
            int i = 1;
            while (i < workDesc.length() && depth > 0) {
                char c = workDesc.charAt(i);
                if (c == '<') depth++;
                else if (c == '>') depth--;
                i++;
            }
            workDesc = workDesc.substring(i);
        }

        int parenEnd = findClosingParen(workDesc);
        if (parenEnd < 0) return "void";
        String returnDesc = workDesc.substring(parenEnd + 1);

        if (returnDesc.startsWith("^")) {
            int semi = returnDesc.indexOf(';');
            if (semi > 0) returnDesc = returnDesc.substring(semi + 1);
        }

        if (isSignature) {
            return trackAndFormatType(typeRecoverer.recoverGenericType(returnDesc));
        }
        return trackAndFormatType(typeRecoverer.recoverType(returnDesc));
    }

    private int findClosingParen(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private String trackAndFormatType(SourceType type) {
        recordTypeFromSourceType(type);
        return type.toJavaSource();
    }

    private void recordTypeFromSourceType(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            ReferenceSourceType refType = (ReferenceSourceType) type;
            String internalName = refType.getInternalName();
            if (internalName.contains("/")) {
                usedTypes.add(internalName);
            }
            for (SourceType typeArg : refType.getTypeArguments()) {
                recordTypeFromSourceType(typeArg);
            }
        } else if (type instanceof ArraySourceType) {
            ArraySourceType arrType = (ArraySourceType) type;
            recordTypeFromSourceType(arrType.getElementType());
        }
    }

    /**
     * Maps a method's local slots to their LocalVariableTable names, keeping only slots whose every entry
     * agrees on a single name. Lets the signature show real parameter names that match the recovered body.
     */
    private Map<Integer, String> unambiguousLvtNames(com.tonic.parser.MethodEntry method) {
        Map<Integer, String> result = new HashMap<>();
        CodeAttribute code = method.getCodeAttribute();
        if (code == null) {
            return result;
        }
        LocalVariableTableAttribute lvt = null;
        for (Attribute attr : code.getAttributes()) {
            if (attr instanceof LocalVariableTableAttribute) {
                lvt = (LocalVariableTableAttribute) attr;
                break;
            }
        }
        if (lvt == null) {
            return result;
        }
        Map<Integer, Set<String>> namesPerSlot = new HashMap<>();
        for (LocalVariableTableEntry e : lvt.getLocalVariableTable()) {
            Object item = classFile.getConstPool().getItem(e.getNameIndex());
            if (item instanceof Utf8Item) {
                namesPerSlot.computeIfAbsent(e.getIndex(), k -> new HashSet<>()).add(((Utf8Item) item).getValue());
            }
        }
        for (Map.Entry<Integer, Set<String>> e : namesPerSlot.entrySet()) {
            if (e.getValue().size() == 1) {
                result.put(e.getKey(), e.getValue().iterator().next());
            }
        }
        return result;
    }

    private String formatParameters(String desc, boolean isSignature, boolean varargs, boolean isStatic,
                                    Map<Integer, String> slotNames) {
        String workDesc = desc;
        if (isSignature && workDesc.startsWith("<")) {
            int depth = 1;
            int idx = 1;
            while (idx < workDesc.length() && depth > 0) {
                char c = workDesc.charAt(idx);
                if (c == '<') depth++;
                else if (c == '>') depth--;
                idx++;
            }
            workDesc = workDesc.substring(idx);
        }

        int parenStart = workDesc.indexOf('(');
        int parenEnd = findClosingParen(workDesc);
        if (parenStart < 0 || parenEnd < 0) return "";

        String params = workDesc.substring(parenStart + 1, parenEnd);
        if (params.isEmpty()) return "";

        List<String> paramTypes = new ArrayList<>();
        List<Integer> paramWidths = new ArrayList<>();
        int i = 0;
        while (i < params.length()) {
            int start = i;
            while (i < params.length() && params.charAt(i) == '[') {
                i++;
            }
            if (i >= params.length()) break;

            char c = params.charAt(i);
            if (c == 'L') {
                int end = findTypeEnd(params, i);
                if (end < 0) break;
                String typeDesc = params.substring(start, end + 1);
                if (isSignature) {
                    paramTypes.add(trackAndFormatType(typeRecoverer.recoverGenericType(typeDesc)));
                } else {
                    paramTypes.add(trackAndFormatType(typeRecoverer.recoverType(typeDesc)));
                }
                paramWidths.add(1);
                i = end + 1;
            } else if (c == 'T') {
                int end = params.indexOf(';', i);
                if (end < 0) break;
                String typeVar = params.substring(i + 1, end);
                paramTypes.add(typeVar);
                paramWidths.add(1);
                i = end + 1;
            } else {
                String typeDesc = params.substring(start, i + 1);
                paramTypes.add(trackAndFormatType(typeRecoverer.recoverType(typeDesc)));
                boolean isArray = i > start;
                paramWidths.add(!isArray && (c == 'J' || c == 'D') ? 2 : 1);
                i++;
            }
        }

        StringBuilder sb = new StringBuilder();
        int slot = isStatic ? 0 : 1;
        for (int j = 0; j < paramTypes.size(); j++) {
            if (j > 0) sb.append(", ");
            String type = paramTypes.get(j);
            if (varargs && j == paramTypes.size() - 1 && type.endsWith("[]")) {
                type = type.substring(0, type.length() - 2) + "...";
            }
            sb.append(type).append(' ');
            String name = slotNames.get(slot);
            sb.append(name != null ? name : "arg" + j);
            slot += j < paramWidths.size() ? paramWidths.get(j) : 1;
        }
        return sb.toString();
    }

    private int findTypeEnd(String s, int start) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ';' && depth == 0) return i;
        }
        return -1;
    }

    /**
     * Emits import statements based on types actually used in the emitted code.
     */
    private void emitImports(IndentingWriter writer, String thisClassName) {
        String thisPackage = getPackageName(thisClassName);

        List<String> imports = usedTypes.stream()
                .filter(name -> !isJavaLangClass(name))
                .filter(name -> !isInternalJdkClass(name))
                .filter(name -> !getPackageName(name).equals(thisPackage))
                .filter(name -> !name.equals(thisClassName))
                .filter(name -> !isInnerClassOf(name, thisClassName))
                .filter(name -> !name.contains("$"))
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

    private boolean isInnerClassOf(String className, String outerClassName) {
        return className.startsWith(outerClassName + "$");
    }

    /**
     * Extracts the package name from an internal class name.
     */
    private String getPackageName(String internalName) {
        if (internalName == null) return "";
        return ClassNameUtil.getPackageName(internalName);
    }

    /**
     * Checks if a class is in the java.lang package (implicitly imported).
     */
    private boolean isJavaLangClass(String internalName) {
        if (!internalName.startsWith("java/lang/")) {
            return false;
        }
        String afterLang = internalName.substring("java/lang/".length());
        return !afterLang.contains("/");
    }

    private boolean isInternalJdkClass(String internalName) {
        return internalName.startsWith("java/lang/invoke/") || internalName.startsWith("sun/") || internalName.startsWith("jdk/internal/");
    }

    private boolean isSyntheticLambdaMethod(String methodName) {
        return methodName.startsWith("lambda$");
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
            if (attr instanceof RuntimeInvisibleAnnotationsAttribute) {
                RuntimeInvisibleAnnotationsAttribute annAttr = (RuntimeInvisibleAnnotationsAttribute) attr;
                for (Annotation ann : annAttr.getAnnotations()) {
                    emitAnnotation(writer, ann);
                }
            } else if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute annAttr = (RuntimeVisibleAnnotationsAttribute) attr;
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

        String typeName = resolveAnnotationType(ann.getTypeIndex());

        // Track the annotation type for imports
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
            String internalName = typeName.substring(1, typeName.length() - 1);
            usedTypes.add(internalName);
        }

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

        // Track the enum type for imports
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
            String internalName = typeName.substring(1, typeName.length() - 1);
            usedTypes.add(internalName);
            typeName = internalName;
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
        String internalName = null;

        // Handle descriptor format
        if (className.startsWith("L") && className.endsWith(";")) {
            internalName = className.substring(1, className.length() - 1);
            className = internalName;
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

        // Track the class type for imports
        if (internalName != null) {
            usedTypes.add(internalName);
        } else if (className.contains("/") || className.contains(".")) {
            usedTypes.add(className.replace('.', '/'));
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
        // Track the nested annotation type for imports
        if (typeName.startsWith("L") && typeName.endsWith(";")) {
            String internalName = typeName.substring(1, typeName.length() - 1);
            usedTypes.add(internalName);
        }
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
