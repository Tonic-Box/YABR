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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final List<BootstrapMethodDef> bootstrapMethodDefs = new ArrayList<>();
    private final List<LambdaBootstrapDef> lambdaBootstrapDefs = new ArrayList<>();
    com.tonic.parser.ConstPool constPool;

    private static class LambdaBootstrapDef {
        final String samDescriptor;
        final String implMethodOwner;
        final String implMethodName;
        final String implMethodDesc;

        LambdaBootstrapDef(String samDescriptor, String implMethodOwner,
                String implMethodName, String implMethodDesc) {
            this.samDescriptor = samDescriptor;
            this.implMethodOwner = implMethodOwner;
            this.implMethodName = implMethodName;
            this.implMethodDesc = implMethodDesc;
        }
    }

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

    /**
     * Represents a bootstrap method definition.
     */
    public static class BootstrapMethodDef {
        int methodHandleIndex;
        List<Integer> arguments;

        BootstrapMethodDef(int methodHandleIndex, List<Integer> arguments) {
            this.methodHandleIndex = methodHandleIndex;
            this.arguments = arguments;
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
     * Gets the constant pool for this class.
     * Only available after build() has been called.
     *
     * @return the constant pool, or null if build() hasn't been called yet
     */
    public com.tonic.parser.ConstPool getConstPool() {
        return constPool;
    }

    /**
     * Adds a bootstrap method and returns its index.
     *
     * @param methodHandleIndex The constant pool index of the method handle.
     * @param arguments The list of constant pool indices for the bootstrap arguments.
     * @return The index of the bootstrap method in the bootstrap methods table.
     */
    public int addBootstrapMethod(int methodHandleIndex, List<Integer> arguments) {
        int index = bootstrapMethodDefs.size();
        bootstrapMethodDefs.add(new BootstrapMethodDef(methodHandleIndex, arguments));
        return index;
    }

    /**
     * Helper to create a LambdaMetafactory bootstrap method.
     * Returns bootstrap method index. Can be called before build().
     *
     * @param samDescriptor The descriptor of the functional interface method (e.g., "()V" for Runnable.run).
     * @param implMethodOwner The class containing the implementation method.
     * @param implMethodName The name of the implementation method.
     * @param implMethodDesc The descriptor of the implementation method.
     * @return The index of the bootstrap method in the bootstrap methods table.
     */
    public int addLambdaBootstrap(String samDescriptor, String implMethodOwner,
            String implMethodName, String implMethodDesc) {
        int index = bootstrapMethodDefs.size() + lambdaBootstrapDefs.size();
        lambdaBootstrapDefs.add(new LambdaBootstrapDef(samDescriptor, implMethodOwner, implMethodName, implMethodDesc));
        return index;
    }

    private void processLambdaBootstraps() {
        for (LambdaBootstrapDef def : lambdaBootstrapDefs) {
            int metafactoryMethod = constPool.addMethodRef(
                "java/lang/invoke/LambdaMetafactory",
                "metafactory",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");
            int metafactoryHandle = constPool.addMethodHandle(6, metafactoryMethod);

            int samType = constPool.addMethodType(def.samDescriptor);
            int implMethod = constPool.addMethodRef(def.implMethodOwner, def.implMethodName, def.implMethodDesc);
            int implHandle = constPool.addMethodHandle(6, implMethod);
            int instantiatedType = constPool.addMethodType(def.samDescriptor);

            List<Integer> args = Arrays.asList(samType, implHandle, instantiatedType);
            bootstrapMethodDefs.add(new BootstrapMethodDef(metafactoryHandle, args));
        }
        lambdaBootstrapDefs.clear();
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
        constPool = classFile.getConstPool();

        processLambdaBootstraps();

        // Create fields
        for (FieldDef fd : fields) {
            classFile.createNewField(fd.access, fd.name, fd.descriptor, List.of());
        }

        for (MethodBuilder mb : methods) {
            mb.buildMethod(classFile);
        }

        // Generate BootstrapMethodsAttribute if needed
        if (!bootstrapMethodDefs.isEmpty()) {
            com.tonic.parser.attribute.BootstrapMethodsAttribute bsmAttr =
                new com.tonic.parser.attribute.BootstrapMethodsAttribute(constPool);
            for (BootstrapMethodDef def : bootstrapMethodDefs) {
                bsmAttr.addBootstrapMethod(def.methodHandleIndex, def.arguments);
            }
            classFile.getClassAttributes().add(bsmAttr);
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
        private final List<SizedOp> sizedOps = new ArrayList<>();
        private final List<ExceptionRegion> exceptionRegions = new ArrayList<>();
        private boolean usesLabels = false;
        private int maxLocalUsed = -1;

        private MethodBuilder(BytecodeBuilder parent, int access, String name, String descriptor) {
            this.parent = parent;
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }

        private void trackLocal(int index, boolean isWide) {
            int slots = isWide ? index + 2 : index + 1;
            if (slots > maxLocalUsed) {
                maxLocalUsed = slots;
            }
        }

        /**
         * Represents an exception handler region (try-catch block).
         */
        public static class ExceptionRegion {
            Label tryStart;
            Label tryEnd;
            Label handlerStart;
            String exceptionType; // e.g., "java/lang/Exception", null for catch-all

            ExceptionRegion(Label tryStart, Label tryEnd, Label handlerStart, String exceptionType) {
                this.tryStart = tryStart;
                this.tryEnd = tryEnd;
                this.handlerStart = handlerStart;
                this.exceptionType = exceptionType;
            }
        }

        // Helper to add a legacy op with known size
        private void addOp(BytecodeOp op, int size) {
            ops.add(op);
            sizedOps.add(new LegacyOp(op, size));
        }

        // ========== Label and Control Flow ==========

        /**
         * Creates a new unbound label.
         */
        public Label newLabel() {
            usesLabels = true;
            return new Label();
        }

        /**
         * Binds a label at the current bytecode position.
         */
        public MethodBuilder label(Label l) {
            usesLabels = true;
            sizedOps.add(new LabelOp(l));
            return this;
        }

        /**
         * Unconditional jump to label (goto).
         */
        public MethodBuilder goto_(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA7, target, 3));
            return this;
        }

        /**
         * Wide unconditional jump to label (goto_w).
         */
        public MethodBuilder goto_w(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xC8, target, 5));
            return this;
        }

        /**
         * Branch if int on stack equals zero.
         */
        public MethodBuilder ifeq(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x99, target, 3));
            return this;
        }

        /**
         * Branch if int on stack not equals zero.
         */
        public MethodBuilder ifne(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x9A, target, 3));
            return this;
        }

        /**
         * Branch if int on stack less than zero.
         */
        public MethodBuilder iflt(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x9B, target, 3));
            return this;
        }

        /**
         * Branch if int on stack greater than or equal to zero.
         */
        public MethodBuilder ifge(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x9C, target, 3));
            return this;
        }

        /**
         * Branch if int on stack greater than zero.
         */
        public MethodBuilder ifgt(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x9D, target, 3));
            return this;
        }

        /**
         * Branch if int on stack less than or equal to zero.
         */
        public MethodBuilder ifle(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x9E, target, 3));
            return this;
        }

        /**
         * Branch if two ints are equal.
         */
        public MethodBuilder if_icmpeq(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0x9F, target, 3));
            return this;
        }

        /**
         * Branch if two ints are not equal.
         */
        public MethodBuilder if_icmpne(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA0, target, 3));
            return this;
        }

        /**
         * Branch if first int less than second.
         */
        public MethodBuilder if_icmplt(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA1, target, 3));
            return this;
        }

        /**
         * Branch if first int greater than or equal to second.
         */
        public MethodBuilder if_icmpge(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA2, target, 3));
            return this;
        }

        /**
         * Branch if first int greater than second.
         */
        public MethodBuilder if_icmpgt(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA3, target, 3));
            return this;
        }

        /**
         * Branch if first int less than or equal to second.
         */
        public MethodBuilder if_icmple(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA4, target, 3));
            return this;
        }

        /**
         * Branch if two object references are equal.
         */
        public MethodBuilder if_acmpeq(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA5, target, 3));
            return this;
        }

        /**
         * Branch if two object references are not equal.
         */
        public MethodBuilder if_acmpne(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xA6, target, 3));
            return this;
        }

        /**
         * Branch if reference is null.
         */
        public MethodBuilder ifnull(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xC6, target, 3));
            return this;
        }

        /**
         * Branch if reference is not null.
         */
        public MethodBuilder ifnonnull(Label target) {
            usesLabels = true;
            sizedOps.add(new BranchOp(0xC7, target, 3));
            return this;
        }

        /**
         * Table switch on an int value.
         *
         * @param low the lowest case value
         * @param high the highest case value
         * @param cases map from case values to their target labels
         * @param defaultLabel the default target label
         * @return this builder
         */
        public MethodBuilder tableswitch(int low, int high, Map<Integer, Label> cases, Label defaultLabel) {
            usesLabels = true;
            sizedOps.add(new TableSwitchOp(low, high, cases, defaultLabel));
            return this;
        }

        /**
         * Lookup switch on an int value.
         *
         * @param cases map from case values to their target labels
         * @param defaultLabel the default target label
         * @return this builder
         */
        public MethodBuilder lookupswitch(Map<Integer, Label> cases, Label defaultLabel) {
            usesLabels = true;
            sizedOps.add(new LookupSwitchOp(cases, defaultLabel));
            return this;
        }

        // ========== Comparison Instructions ==========

        /**
         * Compare two longs. Pushes -1, 0, or 1.
         */
        public MethodBuilder lcmp() {
            addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(0x94, cw.getBytecodeSize())), 1);
            return this;
        }

        /**
         * Compare two floats (less than on NaN).
         */
        public MethodBuilder fcmpl() {
            addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(0x95, cw.getBytecodeSize())), 1);
            return this;
        }

        /**
         * Compare two floats (greater than on NaN).
         */
        public MethodBuilder fcmpg() {
            addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(0x96, cw.getBytecodeSize())), 1);
            return this;
        }

        /**
         * Compare two doubles (less than on NaN).
         */
        public MethodBuilder dcmpl() {
            addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(0x97, cw.getBytecodeSize())), 1);
            return this;
        }

        /**
         * Compare two doubles (greater than on NaN).
         */
        public MethodBuilder dcmpg() {
            addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(0x98, cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Increment Instruction ==========

        /**
         * Increment local variable by constant.
         */
        public MethodBuilder iinc(int varIndex, int increment) {
            trackLocal(varIndex, false);
            addOp((bc, cw) -> bc.addIInc(varIndex, increment), 3);
            return this;
        }

        // ========== Load Instructions ==========

        // Note: CodeWriter.insertXLoad/insertXStore always use the 2-byte form (opcode + index),
        // not the 1-byte specialized opcodes (xload_0, xload_1, etc.). So size is always 2.

        public MethodBuilder iload(int index) {
            trackLocal(index, false);
            addOp((bc, cw) -> bc.addILoad(index), 2);
            return this;
        }

        public MethodBuilder iload_0() { return iload(0); }
        public MethodBuilder iload_1() { return iload(1); }
        public MethodBuilder iload_2() { return iload(2); }
        public MethodBuilder iload_3() { return iload(3); }

        public MethodBuilder lload(int index) {
            trackLocal(index, true);
            addOp((bc, cw) -> bc.addLLoad(index), 2);
            return this;
        }

        public MethodBuilder lload_0() { return lload(0); }
        public MethodBuilder lload_1() { return lload(1); }
        public MethodBuilder lload_2() { return lload(2); }
        public MethodBuilder lload_3() { return lload(3); }

        public MethodBuilder fload(int index) {
            trackLocal(index, false);
            addOp((bc, cw) -> bc.addFLoad(index), 2);
            return this;
        }

        public MethodBuilder fload_0() { return fload(0); }
        public MethodBuilder fload_1() { return fload(1); }
        public MethodBuilder fload_2() { return fload(2); }
        public MethodBuilder fload_3() { return fload(3); }

        public MethodBuilder dload(int index) {
            trackLocal(index, true);
            addOp((bc, cw) -> bc.addDLoad(index), 2);
            return this;
        }

        public MethodBuilder dload_0() { return dload(0); }
        public MethodBuilder dload_1() { return dload(1); }
        public MethodBuilder dload_2() { return dload(2); }
        public MethodBuilder dload_3() { return dload(3); }

        public MethodBuilder aload(int index) {
            trackLocal(index, false);
            addOp((bc, cw) -> bc.addALoad(index), 2);
            return this;
        }

        public MethodBuilder aload_0() { return aload(0); }
        public MethodBuilder aload_1() { return aload(1); }
        public MethodBuilder aload_2() { return aload(2); }
        public MethodBuilder aload_3() { return aload(3); }

        // ========== Store Instructions ==========

        public MethodBuilder istore(int index) {
            trackLocal(index, false);
            addOp((bc, cw) -> bc.addIStore(index), 2);
            return this;
        }

        public MethodBuilder istore_0() { return istore(0); }
        public MethodBuilder istore_1() { return istore(1); }
        public MethodBuilder istore_2() { return istore(2); }
        public MethodBuilder istore_3() { return istore(3); }

        public MethodBuilder lstore(int index) {
            trackLocal(index, true);
            addOp((bc, cw) -> cw.insertLStore(cw.getBytecodeSize(), index), 2);
            return this;
        }

        public MethodBuilder lstore_0() { return lstore(0); }
        public MethodBuilder lstore_1() { return lstore(1); }
        public MethodBuilder lstore_2() { return lstore(2); }
        public MethodBuilder lstore_3() { return lstore(3); }

        public MethodBuilder fstore(int index) {
            trackLocal(index, false);
            addOp((bc, cw) -> cw.insertFStore(cw.getBytecodeSize(), index), 2);
            return this;
        }

        public MethodBuilder fstore_0() { return fstore(0); }
        public MethodBuilder fstore_1() { return fstore(1); }
        public MethodBuilder fstore_2() { return fstore(2); }
        public MethodBuilder fstore_3() { return fstore(3); }

        public MethodBuilder dstore(int index) {
            trackLocal(index, true);
            addOp((bc, cw) -> cw.insertDStore(cw.getBytecodeSize(), index), 2);
            return this;
        }

        public MethodBuilder dstore_0() { return dstore(0); }
        public MethodBuilder dstore_1() { return dstore(1); }
        public MethodBuilder dstore_2() { return dstore(2); }
        public MethodBuilder dstore_3() { return dstore(3); }

        public MethodBuilder astore(int index) {
            trackLocal(index, false);
            addOp((bc, cw) -> bc.addAStore(index), 2);
            return this;
        }

        public MethodBuilder astore_0() { return astore(0); }
        public MethodBuilder astore_1() { return astore(1); }
        public MethodBuilder astore_2() { return astore(2); }
        public MethodBuilder astore_3() { return astore(3); }

        // ========== Constant Instructions ==========

        public MethodBuilder iconst(int value) {
            int size = computeIconstSize(value);
            addOp((bc, cw) -> bc.addIConst(value), size);
            return this;
        }

        private int computeIconstSize(int value) {
            if (value >= -1 && value <= 5) return 1;  // iconst_m1 to iconst_5
            if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return 2;  // bipush
            if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return 3;  // sipush
            return 2;  // ldc (assume small constant pool index)
        }

        public MethodBuilder lconst(long value) {
            int size = (value == 0L || value == 1L) ? 1 : 3;  // lconst_0/1 or ldc2_w
            addOp((bc, cw) -> bc.addLConst(value), size);
            return this;
        }

        public MethodBuilder fconst(float value) {
            int size = (value == 0.0f || value == 1.0f || value == 2.0f) ? 1 : 2;
            addOp((bc, cw) -> bc.addFConst(value), size);
            return this;
        }

        public MethodBuilder dconst(double value) {
            int size = (value == 0.0 || value == 1.0) ? 1 : 3;  // dconst_0/1 or ldc2_w
            addOp((bc, cw) -> bc.addDConst(value), size);
            return this;
        }

        public MethodBuilder aconst_null() {
            addOp((bc, cw) -> bc.addAConstNull(), 1);
            return this;
        }

        public MethodBuilder ldc(String value) {
            addOp((bc, cw) -> bc.addLdc(value), 2);  // Assume small constant pool
            return this;
        }

        public MethodBuilder ldc_int(int value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int index = constPool.getIndexOf(constPool.findOrAddInteger(value));
                cw.appendInstruction(new LdcInstruction(constPool, 0x12, cw.getBytecodeSize(), index));
            }, 2);
            return this;
        }

        public MethodBuilder ldc_float(float value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int index = constPool.getIndexOf(constPool.findOrAddFloat(value));
                cw.appendInstruction(new LdcInstruction(constPool, 0x12, cw.getBytecodeSize(), index));
            }, 2);
            return this;
        }

        public MethodBuilder ldc_class(String className) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(className);
                int index = constPool.getIndexOf(classRef);
                cw.appendInstruction(new LdcInstruction(constPool, 0x12, cw.getBytecodeSize(), index));
            }, 2);
            return this;
        }

        public MethodBuilder ldcw_int(int value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int index = constPool.getIndexOf(constPool.findOrAddInteger(value));
                cw.appendInstruction(new LdcWInstruction(constPool, 0x13, cw.getBytecodeSize(), index));
            }, 3);
            return this;
        }

        public MethodBuilder ldcw_float(float value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int index = constPool.getIndexOf(constPool.findOrAddFloat(value));
                cw.appendInstruction(new LdcWInstruction(constPool, 0x13, cw.getBytecodeSize(), index));
            }, 3);
            return this;
        }

        public MethodBuilder ldcw_string(String value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int stringIndex = constPool.getIndexOf(constPool.findOrAddString(value));
                cw.appendInstruction(new LdcWInstruction(constPool, 0x13, cw.getBytecodeSize(), stringIndex));
            }, 3);
            return this;
        }

        public MethodBuilder ldcw_class(String className) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(className);
                int index = constPool.getIndexOf(classRef);
                cw.appendInstruction(new LdcWInstruction(constPool, 0x13, cw.getBytecodeSize(), index));
            }, 3);
            return this;
        }

        public MethodBuilder ldc2w_long(long value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int index = constPool.getIndexOf(constPool.findOrAddLong(value));
                cw.appendInstruction(new Ldc2WInstruction(constPool, 0x14, cw.getBytecodeSize(), index));
            }, 3);
            return this;
        }

        public MethodBuilder ldc2w_double(double value) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                int index = constPool.getIndexOf(constPool.findOrAddDouble(value));
                cw.appendInstruction(new Ldc2WInstruction(constPool, 0x14, cw.getBytecodeSize(), index));
            }, 3);
            return this;
        }

        // ========== Arithmetic Instructions ==========

        public MethodBuilder iadd() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IADD.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder isub() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.ISUB.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder imul() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IMUL.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder idiv() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IDIV.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder irem() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IREM.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ineg() {
            addOp((bc, cw) -> cw.appendInstruction(new INegInstruction(0x74, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lneg() {
            addOp((bc, cw) -> cw.appendInstruction(new LNegInstruction(0x75, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder fneg() {
            addOp((bc, cw) -> cw.appendInstruction(new FNegInstruction(0x76, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dneg() {
            addOp((bc, cw) -> cw.appendInstruction(new DNegInstruction(0x77, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ladd() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LADD.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lsub() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LSUB.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lmul() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LMUL.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ldiv() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LDIV.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lrem() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LREM.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder fadd() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FADD.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder fsub() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FSUB.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder fmul() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FMUL.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder fdiv() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FDIV.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder frem() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FREM.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dadd() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DADD.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dsub() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DSUB.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dmul() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DMUL.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ddiv() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DDIV.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder drem() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DREM.getOpcode(), cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Bitwise Instructions ==========

        public MethodBuilder iand() {
            addOp((bc, cw) -> cw.appendInstruction(new IAndInstruction(0x7E, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ior() {
            addOp((bc, cw) -> cw.appendInstruction(new IOrInstruction(0x80, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ixor() {
            addOp((bc, cw) -> cw.appendInstruction(new IXorInstruction(0x82, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ishl() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x78, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder ishr() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x7A, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder iushr() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x7C, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder land() {
            addOp((bc, cw) -> cw.appendInstruction(new LandInstruction(0x7F, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lor() {
            addOp((bc, cw) -> cw.appendInstruction(new LorInstruction(0x81, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lxor() {
            addOp((bc, cw) -> cw.appendInstruction(new LXorInstruction(0x83, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lshl() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x79, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lshr() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x7B, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lushr() {
            addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(0x7D, cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Conversion Instructions ==========

        public MethodBuilder i2l() {
            addOp((bc, cw) -> cw.appendInstruction(new I2LInstruction(0x85, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder i2f() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x86, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder i2d() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x87, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder l2i() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x88, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder l2f() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x89, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder l2d() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x8A, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder f2i() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x8B, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder f2l() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x8C, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder f2d() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x8D, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder d2i() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x8E, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder d2l() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x8F, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder d2f() {
            addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(0x90, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder i2b() {
            addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(0x91, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder i2c() {
            addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(0x92, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder i2s() {
            addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(0x93, cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Miscellaneous Instructions ==========

        public MethodBuilder nop() {
            addOp((bc, cw) -> cw.appendInstruction(new NopInstruction(0x00, cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Stack Instructions ==========

        public MethodBuilder dup() {
            addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(0x59, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dup_x1() {
            addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(0x5A, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dup_x2() {
            addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(0x5B, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dup2() {
            addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(0x5C, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dup2_x1() {
            addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(0x5D, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dup2_x2() {
            addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(0x5E, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder pop() {
            addOp((bc, cw) -> cw.appendInstruction(new PopInstruction(0x57, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder pop2() {
            addOp((bc, cw) -> cw.appendInstruction(new Pop2Instruction(0x58, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder swap() {
            addOp((bc, cw) -> cw.appendInstruction(new SwapInstruction(0x5F, cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Return Instructions ==========

        public MethodBuilder ireturn() {
            addOp((bc, cw) -> bc.addReturn(ReturnType.IRETURN), 1);
            return this;
        }

        public MethodBuilder lreturn() {
            addOp((bc, cw) -> bc.addReturn(ReturnType.LRETURN), 1);
            return this;
        }

        public MethodBuilder freturn() {
            addOp((bc, cw) -> bc.addReturn(ReturnType.FRETURN), 1);
            return this;
        }

        public MethodBuilder dreturn() {
            addOp((bc, cw) -> bc.addReturn(ReturnType.DRETURN), 1);
            return this;
        }

        public MethodBuilder areturn() {
            addOp((bc, cw) -> bc.addReturn(ReturnType.ARETURN), 1);
            return this;
        }

        public MethodBuilder vreturn() {
            addOp((bc, cw) -> bc.addReturn(ReturnType.RETURN), 1);
            return this;
        }

        // ========== Field Access ==========

        public MethodBuilder getstatic(String owner, String name, String desc) {
            addOp((bc, cw) -> bc.addGetStatic(owner, name, desc), 3);
            return this;
        }

        public MethodBuilder putstatic(String owner, String name, String desc) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(name);
                com.tonic.parser.constpool.Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(desc);
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(owner);
                com.tonic.parser.constpool.NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));
                com.tonic.parser.constpool.FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
                int fieldRefIndex = constPool.getIndexOf(fieldRef);
                bc.addPutStatic(fieldRefIndex);
            }, 3);
            return this;
        }

        public MethodBuilder putfield(String owner, String name, String desc) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(name);
                com.tonic.parser.constpool.Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(desc);
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(owner);
                com.tonic.parser.constpool.NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));
                com.tonic.parser.constpool.FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
                int fieldRefIndex = constPool.getIndexOf(fieldRef);
                bc.addPutField(fieldRefIndex);
            }, 3);
            return this;
        }

        public MethodBuilder getfield(String owner, String name, String desc) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.Utf8Item fieldNameUtf8 = constPool.findOrAddUtf8(name);
                com.tonic.parser.constpool.Utf8Item fieldDescUtf8 = constPool.findOrAddUtf8(desc);
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(owner);
                com.tonic.parser.constpool.NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(fieldNameUtf8.getIndex(constPool), fieldDescUtf8.getIndex(constPool));
                com.tonic.parser.constpool.FieldRefItem fieldRef = constPool.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
                int fieldRefIndex = constPool.getIndexOf(fieldRef);
                bc.addGetField(fieldRefIndex);
            }, 3);
            return this;
        }

        // ========== Array Instructions ==========

        public MethodBuilder newarray(int atype) {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.NewArrayInstruction(0xBC, cw.getBytecodeSize(), atype, 0)), 2);
            return this;
        }

        public MethodBuilder iastore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.IAStoreInstruction(0x4F, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder iaload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.IALoadInstruction(0x2E, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder arraylength() {
            addOp((bc, cw) -> cw.appendInstruction(new ArrayLengthInstruction(0xBE, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder laload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.LALoadInstruction(0x2F, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder lastore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.LAStoreInstruction(0x50, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder faload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.FALoadInstruction(0x30, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder fastore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.FAStoreInstruction(0x51, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder daload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.DALoadInstruction(0x31, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder dastore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.DAStoreInstruction(0x52, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder baload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.BALOADInstruction(0x33, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder bastore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.BAStoreInstruction(0x54, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder caload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.CALoadInstruction(0x34, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder castore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.CAStoreInstruction(0x55, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder saload() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.SALoadInstruction(0x35, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder sastore() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.SAStoreInstruction(0x56, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder aaload() {
            addOp((bc, cw) -> cw.appendInstruction(new AALoadInstruction(0x32, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder aastore() {
            addOp((bc, cw) -> cw.appendInstruction(new AAStoreInstruction(0x53, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder anewarray(String type) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(type);
                int classRefIndex = constPool.getIndexOf(classRef);
                cw.appendInstruction(new ANewArrayInstruction(constPool, 0xBD, cw.getBytecodeSize(), classRefIndex, 0));
            }, 3);
            return this;
        }

        public MethodBuilder new_(String type) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(type);
                int classRefIndex = constPool.getIndexOf(classRef);
                cw.appendInstruction(new NewInstruction(constPool, 0xBB, cw.getBytecodeSize(), classRefIndex));
            }, 3);
            return this;
        }

        public MethodBuilder multianewarray(String type, int dimensions) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(type);
                int classRefIndex = constPool.getIndexOf(classRef);
                cw.appendInstruction(new MultiANewArrayInstruction(constPool, 0xC5, cw.getBytecodeSize(), classRefIndex, dimensions));
            }, 4);
            return this;
        }

        public MethodBuilder monitorenter() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.MonitorEnterInstruction(0xC2, cw.getBytecodeSize())), 1);
            return this;
        }

        public MethodBuilder monitorexit() {
            addOp((bc, cw) -> cw.appendInstruction(new com.tonic.analysis.instruction.MonitorExitInstruction(0xC3, cw.getBytecodeSize())), 1);
            return this;
        }

        // ========== Type Instructions ==========

        public MethodBuilder instanceof_(String type) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(type);
                int classRefIndex = constPool.getIndexOf(classRef);
                cw.appendInstruction(new InstanceOfInstruction(constPool, 0xC1, cw.getBytecodeSize(), classRefIndex));
            }, 3);
            return this;
        }

        public MethodBuilder checkcast(String type) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(type);
                int classRefIndex = constPool.getIndexOf(classRef);
                cw.appendInstruction(new CheckCastInstruction(constPool, 0xC0, cw.getBytecodeSize(), classRefIndex));
            }, 3);
            return this;
        }

        // ========== Method Invocation ==========

        public MethodBuilder invokevirtual(String owner, String name, String desc) {
            addOp((bc, cw) -> bc.addInvokeVirtual(owner, name, desc), 3);
            return this;
        }

        public MethodBuilder invokestatic(String owner, String name, String desc) {
            addOp((bc, cw) -> bc.addInvokeStatic(owner, name, desc), 3);
            return this;
        }

        public MethodBuilder invokespecial(String owner, String name, String desc) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(owner);
                com.tonic.parser.constpool.NameAndTypeRefItem nameAndType = constPool.findOrAddNameAndType(name, desc);
                com.tonic.parser.constpool.MethodRefItem methodRef = constPool.findOrAddMethodRef(
                    constPool.getIndexOf(classRef), constPool.getIndexOf(nameAndType));
                int methodRefIndex = constPool.getIndexOf(methodRef);
                bc.addInvokeSpecial(methodRefIndex);
            }, 3);
            return this;
        }

        public MethodBuilder invokeinterface(String owner, String name, String desc, int count) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool constPool = bc.getConstPool();
                com.tonic.parser.constpool.InterfaceRefItem interfaceRef = constPool.findOrAddInterfaceRef(owner, name, desc);
                int interfaceRefIndex = constPool.getIndexOf(interfaceRef);
                bc.addInvokeInterface(interfaceRefIndex, count);
            }, 5);
            return this;
        }

        /**
         * Emits an invokedynamic instruction.
         *
         * @param name The name of the dynamic call site.
         * @param descriptor The descriptor of the dynamic call site.
         * @param bootstrapIndex The index into the bootstrap methods table.
         * @return this builder for chaining
         */
        public MethodBuilder invokedynamic(String name, String descriptor, int bootstrapIndex) {
            addOp((bc, cw) -> {
                com.tonic.parser.ConstPool cp = parent.constPool;
                int nameAndType = cp.addNameAndType(name, descriptor);
                int indyIndex = cp.addInvokeDynamic(bootstrapIndex, nameAndType);
                bc.addInvokeDynamic(indyIndex);
            }, 5); // invokedynamic is 5 bytes
            return this;
        }

        // ========== Exception Handling ==========

        public MethodBuilder athrow() {
            addOp((bc, cw) -> cw.appendInstruction(new ATHROWInstruction(0xBF, cw.getBytecodeSize())), 1);
            return this;
        }

        /**
         * Registers an exception handler for a try-catch block.
         *
         * @param tryStart start of the try block
         * @param tryEnd end of the try block (exclusive)
         * @param handler start of the exception handler
         * @param exceptionType exception type (internal name, e.g., "java/lang/Exception"), or null for catch-all
         * @return this builder for chaining
         */
        public MethodBuilder tryCatch(Label tryStart, Label tryEnd, Label handler, String exceptionType) {
            exceptionRegions.add(new ExceptionRegion(tryStart, tryEnd, handler, exceptionType));
            return this;
        }

        /**
         * Registers a catch-all exception handler (for finally blocks).
         *
         * @param tryStart start of the try block
         * @param tryEnd end of the try block (exclusive)
         * @param handler start of the exception handler
         * @return this builder for chaining
         */
        public MethodBuilder tryCatchAll(Label tryStart, Label tryEnd, Label handler) {
            return tryCatch(tryStart, tryEnd, handler, null);
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

            Map<Label, Integer> labelOffsets = new HashMap<>();

            if (usesLabels) {
                // Two-pass approach for label resolution with fixed-point iteration for switch padding

                // Phase 1: Calculate label positions by summing instruction sizes (with iteration for switch padding)
                int maxIterations = 10;
                for (int iter = 0; iter < maxIterations; iter++) {
                    int offset = 0;
                    boolean changed = false;
                    for (SizedOp op : sizedOps) {
                        if (op instanceof LabelOp) {
                            Label label = ((LabelOp) op).label;
                            Integer oldOffset = labelOffsets.get(label);
                            if (oldOffset == null || oldOffset != offset) {
                                labelOffsets.put(label, offset);
                                label.bind(offset);
                                changed = true;
                            }
                        } else {
                            int size = op.getSizeAt(offset);
                            offset += size;
                        }
                    }
                    if (!changed) {
                        break;  // Converged
                    }
                }

                // Phase 2: Emit bytecode with resolved label offsets
                int currentOffset = 0;
                for (SizedOp op : sizedOps) {
                    op.emit(bc, cw, labelOffsets, currentOffset);
                    currentOffset += op.getSizeAt(currentOffset);
                }
            } else {
                // Legacy path for backward compatibility
                for (BytecodeOp op : ops) {
                    op.apply(bc, cw);
                }
            }

            // Add exception handlers to the code attribute
            if (!exceptionRegions.isEmpty()) {
                com.tonic.parser.attribute.CodeAttribute codeAttr = method.getCodeAttribute();
                com.tonic.parser.ConstPool constPool = classFile.getConstPool();

                for (ExceptionRegion region : exceptionRegions) {
                    int startPc = labelOffsets.get(region.tryStart);
                    int endPc = labelOffsets.get(region.tryEnd);
                    int handlerPc = labelOffsets.get(region.handlerStart);

                    int catchType = 0; // 0 = catch all
                    if (region.exceptionType != null) {
                        // Add class reference to constant pool
                        com.tonic.parser.constpool.ClassRefItem classRef = constPool.findOrAddClass(region.exceptionType);
                        catchType = constPool.getIndexOf(classRef);
                    }

                    com.tonic.parser.attribute.table.ExceptionTableEntry entry =
                        new com.tonic.parser.attribute.table.ExceptionTableEntry(
                            startPc, endPc, handlerPc, catchType);
                    codeAttr.getExceptionTable().add(entry);
                }
            }

            if (maxLocalUsed > 0) {
                com.tonic.parser.attribute.CodeAttribute codeAttr = method.getCodeAttribute();
                if (codeAttr != null && codeAttr.getMaxLocals() < maxLocalUsed) {
                    codeAttr.setMaxLocals(maxLocalUsed);
                }
            }

            bc.finalizeBytecode();
        }
    }

    @FunctionalInterface
    private interface BytecodeOp {
        void apply(Bytecode bc, CodeWriter cw);
    }

    // ========== Label Support for Control Flow ==========

    /**
     * Represents a label (jump target) in bytecode.
     * Labels can be forward-referenced before being bound.
     */
    public static class Label {
        private int offset = -1;  // -1 = unbound

        /**
         * Checks if this label has been bound to a bytecode offset.
         */
        public boolean isBound() {
            return offset >= 0;
        }

        /**
         * Gets the bytecode offset this label is bound to.
         * @throws IllegalStateException if label is not bound
         */
        public int getOffset() {
            if (!isBound()) {
                throw new IllegalStateException("Label not bound");
            }
            return offset;
        }

        void bind(int offset) {
            if (isBound()) {
                throw new IllegalStateException("Label already bound at offset " + this.offset);
            }
            this.offset = offset;
        }
    }

    /**
     * A bytecode operation that knows its size for two-pass label resolution.
     */
    private static abstract class SizedOp {
        /**
         * Returns the size in bytes this operation will produce.
         */
        abstract int getSize();

        /**
         * Returns the size in bytes this operation will produce at the given offset.
         * Default implementation returns getSize(), but switch instructions override this
         * to calculate padding correctly.
         *
         * @param offset the bytecode offset where this instruction will be placed
         * @return the size in bytes
         */
        int getSizeAt(int offset) {
            return getSize();
        }

        /**
         * Emits the bytecode for this operation.
         * @param bc the Bytecode instance
         * @param cw the CodeWriter instance
         * @param labelOffsets map of labels to their bytecode offsets
         * @param currentOffset the current bytecode offset
         */
        abstract void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset);
    }

    /**
     * A label marker (size 0, just records position).
     */
    private static class LabelOp extends SizedOp {
        final Label label;

        LabelOp(Label label) {
            this.label = label;
        }

        @Override
        int getSize() {
            return 0;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            // Labels don't emit bytecode, they just mark positions
        }
    }

    /**
     * Wraps a legacy BytecodeOp with a known size.
     */
    private static class LegacyOp extends SizedOp {
        final BytecodeOp op;
        final int size;

        LegacyOp(BytecodeOp op, int size) {
            this.op = op;
            this.size = size;
        }

        @Override
        int getSize() {
            return size;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            op.apply(bc, cw);
        }
    }

    /**
     * A branch instruction that targets a label.
     */
    private static class BranchOp extends SizedOp {
        final int opcode;
        final Label target;
        final int size;  // 3 for short branches, 5 for wide

        BranchOp(int opcode, Label target, int size) {
            this.opcode = opcode;
            this.target = target;
            this.size = size;
        }

        @Override
        int getSize() {
            return size;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            Integer targetOffset = labelOffsets.get(target);
            if (targetOffset == null) {
                throw new IllegalStateException("Label not found in label map");
            }
            int relativeOffset = targetOffset - currentOffset;

            if (opcode == 0xA7) {  // goto
                cw.appendInstruction(new GotoInstruction(opcode, currentOffset, (short) relativeOffset));
            } else if (opcode == 0xC8) {  // goto_w
                cw.appendInstruction(new GotoInstruction(opcode, currentOffset, relativeOffset));
            } else {
                // Conditional branches
                cw.appendInstruction(new ConditionalBranchInstruction(opcode, currentOffset, (short) relativeOffset));
            }
        }
    }

    /**
     * A tableswitch instruction with label-based targets.
     */
    private static class TableSwitchOp extends SizedOp {
        final int low;
        final int high;
        final Map<Integer, Label> cases;
        final Label defaultLabel;

        TableSwitchOp(int low, int high, Map<Integer, Label> cases, Label defaultLabel) {
            this.low = low;
            this.high = high;
            this.cases = cases;
            this.defaultLabel = defaultLabel;
        }

        @Override
        int getSize() {
            // Size = 1 (opcode) + padding (worst case 3) + 12 (default + low + high) + (high-low+1)*4
            return 1 + 3 + 12 + (high - low + 1) * 4;
        }

        @Override
        int getSizeAt(int offset) {
            // Calculate actual padding based on the offset
            int padding = (4 - ((offset + 1) % 4)) % 4;
            return 1 + padding + 12 + (high - low + 1) * 4;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            // Calculate actual padding based on current offset
            int padding = (4 - ((currentOffset + 1) % 4)) % 4;

            // Resolve default label offset
            Integer defaultTargetOffset = labelOffsets.get(defaultLabel);
            if (defaultTargetOffset == null) {
                throw new IllegalStateException("Default label not found in label map");
            }
            int defaultOffset = defaultTargetOffset - currentOffset;

            // Build jump offsets map with resolved label offsets
            Map<Integer, Integer> jumpOffsets = new java.util.LinkedHashMap<>();
            for (int key = low; key <= high; key++) {
                Label caseLabel = cases.get(key);
                if (caseLabel != null) {
                    Integer caseTargetOffset = labelOffsets.get(caseLabel);
                    if (caseTargetOffset == null) {
                        throw new IllegalStateException("Case label for key " + key + " not found in label map");
                    }
                    jumpOffsets.put(key, caseTargetOffset - currentOffset);
                } else {
                    // Use default offset if no specific case
                    jumpOffsets.put(key, defaultOffset);
                }
            }

            // Create and emit the instruction
            TableSwitchInstruction instr = new TableSwitchInstruction(
                0xAA, currentOffset, padding, defaultOffset, low, high, jumpOffsets);
            cw.appendInstruction(instr);
        }
    }

    /**
     * A lookupswitch instruction with label-based targets.
     */
    private static class LookupSwitchOp extends SizedOp {
        final Map<Integer, Label> cases;
        final Label defaultLabel;

        LookupSwitchOp(Map<Integer, Label> cases, Label defaultLabel) {
            this.cases = cases;
            this.defaultLabel = defaultLabel;
        }

        @Override
        int getSize() {
            // Size = 1 (opcode) + padding (worst case 3) + 8 (default + npairs) + npairs*8
            return 1 + 3 + 8 + cases.size() * 8;
        }

        @Override
        int getSizeAt(int offset) {
            // Calculate actual padding based on the offset
            int padding = (4 - ((offset + 1) % 4)) % 4;
            return 1 + padding + 8 + cases.size() * 8;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            // Calculate actual padding based on current offset
            int padding = (4 - ((currentOffset + 1) % 4)) % 4;

            // Resolve default label offset
            Integer defaultTargetOffset = labelOffsets.get(defaultLabel);
            if (defaultTargetOffset == null) {
                throw new IllegalStateException("Default label not found in label map");
            }
            int defaultOffset = defaultTargetOffset - currentOffset;

            // Build match offsets map with resolved label offsets
            Map<Integer, Integer> matchOffsets = new java.util.LinkedHashMap<>();
            for (Map.Entry<Integer, Label> entry : cases.entrySet()) {
                Integer caseTargetOffset = labelOffsets.get(entry.getValue());
                if (caseTargetOffset == null) {
                    throw new IllegalStateException("Case label for key " + entry.getKey() + " not found in label map");
                }
                matchOffsets.put(entry.getKey(), caseTargetOffset - currentOffset);
            }

            // Create and emit the instruction
            int npairs = matchOffsets.size();
            LookupSwitchInstruction instr = new LookupSwitchInstruction(
                0xAB, currentOffset, padding, defaultOffset, npairs, matchOffsets);
            cw.appendInstruction(instr);
        }
    }
}
