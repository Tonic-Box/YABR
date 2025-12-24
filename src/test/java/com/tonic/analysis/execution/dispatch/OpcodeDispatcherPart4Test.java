package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherPart4Test {

    private OpcodeDispatcher dispatcher;
    private ConcreteStack stack;
    private ConcreteLocals locals;
    private TestDispatchContext context;

    private static class TestDispatchContext implements DispatchContext {
        private String pendingNewClass;
        private int[] pendingArrayDimensions;
        private String pendingCheckCastClass;
        private String pendingInstanceOfClass;

        @Override
        public int resolveIntConstant(int index) {
            return 0;
        }

        @Override
        public long resolveLongConstant(int index) {
            return 0L;
        }

        @Override
        public float resolveFloatConstant(int index) {
            return 0.0f;
        }

        @Override
        public double resolveDoubleConstant(int index) {
            return 0.0;
        }

        @Override
        public String resolveStringConstant(int index) {
            return null;
        }

        @Override
        public ObjectInstance resolveClassConstant(int index) {
            return null;
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
            return pendingNewClass;
        }

        @Override
        public int[] getPendingArrayDimensions() {
            return pendingArrayDimensions;
        }

        @Override
        public void setPendingInvoke(MethodInfo methodInfo) {
        }

        @Override
        public void setPendingFieldAccess(FieldInfo fieldInfo) {
        }

        @Override
        public void setPendingNewClass(String className) {
            this.pendingNewClass = className;
        }

        @Override
        public void setPendingArrayDimensions(int[] dimensions) {
            this.pendingArrayDimensions = dimensions;
        }

        @Override
        public void setBranchTarget(int target) {
        }

        @Override
        public int getBranchTarget() {
            return 0;
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
        stack = new ConcreteStack(100);
        locals = new ConcreteLocals(10);
        context = new TestDispatchContext();
    }

    private OpcodeDispatcher.DispatchResult dispatchSimple(Instruction instr) {
        SimpleStackFrame frame = new SimpleStackFrame(stack, locals);
        frame.setCurrentInstruction(instr);

        int opcode = instr.getOpcode();
        return dispatchByOpcode(opcode, instr, frame);
    }

    private OpcodeDispatcher.DispatchResult dispatchByOpcode(int opcode, Instruction instr, SimpleStackFrame frame) {
        switch (opcode) {
            case 0x33: {
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "baload");
                context.checkArrayBounds(array, index);
                stack.pushInt(array.getByte(index));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x34: {
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "caload");
                context.checkArrayBounds(array, index);
                stack.pushInt(array.getChar(index));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x35: {
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "saload");
                context.checkArrayBounds(array, index);
                stack.pushInt(array.getShort(index));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x54: {
                int value = stack.popInt();
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "bastore");
                context.checkArrayBounds(array, index);
                array.setByte(index, (byte) value);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x55: {
                int value = stack.popInt();
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "castore");
                context.checkArrayBounds(array, index);
                array.setChar(index, (char) value);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x56: {
                int value = stack.popInt();
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "sastore");
                context.checkArrayBounds(array, index);
                array.setShort(index, (short) value);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xBB:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.NEW_OBJECT;

            case 0xBC:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.NEW_ARRAY;

            case 0xBD:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.NEW_ARRAY;

            case 0xBE: {
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "arraylength");
                stack.pushInt(array.getLength());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xC0:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CHECKCAST;

            case 0xC1:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.INSTANCEOF;

            case 0xC2: {
                ObjectInstance ref = stack.popReference();
                context.checkNullReference(ref, "monitorenter");
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xC3: {
                ObjectInstance ref = stack.popReference();
                context.checkNullReference(ref, "monitorexit");
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xC5:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.NEW_ARRAY;

            default:
                throw new UnsupportedOperationException("Opcode not handled: 0x" + Integer.toHexString(opcode));
        }
    }

    @Test
    void testNew() {
        SimpleInstruction instr = new SimpleInstruction(0xBB, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_OBJECT, result);
    }

    @Test
    void testNewArrayBoolean() {
        stack.pushInt(5);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 4, 5);
        assertEquals(NewArrayInstruction.ArrayType.T_BOOLEAN, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayChar() {
        stack.pushInt(10);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 5, 10);
        assertEquals(NewArrayInstruction.ArrayType.T_CHAR, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayFloat() {
        stack.pushInt(8);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 6, 8);
        assertEquals(NewArrayInstruction.ArrayType.T_FLOAT, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayDouble() {
        stack.pushInt(12);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 7, 12);
        assertEquals(NewArrayInstruction.ArrayType.T_DOUBLE, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayByte() {
        stack.pushInt(20);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 8, 20);
        assertEquals(NewArrayInstruction.ArrayType.T_BYTE, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayShort() {
        stack.pushInt(15);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 9, 15);
        assertEquals(NewArrayInstruction.ArrayType.T_SHORT, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayInt() {
        stack.pushInt(25);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 10, 25);
        assertEquals(NewArrayInstruction.ArrayType.T_INT, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testNewArrayLong() {
        stack.pushInt(30);
        NewArrayInstruction instr = new NewArrayInstruction(0xBC, 0, 11, 30);
        assertEquals(NewArrayInstruction.ArrayType.T_LONG, instr.getArrayType());
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testANewArray() {
        stack.pushInt(7);
        SimpleInstruction instr = new SimpleInstruction(0xBD, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testMultiANewArray() {
        stack.pushInt(3);
        stack.pushInt(4);
        stack.pushInt(5);
        SimpleInstruction instr = new SimpleInstruction(0xC5, 0, 4);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.NEW_ARRAY, result);
    }

    @Test
    void testArrayLength() {
        ArrayInstance array = new ArrayInstance(1, "I", 42);
        stack.pushReference(array);
        ArrayLengthInstruction instr = new ArrayLengthInstruction(0xBE, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals(42, stack.popInt());
    }

    @Test
    void testArrayLengthNull() {
        stack.pushNull();
        ArrayLengthInstruction instr = new ArrayLengthInstruction(0xBE, 0);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testCheckCast() {
        SimpleInstruction instr = new SimpleInstruction(0xC0, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CHECKCAST, result);
    }

    @Test
    void testInstanceOf() {
        SimpleInstruction instr = new SimpleInstruction(0xC1, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.INSTANCEOF, result);
    }

    @Test
    void testBALoad() {
        ArrayInstance array = new ArrayInstance(1, "B", 5);
        array.setByte(2, (byte) 99);
        stack.pushReference(array);
        stack.pushInt(2);
        BALOADInstruction instr = new BALOADInstruction(0x33, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals(99, stack.popInt());
    }

    @Test
    void testBALoadNull() {
        stack.pushNull();
        stack.pushInt(0);
        BALOADInstruction instr = new BALOADInstruction(0x33, 0);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testBALoadOutOfBounds() {
        ArrayInstance array = new ArrayInstance(1, "B", 3);
        stack.pushReference(array);
        stack.pushInt(5);
        BALOADInstruction instr = new BALOADInstruction(0x33, 0);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testBAStore() {
        ArrayInstance array = new ArrayInstance(1, "B", 5);
        stack.pushReference(array);
        stack.pushInt(3);
        stack.pushInt(77);
        SimpleInstruction instr = new SimpleInstruction(0x54, 0, 1);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals((byte) 77, array.getByte(3));
    }

    @Test
    void testBAStoreNull() {
        stack.pushNull();
        stack.pushInt(0);
        stack.pushInt(100);
        SimpleInstruction instr = new SimpleInstruction(0x54, 0, 1);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testCALoad() {
        ArrayInstance array = new ArrayInstance(1, "C", 5);
        array.setChar(1, 'A');
        stack.pushReference(array);
        stack.pushInt(1);
        CALoadInstruction instr = new CALoadInstruction(0x34, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals('A', stack.popInt());
    }

    @Test
    void testCALoadNull() {
        stack.pushNull();
        stack.pushInt(0);
        CALoadInstruction instr = new CALoadInstruction(0x34, 0);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testCAStore() {
        ArrayInstance array = new ArrayInstance(1, "C", 5);
        stack.pushReference(array);
        stack.pushInt(2);
        stack.pushInt('Z');
        SimpleInstruction instr = new SimpleInstruction(0x55, 0, 1);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals('Z', array.getChar(2));
    }

    @Test
    void testSALoad() {
        ArrayInstance array = new ArrayInstance(1, "S", 5);
        array.setShort(4, (short) 32000);
        stack.pushReference(array);
        stack.pushInt(4);
        SALoadInstruction instr = new SALoadInstruction(0x35, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals(32000, stack.popInt());
    }

    @Test
    void testSALoadNull() {
        stack.pushNull();
        stack.pushInt(0);
        SALoadInstruction instr = new SALoadInstruction(0x35, 0);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testSAStore() {
        ArrayInstance array = new ArrayInstance(1, "S", 5);
        stack.pushReference(array);
        stack.pushInt(1);
        stack.pushInt(12345);
        SimpleInstruction instr = new SimpleInstruction(0x56, 0, 1);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals((short) 12345, array.getShort(1));
    }

    @Test
    void testMonitorEnter() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/Object");
        stack.pushReference(obj);
        MonitorEnterInstruction instr = new MonitorEnterInstruction(0xC2, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals(0, stack.depth());
    }

    @Test
    void testMonitorEnterNull() {
        stack.pushNull();
        MonitorEnterInstruction instr = new MonitorEnterInstruction(0xC2, 0);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }

    @Test
    void testMonitorExit() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/Object");
        stack.pushReference(obj);
        SimpleInstruction instr = new SimpleInstruction(0xC3, 0, 1);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertEquals(0, stack.depth());
    }

    @Test
    void testMonitorExitNull() {
        stack.pushNull();
        SimpleInstruction instr = new SimpleInstruction(0xC3, 0, 1);
        assertThrows(NullPointerException.class, () -> dispatchSimple(instr));
    }
}
