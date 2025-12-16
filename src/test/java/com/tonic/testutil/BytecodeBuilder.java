package com.tonic.testutil;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;

import static com.tonic.analysis.instruction.ArithmeticInstruction.ArithmeticType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating test classes with bytecode.
 * Provides a simple DSL for constructing class files programmatically.
 *
 * <p>Example usage:
 * <pre>{@code
 * ClassFile cf = BytecodeBuilder.forClass("com/test/Calculator")
 *     .publicStaticMethod("add", "(II)I")
 *         .iload(0)
 *         .iload(1)
 *         .iadd()
 *         .ireturn()
 *     .endMethod()
 *     .build();
 * }</pre>
 */
public class BytecodeBuilder {

    private final String className;
    private final ClassPool pool;
    private ClassFile classFile;
    private final List<MethodBuilder> methods = new ArrayList<>();
    private final List<FieldDef> fields = new ArrayList<>();

    private static class FieldDef {
        final int access;
        final String name;
        final String descriptor;
        FieldDef(int access, String name, String descriptor) {
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }
    }

    private BytecodeBuilder(String className) {
        this.className = className;
        this.pool = new ClassPool(true);
    }

    /**
     * Creates a new BytecodeBuilder for the given class name.
     *
     * @param className internal class name (e.g., "com/test/MyClass")
     * @return a new BytecodeBuilder
     */
    public static BytecodeBuilder forClass(String className) {
        return new BytecodeBuilder(className);
    }

    /**
     * Adds a public static method to the class.
     *
     * @param name method name
     * @param descriptor method descriptor (e.g., "(II)I")
     * @return a MethodBuilder for the method
     */
    public MethodBuilder publicStaticMethod(String name, String descriptor) {
        int access = new AccessBuilder().setPublic().setStatic().build();
        return method(access, name, descriptor);
    }

    /**
     * Adds a public instance method to the class.
     *
     * @param name method name
     * @param descriptor method descriptor
     * @return a MethodBuilder for the method
     */
    public MethodBuilder publicMethod(String name, String descriptor) {
        int access = new AccessBuilder().setPublic().build();
        return method(access, name, descriptor);
    }

    /**
     * Adds a method with specified access flags.
     *
     * @param access access flags
     * @param name method name
     * @param descriptor method descriptor
     * @return a MethodBuilder for the method
     */
    public MethodBuilder method(int access, String name, String descriptor) {
        MethodBuilder mb = new MethodBuilder(this, access, name, descriptor);
        methods.add(mb);
        return mb;
    }

    /**
     * Adds a field to the class.
     *
     * @param access field access flags
     * @param name field name
     * @param descriptor field descriptor
     * @return this builder for chaining
     */
    public BytecodeBuilder field(int access, String name, String descriptor) {
        fields.add(new FieldDef(access, name, descriptor));
        return this;
    }

    /**
     * Builds the final ClassFile.
     *
     * @return the constructed ClassFile
     * @throws IOException if building fails
     */
    public ClassFile build() throws IOException {
        int classAccess = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass(className, classAccess);

        // Create fields
        for (FieldDef fd : fields) {
            classFile.createNewField(fd.access, fd.name, fd.descriptor, List.of());
        }

        for (MethodBuilder mb : methods) {
            mb.buildMethod(classFile);
        }

        return classFile;
    }

    /**
     * Builder for a single method's bytecode.
     */
    public static class MethodBuilder {
        private final BytecodeBuilder parent;
        private final int access;
        private final String name;
        private final String descriptor;
        private final List<BytecodeOp> ops = new ArrayList<>();

        private MethodBuilder(BytecodeBuilder parent, int access, String name, String descriptor) {
            this.parent = parent;
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }

        // ========== Load Instructions ==========

        public MethodBuilder iload(int index) {
            ops.add((bc, cw) -> bc.addILoad(index));
            return this;
        }

        public MethodBuilder lload(int index) {
            ops.add((bc, cw) -> bc.addLLoad(index));
            return this;
        }

        public MethodBuilder fload(int index) {
            ops.add((bc, cw) -> bc.addFLoad(index));
            return this;
        }

        public MethodBuilder dload(int index) {
            ops.add((bc, cw) -> bc.addDLoad(index));
            return this;
        }

        public MethodBuilder aload(int index) {
            ops.add((bc, cw) -> bc.addALoad(index));
            return this;
        }

        // ========== Store Instructions ==========

        public MethodBuilder istore(int index) {
            ops.add((bc, cw) -> bc.addIStore(index));
            return this;
        }

        public MethodBuilder lstore(int index) {
            ops.add((bc, cw) -> cw.insertLStore(cw.getBytecodeSize(), index));
            return this;
        }

        public MethodBuilder fstore(int index) {
            ops.add((bc, cw) -> cw.insertFStore(cw.getBytecodeSize(), index));
            return this;
        }

        public MethodBuilder dstore(int index) {
            ops.add((bc, cw) -> cw.insertDStore(cw.getBytecodeSize(), index));
            return this;
        }

        public MethodBuilder astore(int index) {
            ops.add((bc, cw) -> bc.addAStore(index));
            return this;
        }

        // ========== Constant Instructions ==========

        public MethodBuilder iconst(int value) {
            ops.add((bc, cw) -> bc.addIConst(value));
            return this;
        }

        public MethodBuilder lconst(long value) {
            ops.add((bc, cw) -> bc.addLConst(value));
            return this;
        }

        public MethodBuilder fconst(float value) {
            ops.add((bc, cw) -> bc.addFConst(value));
            return this;
        }

        public MethodBuilder dconst(double value) {
            ops.add((bc, cw) -> bc.addDConst(value));
            return this;
        }

        public MethodBuilder aconst_null() {
            ops.add((bc, cw) -> bc.addAConstNull());
            return this;
        }

        public MethodBuilder ldc(String value) {
            ops.add((bc, cw) -> bc.addLdc(value));
            return this;
        }

        // ========== Arithmetic Instructions ==========

        public MethodBuilder iadd() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IADD.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder isub() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.ISUB.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder imul() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IMUL.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder idiv() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IDIV.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder irem() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IREM.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ineg() {
            ops.add((bc, cw) -> cw.appendInstruction(new INegInstruction(0x74, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ladd() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LADD.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder lsub() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LSUB.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder lmul() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LMUL.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ldiv() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LDIV.getOpcode(), cw.getBytecodeSize())));
            return this;
        }

        // ========== Bitwise Instructions ==========

        public MethodBuilder iand() {
            ops.add((bc, cw) -> cw.appendInstruction(new IAndInstruction(0x7E, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ior() {
            ops.add((bc, cw) -> cw.appendInstruction(new IOrInstruction(0x80, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ixor() {
            ops.add((bc, cw) -> cw.appendInstruction(new IXorInstruction(0x82, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ishl() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x78, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder ishr() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x7A, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder iushr() {
            ops.add((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x7C, cw.getBytecodeSize())));
            return this;
        }

        // ========== Conversion Instructions ==========

        public MethodBuilder i2l() {
            ops.add((bc, cw) -> cw.appendInstruction(new I2LInstruction(0x85, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder i2f() {
            ops.add((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x86, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder i2d() {
            ops.add((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x87, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder l2i() {
            ops.add((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x88, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder i2b() {
            ops.add((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(0x91, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder i2c() {
            ops.add((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(0x92, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder i2s() {
            ops.add((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(0x93, cw.getBytecodeSize())));
            return this;
        }

        // ========== Stack Instructions ==========

        public MethodBuilder dup() {
            ops.add((bc, cw) -> cw.appendInstruction(new DupInstruction(0x59, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder dup2() {
            ops.add((bc, cw) -> cw.appendInstruction(new DupInstruction(0x5C, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder pop() {
            ops.add((bc, cw) -> cw.appendInstruction(new PopInstruction(0x57, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder pop2() {
            ops.add((bc, cw) -> cw.appendInstruction(new Pop2Instruction(0x58, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder swap() {
            ops.add((bc, cw) -> cw.appendInstruction(new SwapInstruction(0x5F, cw.getBytecodeSize())));
            return this;
        }

        // ========== Return Instructions ==========

        public MethodBuilder ireturn() {
            ops.add((bc, cw) -> bc.addReturn(ReturnType.IRETURN));
            return this;
        }

        public MethodBuilder lreturn() {
            ops.add((bc, cw) -> bc.addReturn(ReturnType.LRETURN));
            return this;
        }

        public MethodBuilder freturn() {
            ops.add((bc, cw) -> bc.addReturn(ReturnType.FRETURN));
            return this;
        }

        public MethodBuilder dreturn() {
            ops.add((bc, cw) -> bc.addReturn(ReturnType.DRETURN));
            return this;
        }

        public MethodBuilder areturn() {
            ops.add((bc, cw) -> bc.addReturn(ReturnType.ARETURN));
            return this;
        }

        public MethodBuilder vreturn() {
            ops.add((bc, cw) -> bc.addReturn(ReturnType.RETURN));
            return this;
        }

        // ========== Field Access ==========

        public MethodBuilder getstatic(String owner, String name, String desc) {
            ops.add((bc, cw) -> bc.addGetStatic(owner, name, desc));
            return this;
        }

        public MethodBuilder putfield(String owner, String name, String desc) {
            ops.add((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(name);
                com.tonic.parser.constpool.Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(desc);
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(owner);
                com.tonic.parser.constpool.NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));
                com.tonic.parser.constpool.FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
                int fieldRefIndex = constPool.getIndexOf(fieldRef);
                bc.addPutField(fieldRefIndex);
            });
            return this;
        }

        public MethodBuilder getfield(String owner, String name, String desc) {
            ops.add((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(name);
                com.tonic.parser.constpool.Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(desc);
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(owner);
                com.tonic.parser.constpool.NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));
                com.tonic.parser.constpool.FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
                int fieldRefIndex = constPool.getIndexOf(fieldRef);
                bc.addGetField(fieldRefIndex);
            });
            return this;
        }

        // ========== Array Instructions ==========

        public MethodBuilder newarray(int atype) {
            // NewArrayInstruction constructor: (opcode, offset, typeCode, count)
            // For bytecode emission, we use count=0 as placeholder since the actual count comes from stack
            ops.add((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.NewArrayInstruction(0xBC, cw.getBytecodeSize(), atype, 0)));
            return this;
        }

        public MethodBuilder iastore() {
            ops.add((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.IAStoreInstruction(0x4F, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder iaload() {
            ops.add((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.IALoadInstruction(0x2E, cw.getBytecodeSize())));
            return this;
        }

        public MethodBuilder invokevirtual(String owner, String name, String desc) {
            ops.add((bc, cw) -> bc.addInvokeVirtual(owner, name, desc));
            return this;
        }

        public MethodBuilder invokestatic(String owner, String name, String desc) {
            ops.add((bc, cw) -> bc.addInvokeStatic(owner, name, desc));
            return this;
        }

        // ========== Exception Handling ==========

        public MethodBuilder athrow() {
            ops.add((bc, cw) -> cw.appendInstruction(new ATHROWInstruction(0xBF, cw.getBytecodeSize())));
            return this;
        }

        // ========== Builder Methods ==========

        /**
         * Ends the method definition and returns to the class builder.
         *
         * @return the parent BytecodeBuilder
         */
        public BytecodeBuilder endMethod() {
            return parent;
        }

        /**
         * Builds the final ClassFile (convenience method).
         *
         * @return the constructed ClassFile
         * @throws IOException if building fails
         */
        public ClassFile build() throws IOException {
            return parent.build();
        }

        void buildMethod(ClassFile classFile) throws IOException {
            MethodEntry method = classFile.createNewMethodWithDescriptor(access, name, descriptor);
            Bytecode bc = new Bytecode(method);
            CodeWriter cw = bc.getCodeWriter();

            for (BytecodeOp op : ops) {
                op.apply(bc, cw);
            }

            bc.finalizeBytecode();
        }
    }

    @FunctionalInterface
    private interface BytecodeOp {
        void apply(Bytecode bc, CodeWriter cw);
    }
}
