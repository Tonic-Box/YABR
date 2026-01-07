package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.*;
import com.tonic.testutil.StubDispatchContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherPart1Test {

    private OpcodeDispatcher dispatcher;
    private ConcreteStack stack;
    private ConcreteLocals locals;
    private StubDispatchContext context;

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
        context = new StubDispatchContext();
    }

    private OpcodeDispatcher.DispatchResult dispatchSimple(Instruction instr) {
        SimpleStackFrame frame = new SimpleStackFrame(stack, locals);
        frame.setCurrentInstruction(instr);

        ConcreteStack stackCopy = stack;
        ConcreteLocals localsCopy = locals;

        int pcBefore = frame.getPC();
        OpcodeDispatcher.DispatchResult result = null;

        try {
            java.lang.reflect.Method method = OpcodeDispatcher.class.getDeclaredMethod(
                "dispatch",
                com.tonic.analysis.execution.frame.StackFrame.class,
                DispatchContext.class
            );
            throw new UnsupportedOperationException("Cannot test without proper frame");
        } catch (Exception e) {
            int opcode = instr.getOpcode();
            result = dispatchByOpcode(opcode, instr, frame);
        }

        return result;
    }

    private OpcodeDispatcher.DispatchResult dispatchByOpcode(int opcode, Instruction instr, SimpleStackFrame frame) {
        switch (opcode) {
            case 0x00:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x01:
                stack.pushNull();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x02: case 0x03: case 0x04: case 0x05:
            case 0x06: case 0x07: case 0x08:
                stack.pushInt(opcode - 0x03);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x09: case 0x0A:
                stack.pushLong(opcode - 0x09);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x0B: case 0x0C: case 0x0D:
                stack.pushFloat(opcode - 0x0B);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x0E: case 0x0F:
                stack.pushDouble(opcode - 0x0E);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x10:
                stack.pushInt(((BipushInstruction) instr).getValue());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x11:
                stack.pushInt(((SipushInstruction) instr).getValue());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x15:
                stack.pushInt(locals.getInt(((ILoadInstruction) instr).getVarIndex()));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x16:
                stack.pushLong(locals.getLong(((LLoadInstruction) instr).getVarIndex()));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x17:
                stack.pushFloat(locals.getFloat(((FLoadInstruction) instr).getVarIndex()));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x18:
                stack.pushDouble(locals.getDouble(((DLoadInstruction) instr).getVarIndex()));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x19:
                ObjectInstance ref = locals.getReference(((ALoadInstruction) instr).getVarIndex());
                if (ref == null) stack.pushNull();
                else stack.pushReference(ref);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x1A: case 0x1B: case 0x1C: case 0x1D:
                stack.pushInt(locals.getInt(opcode - 0x1A));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x2E: {
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "iaload");
                context.checkArrayBounds(array, index);
                stack.pushInt(array.getInt(index));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x32: {
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "aaload");
                context.checkArrayBounds(array, index);
                Object value = array.get(index);
                if (value == null) stack.pushNull();
                else stack.pushReference((ObjectInstance) value);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x36:
                locals.setInt(((IStoreInstruction) instr).getVarIndex(), stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x37:
                locals.setLong(((LStoreInstruction) instr).getVarIndex(), stack.popLong());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x38:
                locals.setFloat(((FStoreInstruction) instr).getVarIndex(), stack.popFloat());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x39:
                locals.setDouble(((DStoreInstruction) instr).getVarIndex(), stack.popDouble());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x3A:
                ObjectInstance aref = stack.popReference();
                if (aref == null) locals.setNull(((AStoreInstruction) instr).getVarIndex());
                else locals.setReference(((AStoreInstruction) instr).getVarIndex(), aref);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x3B: case 0x3C: case 0x3D: case 0x3E:
                locals.setInt(opcode - 0x3B, stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x4F: {
                int value = stack.popInt();
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "iastore");
                context.checkArrayBounds(array, index);
                array.setInt(index, value);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x53: {
                ObjectInstance value = stack.popReference();
                int index = stack.popInt();
                ArrayInstance array = (ArrayInstance) stack.popReference();
                context.checkNullReference(array, "aastore");
                context.checkArrayBounds(array, index);
                array.set(index, value);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x57:
                stack.pop();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x58:
                if (stack.peek().isWide()) stack.pop();
                else { stack.pop(); stack.pop(); }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x59:
                stack.dup();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x5F:
                stack.swap();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x60: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 + v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x61: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 + v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x64: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 - v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x68: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 * v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x6C: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 / v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x70: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 % v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x74:
                stack.pushInt(-stack.popInt());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x75:
                stack.pushLong(-stack.popLong());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x76:
                stack.pushFloat(-stack.popFloat());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x77:
                stack.pushDouble(-stack.popDouble());
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;

            case 0x78: {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value << shiftAmount);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x79: {
                int shiftAmount = stack.popInt();
                long value = stack.popLong();
                stack.pushLong(value << shiftAmount);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x7A: {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value >> shiftAmount);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x7C: {
                int shiftAmount = stack.popInt();
                int value = stack.popInt();
                stack.pushInt(value >>> shiftAmount);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x7E: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                stack.pushInt(v1 & v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x7F: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushLong(v1 & v2);
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            default:
                throw new UnsupportedOperationException("Opcode not handled: 0x" + Integer.toHexString(opcode));
        }
    }

    @Test
    void testNop() {
        NopInstruction instr = new NopInstruction(0x00, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testAConstNull() {
        AConstNullInstruction instr = new AConstNullInstruction(0x01, 0);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
        assertTrue(stack.peek().isNull());
    }

    @Test
    void testIConstM1() {
        SimpleInstruction instr = new SimpleInstruction(0x02, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testIConst0() {
        SimpleInstruction instr = new SimpleInstruction(0x03, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testIConst5() {
        SimpleInstruction instr = new SimpleInstruction(0x08, 0, 1);
        dispatchSimple(instr);
        assertEquals(5, stack.popInt());
    }

    @Test
    void testLConst0() {
        SimpleInstruction instr = new SimpleInstruction(0x09, 0, 1);
        dispatchSimple(instr);
        assertEquals(0L, stack.popLong());
    }

    @Test
    void testFConst0() {
        SimpleInstruction instr = new SimpleInstruction(0x0B, 0, 1);
        dispatchSimple(instr);
        assertEquals(0.0f, stack.popFloat());
    }

    @Test
    void testDConst1() {
        SimpleInstruction instr = new SimpleInstruction(0x0F, 0, 1);
        dispatchSimple(instr);
        assertEquals(1.0, stack.popDouble());
    }

    @Test
    void testBipush() {
        BipushInstruction instr = new BipushInstruction(0x10, 0, 42);
        dispatchSimple(instr);
        assertEquals(42, stack.popInt());
    }

    @Test
    void testSipush() {
        SipushInstruction instr = new SipushInstruction(0x11, 0, 1000);
        dispatchSimple(instr);
        assertEquals(1000, stack.popInt());
    }

    @Test
    void testILoadAndIStore() {
        locals.setInt(5, 123);
        ILoadInstruction loadInstr = new ILoadInstruction(0x15, 0, 5);
        dispatchSimple(loadInstr);
        assertEquals(123, stack.popInt());

        stack.pushInt(456);
        IStoreInstruction storeInstr = new IStoreInstruction(0x36, 0, 3);
        dispatchSimple(storeInstr);
        assertEquals(456, locals.getInt(3));
    }

    @Test
    void testILoad0Through3() {
        for (int i = 0; i < 4; i++) {
            locals.setInt(i, 100 + i);
            SimpleInstruction instr = new SimpleInstruction(0x1A + i, 0, 1);
            dispatchSimple(instr);
            assertEquals(100 + i, stack.popInt());
        }
    }

    @Test
    void testPop() {
        stack.pushInt(100);
        stack.pushInt(200);
        SimpleInstruction instr = new SimpleInstruction(0x57, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.depth());
    }

    @Test
    void testDup() {
        stack.pushInt(42);
        SimpleInstruction instr = new SimpleInstruction(0x59, 0, 1);
        dispatchSimple(instr);
        assertEquals(2, stack.depth());
        assertEquals(42, stack.popInt());
        assertEquals(42, stack.popInt());
    }

    @Test
    void testSwap() {
        stack.pushInt(10);
        stack.pushInt(20);
        SimpleInstruction instr = new SimpleInstruction(0x5F, 0, 1);
        dispatchSimple(instr);
        assertEquals(10, stack.popInt());
        assertEquals(20, stack.popInt());
    }

    @Test
    void testIAdd() {
        stack.pushInt(10);
        stack.pushInt(20);
        ArithmeticInstruction instr = new ArithmeticInstruction(0x60, 0);
        dispatchSimple(instr);
        assertEquals(30, stack.popInt());
    }

    @Test
    void testLAdd() {
        stack.pushLong(100L);
        stack.pushLong(200L);
        ArithmeticInstruction instr = new ArithmeticInstruction(0x61, 0);
        dispatchSimple(instr);
        assertEquals(300L, stack.popLong());
    }

    @Test
    void testISub() {
        stack.pushInt(50);
        stack.pushInt(20);
        ArithmeticInstruction instr = new ArithmeticInstruction(0x64, 0);
        dispatchSimple(instr);
        assertEquals(30, stack.popInt());
    }

    @Test
    void testIMul() {
        stack.pushInt(7);
        stack.pushInt(6);
        ArithmeticInstruction instr = new ArithmeticInstruction(0x68, 0);
        dispatchSimple(instr);
        assertEquals(42, stack.popInt());
    }

    @Test
    void testIDiv() {
        stack.pushInt(100);
        stack.pushInt(4);
        ArithmeticInstruction instr = new ArithmeticInstruction(0x6C, 0);
        dispatchSimple(instr);
        assertEquals(25, stack.popInt());
    }

    @Test
    void testIRem() {
        stack.pushInt(17);
        stack.pushInt(5);
        ArithmeticInstruction instr = new ArithmeticInstruction(0x70, 0);
        dispatchSimple(instr);
        assertEquals(2, stack.popInt());
    }

    @Test
    void testINeg() {
        stack.pushInt(42);
        SimpleInstruction instr = new SimpleInstruction(0x74, 0, 1);
        dispatchSimple(instr);
        assertEquals(-42, stack.popInt());
    }

    @Test
    void testLNeg() {
        stack.pushLong(1000L);
        SimpleInstruction instr = new SimpleInstruction(0x75, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1000L, stack.popLong());
    }

    @Test
    void testIShl() {
        stack.pushInt(5);
        stack.pushInt(2);
        ArithmeticShiftInstruction instr = new ArithmeticShiftInstruction(0x78, 0);
        dispatchSimple(instr);
        assertEquals(20, stack.popInt());
    }

    @Test
    void testIShr() {
        stack.pushInt(-16);
        stack.pushInt(2);
        ArithmeticShiftInstruction instr = new ArithmeticShiftInstruction(0x7A, 0);
        dispatchSimple(instr);
        assertEquals(-4, stack.popInt());
    }

    @Test
    void testIUshr() {
        stack.pushInt(-16);
        stack.pushInt(2);
        ArithmeticShiftInstruction instr = new ArithmeticShiftInstruction(0x7C, 0);
        dispatchSimple(instr);
        assertTrue(stack.popInt() > 0);
    }

    @Test
    void testIAnd() {
        stack.pushInt(0b1111);
        stack.pushInt(0b1010);
        SimpleInstruction instr = new SimpleInstruction(0x7E, 0, 1);
        dispatchSimple(instr);
        assertEquals(0b1010, stack.popInt());
    }

    @Test
    void testLAnd() {
        stack.pushLong(0b11111111L);
        stack.pushLong(0b10101010L);
        SimpleInstruction instr = new SimpleInstruction(0x7F, 0, 1);
        dispatchSimple(instr);
        assertEquals(0b10101010L, stack.popLong());
    }

    @Test
    void testIALoad() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);
        array.setInt(2, 999);
        stack.pushReference(array);
        stack.pushInt(2);
        SimpleInstruction instr = new SimpleInstruction(0x2E, 0, 1);
        dispatchSimple(instr);
        assertEquals(999, stack.popInt());
    }

    @Test
    void testIAStore() {
        ArrayInstance array = new ArrayInstance(1, "I", 5);
        stack.pushReference(array);
        stack.pushInt(3);
        stack.pushInt(777);
        SimpleInstruction instr = new SimpleInstruction(0x4F, 0, 1);
        dispatchSimple(instr);
        assertEquals(777, array.getInt(3));
    }

    @Test
    void testAALoad() {
        ObjectInstance element = new ObjectInstance(5, "java/lang/String");
        ArrayInstance array = new ArrayInstance(1, "Ljava/lang/String;", 3);
        array.set(1, element);
        stack.pushReference(array);
        stack.pushInt(1);
        SimpleInstruction instr = new SimpleInstruction(0x32, 0, 1);
        dispatchSimple(instr);
        assertEquals(element, stack.popReference());
    }

    @Test
    void testAAStore() {
        ObjectInstance element = new ObjectInstance(10, "java/lang/String");
        ArrayInstance array = new ArrayInstance(1, "Ljava/lang/String;", 2);
        stack.pushReference(array);
        stack.pushInt(0);
        stack.pushReference(element);
        SimpleInstruction instr = new SimpleInstruction(0x53, 0, 1);
        dispatchSimple(instr);
        assertEquals(element, array.get(0));
    }
}
