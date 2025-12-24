package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.instruction.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherPart2Test {

    private OpcodeDispatcher dispatcher;
    private ConcreteStack stack;
    private ConcreteLocals locals;
    private TestDispatchContext context;

    private static class TestDispatchContext implements DispatchContext {
        private int lastIntConstant;
        private long lastLongConstant;
        private float lastFloatConstant;
        private double lastDoubleConstant;
        private String lastStringConstant;
        private ObjectInstance lastClassConstant;
        private int branchTarget;

        @Override
        public int resolveIntConstant(int index) {
            return lastIntConstant;
        }

        @Override
        public long resolveLongConstant(int index) {
            return lastLongConstant;
        }

        @Override
        public float resolveFloatConstant(int index) {
            return lastFloatConstant;
        }

        @Override
        public double resolveDoubleConstant(int index) {
            return lastDoubleConstant;
        }

        @Override
        public String resolveStringConstant(int index) {
            return lastStringConstant;
        }

        @Override
        public ObjectInstance resolveClassConstant(int index) {
            return lastClassConstant;
        }

        @Override
        public ArrayInstance getArray(ObjectInstance ref) {
            return (ArrayInstance) ref;
        }

        @Override
        public void checkArrayBounds(ArrayInstance array, int index) {
            if (index < 0 || index >= array.getLength()) {
                throw new ArrayIndexOutOfBoundsException("Index: " + index + ", Length: " + array.getLength());
            }
        }

        @Override
        public void checkNullReference(ObjectInstance ref, String operation) {
            if (ref == null) {
                throw new NullPointerException(operation);
            }
        }

        @Override
        public FieldInfo resolveField(int cpIndex) {
            return null;
        }

        @Override
        public MethodInfo resolveMethod(int cpIndex) {
            return null;
        }

        @Override
        public boolean isInstanceOf(ObjectInstance obj, String className) {
            return obj.isInstanceOf(className);
        }

        @Override
        public void checkCast(ObjectInstance obj, String className) {
        }

        @Override
        public MethodInfo getPendingInvoke() {
            return null;
        }

        @Override
        public FieldInfo getPendingFieldAccess() {
            return null;
        }

        @Override
        public String getPendingNewClass() {
            return null;
        }

        @Override
        public int[] getPendingArrayDimensions() {
            return null;
        }

        @Override
        public void setPendingInvoke(MethodInfo methodInfo) {
        }

        @Override
        public void setPendingFieldAccess(FieldInfo fieldInfo) {
        }

        @Override
        public void setPendingNewClass(String className) {
        }

        @Override
        public void setPendingArrayDimensions(int[] dimensions) {
        }

        @Override
        public void setBranchTarget(int target) {
            this.branchTarget = target;
        }

        @Override
        public int getBranchTarget() {
            return branchTarget;
        }

        @Override
        public void setPendingInvokeDynamic(InvokeDynamicInfo info) {
        }

        @Override
        public InvokeDynamicInfo getPendingInvokeDynamic() {
            return null;
        }

        @Override
        public void setPendingMethodHandle(MethodHandleInfo info) {
        }

        @Override
        public MethodHandleInfo getPendingMethodHandle() {
            return null;
        }

        @Override
        public void setPendingMethodType(MethodTypeInfo info) {
        }

        @Override
        public MethodTypeInfo getPendingMethodType() {
            return null;
        }

        @Override
        public void setPendingConstantDynamic(ConstantDynamicInfo info) {
        }

        @Override
        public ConstantDynamicInfo getPendingConstantDynamic() {
            return null;
        }
    }

    private static class SimpleInstruction extends Instruction {
        public SimpleInstruction(int opcode, int offset, int length) {
            super(opcode, offset, length);
        }

        @Override
        public void accept(com.tonic.analysis.visitor.AbstractBytecodeVisitor visitor) {}

        @Override
        public void write(java.io.DataOutputStream dos) {}

        @Override
        public int getStackChange() {
            return 0;
        }

        @Override
        public int getLocalChange() {
            return 0;
        }
    }

    private static class SimpleStackFrame {
        private Instruction currentInstruction;
        private ConcreteStack stack;
        private ConcreteLocals locals;
        private int pc;

        public SimpleStackFrame(ConcreteStack stack, ConcreteLocals locals) {
            this.stack = stack;
            this.locals = locals;
            this.pc = 0;
        }

        public void setCurrentInstruction(Instruction instr) {
            this.currentInstruction = instr;
        }

        public Instruction getCurrentInstruction() {
            return currentInstruction;
        }

        public ConcreteStack getStack() {
            return stack;
        }

        public ConcreteLocals getLocals() {
            return locals;
        }

        public int getPC() {
            return pc;
        }

        public void advancePC(int delta) {
            pc += delta;
        }
    }

    @BeforeEach
    void setUp() {
        dispatcher = new OpcodeDispatcher();
        stack = new ConcreteStack(20);
        locals = new ConcreteLocals(10);
        context = new TestDispatchContext();
    }

    private OpcodeDispatcher.DispatchResult dispatchSimple(Instruction instr) {
        SimpleStackFrame frame = new SimpleStackFrame(stack, locals);
        frame.setCurrentInstruction(instr);
        return dispatchByOpcode(instr.getOpcode(), instr, frame);
    }

    private OpcodeDispatcher.DispatchResult dispatchByOpcode(int opcode, Instruction instr, SimpleStackFrame frame) {
        switch (opcode) {
            case 0x85:
                stack.pushLong((long) stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x86:
                stack.pushFloat((float) stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x87:
                stack.pushDouble((double) stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x88:
                stack.pushInt((int) stack.popLong());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x89:
                stack.pushFloat((float) stack.popLong());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x8A:
                stack.pushDouble((double) stack.popLong());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x8B:
                stack.pushInt((int) stack.popFloat());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x8C:
                stack.pushLong((long) stack.popFloat());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x8D:
                stack.pushDouble((double) stack.popFloat());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x8E:
                stack.pushInt((int) stack.popDouble());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x8F:
                stack.pushLong((long) stack.popDouble());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x90:
                stack.pushFloat((float) stack.popDouble());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x91:
                stack.pushInt((byte) stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x92:
                stack.pushInt((char) stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x93:
                stack.pushInt((short) stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            default:
                throw new UnsupportedOperationException("Opcode not handled: 0x" + Integer.toHexString(opcode));
        }
    }

    @Test
    void testI2L_PositiveValue() {
        stack.pushInt(42);
        SimpleInstruction instr = new SimpleInstruction(0x85, 0, 1);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals(42L, stack.popLong());
    }

    @Test
    void testI2L_Zero() {
        stack.pushInt(0);
        SimpleInstruction instr = new SimpleInstruction(0x85, 0, 1);
        dispatchSimple(instr);
        assertEquals(0L, stack.popLong());
    }

    @Test
    void testI2L_NegativeValue() {
        stack.pushInt(-100);
        SimpleInstruction instr = new SimpleInstruction(0x85, 0, 1);
        dispatchSimple(instr);
        assertEquals(-100L, stack.popLong());
    }

    @Test
    void testI2L_MaxValue() {
        stack.pushInt(Integer.MAX_VALUE);
        SimpleInstruction instr = new SimpleInstruction(0x85, 0, 1);
        dispatchSimple(instr);
        assertEquals((long) Integer.MAX_VALUE, stack.popLong());
    }

    @Test
    void testI2F_PositiveValue() {
        stack.pushInt(42);
        SimpleInstruction instr = new SimpleInstruction(0x86, 0, 1);
        dispatchSimple(instr);
        assertEquals(42.0f, stack.popFloat());
    }

    @Test
    void testI2F_Zero() {
        stack.pushInt(0);
        SimpleInstruction instr = new SimpleInstruction(0x86, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0f, stack.popFloat());
    }

    @Test
    void testI2F_NegativeValue() {
        stack.pushInt(-123);
        SimpleInstruction instr = new SimpleInstruction(0x86, 0, 1);
        dispatchSimple(instr);
        assertEquals(-123.0f, stack.popFloat());
    }

    @Test
    void testI2D_PositiveValue() {
        stack.pushInt(100);
        SimpleInstruction instr = new SimpleInstruction(0x87, 0, 1);
        dispatchSimple(instr);
        assertEquals(100.0, stack.popDouble());
    }

    @Test
    void testI2D_Zero() {
        stack.pushInt(0);
        SimpleInstruction instr = new SimpleInstruction(0x87, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0, stack.popDouble());
    }

    @Test
    void testI2D_NegativeValue() {
        stack.pushInt(-500);
        SimpleInstruction instr = new SimpleInstruction(0x87, 0, 1);
        dispatchSimple(instr);
        assertEquals(-500.0, stack.popDouble());
    }

    @Test
    void testL2I_PositiveValue() {
        stack.pushLong(12345L);
        SimpleInstruction instr = new SimpleInstruction(0x88, 0, 1);
        dispatchSimple(instr);
        assertEquals(12345, stack.popInt());
    }

    @Test
    void testL2I_Zero() {
        stack.pushLong(0L);
        SimpleInstruction instr = new SimpleInstruction(0x88, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testL2I_NegativeValue() {
        stack.pushLong(-999L);
        SimpleInstruction instr = new SimpleInstruction(0x88, 0, 1);
        dispatchSimple(instr);
        assertEquals(-999, stack.popInt());
    }

    @Test
    void testL2I_TruncationOverflow() {
        stack.pushLong(0x1FFFFFFFFL);
        SimpleInstruction instr = new SimpleInstruction(0x88, 0, 1);
        dispatchSimple(instr);
        assertEquals((int) 0x1FFFFFFFFL, stack.popInt());
    }

    @Test
    void testL2F_PositiveValue() {
        stack.pushLong(50000L);
        SimpleInstruction instr = new SimpleInstruction(0x89, 0, 1);
        dispatchSimple(instr);
        assertEquals(50000.0f, stack.popFloat());
    }

    @Test
    void testL2F_Zero() {
        stack.pushLong(0L);
        SimpleInstruction instr = new SimpleInstruction(0x89, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0f, stack.popFloat());
    }

    @Test
    void testL2F_NegativeValue() {
        stack.pushLong(-77777L);
        SimpleInstruction instr = new SimpleInstruction(0x89, 0, 1);
        dispatchSimple(instr);
        assertEquals(-77777.0f, stack.popFloat());
    }

    @Test
    void testL2D_PositiveValue() {
        stack.pushLong(999999L);
        SimpleInstruction instr = new SimpleInstruction(0x8A, 0, 1);
        dispatchSimple(instr);
        assertEquals(999999.0, stack.popDouble());
    }

    @Test
    void testL2D_Zero() {
        stack.pushLong(0L);
        SimpleInstruction instr = new SimpleInstruction(0x8A, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0, stack.popDouble());
    }

    @Test
    void testL2D_NegativeValue() {
        stack.pushLong(-123456789L);
        SimpleInstruction instr = new SimpleInstruction(0x8A, 0, 1);
        dispatchSimple(instr);
        assertEquals(-123456789.0, stack.popDouble());
    }

    @Test
    void testF2I_PositiveValue() {
        stack.pushFloat(42.7f);
        SimpleInstruction instr = new SimpleInstruction(0x8B, 0, 1);
        dispatchSimple(instr);
        assertEquals(42, stack.popInt());
    }

    @Test
    void testF2I_Zero() {
        stack.pushFloat(0.0f);
        SimpleInstruction instr = new SimpleInstruction(0x8B, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testF2I_NegativeValue() {
        stack.pushFloat(-99.9f);
        SimpleInstruction instr = new SimpleInstruction(0x8B, 0, 1);
        dispatchSimple(instr);
        assertEquals(-99, stack.popInt());
    }

    @Test
    void testF2I_NaN() {
        stack.pushFloat(Float.NaN);
        SimpleInstruction instr = new SimpleInstruction(0x8B, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testF2I_PositiveInfinity() {
        stack.pushFloat(Float.POSITIVE_INFINITY);
        SimpleInstruction instr = new SimpleInstruction(0x8B, 0, 1);
        dispatchSimple(instr);
        assertEquals(Integer.MAX_VALUE, stack.popInt());
    }

    @Test
    void testF2I_NegativeInfinity() {
        stack.pushFloat(Float.NEGATIVE_INFINITY);
        SimpleInstruction instr = new SimpleInstruction(0x8B, 0, 1);
        dispatchSimple(instr);
        assertEquals(Integer.MIN_VALUE, stack.popInt());
    }

    @Test
    void testF2L_PositiveValue() {
        stack.pushFloat(1234.5f);
        SimpleInstruction instr = new SimpleInstruction(0x8C, 0, 1);
        dispatchSimple(instr);
        assertEquals(1234L, stack.popLong());
    }

    @Test
    void testF2L_Zero() {
        stack.pushFloat(0.0f);
        SimpleInstruction instr = new SimpleInstruction(0x8C, 0, 1);
        dispatchSimple(instr);
        assertEquals(0L, stack.popLong());
    }

    @Test
    void testF2L_NegativeValue() {
        stack.pushFloat(-5678.9f);
        SimpleInstruction instr = new SimpleInstruction(0x8C, 0, 1);
        dispatchSimple(instr);
        assertEquals(-5678L, stack.popLong());
    }

    @Test
    void testF2L_NaN() {
        stack.pushFloat(Float.NaN);
        SimpleInstruction instr = new SimpleInstruction(0x8C, 0, 1);
        dispatchSimple(instr);
        assertEquals(0L, stack.popLong());
    }

    @Test
    void testF2D_PositiveValue() {
        stack.pushFloat(3.14f);
        SimpleInstruction instr = new SimpleInstruction(0x8D, 0, 1);
        dispatchSimple(instr);
        double result = stack.popDouble();
        assertTrue(Math.abs(result - 3.14) < 0.01);
    }

    @Test
    void testF2D_Zero() {
        stack.pushFloat(0.0f);
        SimpleInstruction instr = new SimpleInstruction(0x8D, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0, stack.popDouble());
    }

    @Test
    void testF2D_NegativeValue() {
        stack.pushFloat(-2.718f);
        SimpleInstruction instr = new SimpleInstruction(0x8D, 0, 1);
        dispatchSimple(instr);
        double result = stack.popDouble();
        assertTrue(Math.abs(result - (-2.718)) < 0.001);
    }

    @Test
    void testD2I_PositiveValue() {
        stack.pushDouble(999.999);
        SimpleInstruction instr = new SimpleInstruction(0x8E, 0, 1);
        dispatchSimple(instr);
        assertEquals(999, stack.popInt());
    }

    @Test
    void testD2I_Zero() {
        stack.pushDouble(0.0);
        SimpleInstruction instr = new SimpleInstruction(0x8E, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testD2I_NegativeValue() {
        stack.pushDouble(-777.777);
        SimpleInstruction instr = new SimpleInstruction(0x8E, 0, 1);
        dispatchSimple(instr);
        assertEquals(-777, stack.popInt());
    }

    @Test
    void testD2I_NaN() {
        stack.pushDouble(Double.NaN);
        SimpleInstruction instr = new SimpleInstruction(0x8E, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testD2L_PositiveValue() {
        stack.pushDouble(123456.789);
        SimpleInstruction instr = new SimpleInstruction(0x8F, 0, 1);
        dispatchSimple(instr);
        assertEquals(123456L, stack.popLong());
    }

    @Test
    void testD2L_Zero() {
        stack.pushDouble(0.0);
        SimpleInstruction instr = new SimpleInstruction(0x8F, 0, 1);
        dispatchSimple(instr);
        assertEquals(0L, stack.popLong());
    }

    @Test
    void testD2L_NegativeValue() {
        stack.pushDouble(-987654.321);
        SimpleInstruction instr = new SimpleInstruction(0x8F, 0, 1);
        dispatchSimple(instr);
        assertEquals(-987654L, stack.popLong());
    }

    @Test
    void testD2L_NaN() {
        stack.pushDouble(Double.NaN);
        SimpleInstruction instr = new SimpleInstruction(0x8F, 0, 1);
        dispatchSimple(instr);
        assertEquals(0L, stack.popLong());
    }

    @Test
    void testD2F_PositiveValue() {
        stack.pushDouble(1.23456789);
        SimpleInstruction instr = new SimpleInstruction(0x90, 0, 1);
        dispatchSimple(instr);
        float result = stack.popFloat();
        assertTrue(Math.abs(result - 1.23456789f) < 0.0001f);
    }

    @Test
    void testD2F_Zero() {
        stack.pushDouble(0.0);
        SimpleInstruction instr = new SimpleInstruction(0x90, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0f, stack.popFloat());
    }

    @Test
    void testD2F_NegativeValue() {
        stack.pushDouble(-9.87654321);
        SimpleInstruction instr = new SimpleInstruction(0x90, 0, 1);
        dispatchSimple(instr);
        float result = stack.popFloat();
        assertTrue(Math.abs(result - (-9.87654321f)) < 0.0001f);
    }

    @Test
    void testD2F_PrecisionLoss() {
        stack.pushDouble(1.23456789123456789);
        SimpleInstruction instr = new SimpleInstruction(0x90, 0, 1);
        dispatchSimple(instr);
        float result = stack.popFloat();
        assertNotEquals(1.23456789123456789, (double) result);
    }

    @Test
    void testI2B_PositiveValue() {
        stack.pushInt(100);
        SimpleInstruction instr = new SimpleInstruction(0x91, 0, 1);
        dispatchSimple(instr);
        assertEquals(100, stack.popInt());
    }

    @Test
    void testI2B_Zero() {
        stack.pushInt(0);
        SimpleInstruction instr = new SimpleInstruction(0x91, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testI2B_NegativeValue() {
        stack.pushInt(-50);
        SimpleInstruction instr = new SimpleInstruction(0x91, 0, 1);
        dispatchSimple(instr);
        assertEquals(-50, stack.popInt());
    }

    @Test
    void testI2B_Truncation() {
        stack.pushInt(300);
        SimpleInstruction instr = new SimpleInstruction(0x91, 0, 1);
        dispatchSimple(instr);
        assertEquals((byte) 300, stack.popInt());
    }

    @Test
    void testI2C_PositiveValue() {
        stack.pushInt(65);
        SimpleInstruction instr = new SimpleInstruction(0x92, 0, 1);
        dispatchSimple(instr);
        assertEquals(65, stack.popInt());
    }

    @Test
    void testI2C_Zero() {
        stack.pushInt(0);
        SimpleInstruction instr = new SimpleInstruction(0x92, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testI2C_NegativeToUnsigned() {
        stack.pushInt(-1);
        SimpleInstruction instr = new SimpleInstruction(0x92, 0, 1);
        dispatchSimple(instr);
        assertEquals((char) -1, stack.popInt());
    }

    @Test
    void testI2C_Truncation() {
        stack.pushInt(100000);
        SimpleInstruction instr = new SimpleInstruction(0x92, 0, 1);
        dispatchSimple(instr);
        assertEquals((char) 100000, stack.popInt());
    }

    @Test
    void testI2S_PositiveValue() {
        stack.pushInt(1000);
        SimpleInstruction instr = new SimpleInstruction(0x93, 0, 1);
        dispatchSimple(instr);
        assertEquals(1000, stack.popInt());
    }

    @Test
    void testI2S_Zero() {
        stack.pushInt(0);
        SimpleInstruction instr = new SimpleInstruction(0x93, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testI2S_NegativeValue() {
        stack.pushInt(-5000);
        SimpleInstruction instr = new SimpleInstruction(0x93, 0, 1);
        dispatchSimple(instr);
        assertEquals(-5000, stack.popInt());
    }

    @Test
    void testI2S_Truncation() {
        stack.pushInt(50000);
        SimpleInstruction instr = new SimpleInstruction(0x93, 0, 1);
        dispatchSimple(instr);
        assertEquals((short) 50000, stack.popInt());
    }
}
