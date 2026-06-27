package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.instruction.*;
import com.tonic.testutil.StubDispatchContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherPart5Test {

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

        int opcode = instr.getOpcode();
        return dispatchByOpcode(opcode, instr, frame);
    }

    private OpcodeDispatcher.DispatchResult dispatchByOpcode(int opcode, Instruction instr, SimpleStackFrame frame) {
        switch (opcode) {
            case 0xAC:
                stack.popInt();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.RETURN;

            case 0xAD:
                stack.popLong();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.RETURN;

            case 0xAE:
                stack.popFloat();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.RETURN;

            case 0xAF:
                stack.popDouble();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.RETURN;

            case 0xB0:
                stack.popReference();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.RETURN;

            case 0xB1:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.RETURN;

            case 0xB2:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.FIELD_GET;

            case 0xB3:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.FIELD_PUT;

            case 0xB4:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.FIELD_GET;

            case 0xB5:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.FIELD_PUT;

            case 0xB6:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.INVOKE;

            case 0xB7:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.INVOKE;

            case 0xB8:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.INVOKE;

            case 0xB9:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.INVOKE;

            case 0xBA:
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.INVOKE_DYNAMIC;

            case 0xBF:
                stack.popReference();
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.ATHROW;

            default:
                throw new UnsupportedOperationException("Opcode not handled: 0x" + Integer.toHexString(opcode));
        }
    }

    @Test
    void testGetStatic() {
        SimpleInstruction instr = new SimpleInstruction(0xB2, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_GET, result);
    }

    @Test
    void testPutStatic() {
        SimpleInstruction instr = new SimpleInstruction(0xB3, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_PUT, result);
    }

    @Test
    void testGetField() {
        SimpleInstruction instr = new SimpleInstruction(0xB4, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_GET, result);
    }

    @Test
    void testPutField() {
        SimpleInstruction instr = new SimpleInstruction(0xB5, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_PUT, result);
    }

    @Test
    void testInvokeVirtual() {
        SimpleInstruction instr = new SimpleInstruction(0xB6, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, result);
    }

    @Test
    void testInvokeSpecial() {
        SimpleInstruction instr = new SimpleInstruction(0xB7, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, result);
    }

    @Test
    void testInvokeStatic() {
        SimpleInstruction instr = new SimpleInstruction(0xB8, 0, 3);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, result);
    }

    @Test
    void testInvokeInterface() {
        SimpleInstruction instr = new SimpleInstruction(0xB9, 0, 5);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, result);
    }

    @Test
    void testInvokeDynamic() {
        SimpleInstruction instr = new SimpleInstruction(0xBA, 0, 5);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE_DYNAMIC, result);
    }

    @Test
    void testIReturn() {
        stack.pushInt(42);
        SimpleInstruction instr = new SimpleInstruction(0xAC, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, result);
        assertEquals(depthBefore - 1, stack.depth());
    }

    @Test
    void testLReturn() {
        stack.pushLong(123456789L);
        SimpleInstruction instr = new SimpleInstruction(0xAD, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, result);
        assertEquals(depthBefore - 1, stack.depth());
    }

    @Test
    void testFReturn() {
        stack.pushFloat(3.14f);
        SimpleInstruction instr = new SimpleInstruction(0xAE, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, result);
        assertEquals(depthBefore - 1, stack.depth());
    }

    @Test
    void testDReturn() {
        stack.pushDouble(2.71828);
        SimpleInstruction instr = new SimpleInstruction(0xAF, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, result);
        assertEquals(depthBefore - 1, stack.depth());
    }

    @Test
    void testAReturn() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/Object");
        stack.pushReference(obj);
        SimpleInstruction instr = new SimpleInstruction(0xB0, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, result);
        assertEquals(depthBefore - 1, stack.depth());
    }

    @Test
    void testReturn() {
        SimpleInstruction instr = new SimpleInstruction(0xB1, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, result);
        assertEquals(depthBefore, stack.depth());
    }

    @Test
    void testAThrow() {
        ObjectInstance exception = new ObjectInstance(1, "java/lang/Exception");
        stack.pushReference(exception);
        SimpleInstruction instr = new SimpleInstruction(0xBF, 0, 1);
        int depthBefore = stack.depth();
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.ATHROW, result);
        assertEquals(depthBefore - 1, stack.depth());
    }

    @Test
    void testFieldAccessSequence() {
        SimpleInstruction getStatic = new SimpleInstruction(0xB2, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_GET, dispatchSimple(getStatic));

        SimpleInstruction putStatic = new SimpleInstruction(0xB3, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_PUT, dispatchSimple(putStatic));

        SimpleInstruction getField = new SimpleInstruction(0xB4, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_GET, dispatchSimple(getField));

        SimpleInstruction putField = new SimpleInstruction(0xB5, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.FIELD_PUT, dispatchSimple(putField));
    }

    @Test
    void testInvokeSequence() {
        SimpleInstruction invokeVirtual = new SimpleInstruction(0xB6, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, dispatchSimple(invokeVirtual));

        SimpleInstruction invokeSpecial = new SimpleInstruction(0xB7, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, dispatchSimple(invokeSpecial));

        SimpleInstruction invokeStatic = new SimpleInstruction(0xB8, 0, 3);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, dispatchSimple(invokeStatic));

        SimpleInstruction invokeInterface = new SimpleInstruction(0xB9, 0, 5);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE, dispatchSimple(invokeInterface));

        SimpleInstruction invokeDynamic = new SimpleInstruction(0xBA, 0, 5);
        assertEquals(OpcodeDispatcher.DispatchResult.INVOKE_DYNAMIC, dispatchSimple(invokeDynamic));
    }

    @Test
    void testReturnSequence() {
        stack.pushInt(1);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, dispatchSimple(new SimpleInstruction(0xAC, 0, 1)));

        stack.pushLong(2L);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, dispatchSimple(new SimpleInstruction(0xAD, 0, 1)));

        stack.pushFloat(3.0f);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, dispatchSimple(new SimpleInstruction(0xAE, 0, 1)));

        stack.pushDouble(4.0);
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, dispatchSimple(new SimpleInstruction(0xAF, 0, 1)));

        stack.pushReference(new ObjectInstance(5, "java/lang/Object"));
        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, dispatchSimple(new SimpleInstruction(0xB0, 0, 1)));

        assertEquals(OpcodeDispatcher.DispatchResult.RETURN, dispatchSimple(new SimpleInstruction(0xB1, 0, 1)));
    }

    @Test
    void testReturnDoesNotPopStack() {
        stack.pushInt(100);
        stack.pushInt(200);
        SimpleInstruction instr = new SimpleInstruction(0xB1, 0, 1);
        dispatchSimple(instr);
        assertEquals(2, stack.depth());
    }

    @Test
    void testAThrowPopsException() {
        ObjectInstance ex1 = new ObjectInstance(1, "java/lang/RuntimeException");
        ObjectInstance ex2 = new ObjectInstance(2, "java/lang/Exception");
        stack.pushReference(ex1);
        stack.pushReference(ex2);

        SimpleInstruction instr = new SimpleInstruction(0xBF, 0, 1);
        dispatchSimple(instr);

        assertEquals(1, stack.depth());
        assertEquals(ex1, stack.popReference());
    }
}
