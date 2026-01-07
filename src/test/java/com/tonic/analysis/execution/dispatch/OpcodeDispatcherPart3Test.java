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

class OpcodeDispatcherPart3Test {

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

    private static class TestStackFrame {
        private Instruction currentInstruction;
        private ConcreteStack stack;
        private ConcreteLocals locals;
        private int pc;

        public TestStackFrame(ConcreteStack stack, ConcreteLocals locals) {
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
        TestStackFrame frame = new TestStackFrame(stack, locals);
        frame.setCurrentInstruction(instr);
        return dispatchByOpcode(instr.getOpcode(), instr, frame);
    }

    private OpcodeDispatcher.DispatchResult dispatchByOpcode(int opcode, Instruction instr, TestStackFrame frame) {
        switch (opcode) {
            case 0x94: {
                long v2 = stack.popLong();
                long v1 = stack.popLong();
                stack.pushInt(Long.compare(v1, v2));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x95: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                if (Float.isNaN(v1) || Float.isNaN(v2)) stack.pushInt(-1);
                else stack.pushInt(Float.compare(v1, v2));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x96: {
                float v2 = stack.popFloat();
                float v1 = stack.popFloat();
                if (Float.isNaN(v1) || Float.isNaN(v2)) stack.pushInt(1);
                else stack.pushInt(Float.compare(v1, v2));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x97: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                if (Double.isNaN(v1) || Double.isNaN(v2)) stack.pushInt(-1);
                else stack.pushInt(Double.compare(v1, v2));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x98: {
                double v2 = stack.popDouble();
                double v1 = stack.popDouble();
                if (Double.isNaN(v1) || Double.isNaN(v2)) stack.pushInt(1);
                else stack.pushInt(Double.compare(v1, v2));
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x99: {
                int value = stack.popInt();
                if (value == 0) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x9A: {
                int value = stack.popInt();
                if (value != 0) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x9B: {
                int value = stack.popInt();
                if (value < 0) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x9C: {
                int value = stack.popInt();
                if (value >= 0) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x9D: {
                int value = stack.popInt();
                if (value > 0) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x9E: {
                int value = stack.popInt();
                if (value <= 0) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0x9F: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                if (v1 == v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA0: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                if (v1 != v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA1: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                if (v1 < v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA2: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                if (v1 >= v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA3: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                if (v1 > v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA4: {
                int v2 = stack.popInt();
                int v1 = stack.popInt();
                if (v1 <= v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA5: {
                ObjectInstance v2 = stack.popReference();
                ObjectInstance v1 = stack.popReference();
                if (v1 == v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA6: {
                ObjectInstance v2 = stack.popReference();
                ObjectInstance v1 = stack.popReference();
                if (v1 != v2) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xA7: {
                context.setBranchTarget(((GotoInstruction) instr).getBranchOffset());
                return OpcodeDispatcher.DispatchResult.BRANCH;
            }

            case 0xC6: {
                ObjectInstance ref = stack.popReference();
                if (ref == null) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            case 0xC7: {
                ObjectInstance ref = stack.popReference();
                if (ref != null) {
                    context.setBranchTarget(((ConditionalBranchInstruction) instr).getBranchOffset());
                    return OpcodeDispatcher.DispatchResult.BRANCH;
                }
                frame.advancePC(instr.getLength());
                return OpcodeDispatcher.DispatchResult.CONTINUE;
            }

            default:
                throw new UnsupportedOperationException("Opcode not handled: 0x" + Integer.toHexString(opcode));
        }
    }

    @Test
    void testLCmpLess() {
        stack.pushLong(5L);
        stack.pushLong(10L);
        SimpleInstruction instr = new SimpleInstruction(0x94, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testLCmpEqual() {
        stack.pushLong(42L);
        stack.pushLong(42L);
        SimpleInstruction instr = new SimpleInstruction(0x94, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testLCmpGreater() {
        stack.pushLong(100L);
        stack.pushLong(50L);
        SimpleInstruction instr = new SimpleInstruction(0x94, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testFCmpLLess() {
        stack.pushFloat(1.5f);
        stack.pushFloat(2.5f);
        SimpleInstruction instr = new SimpleInstruction(0x95, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testFCmpLEqual() {
        stack.pushFloat(3.14f);
        stack.pushFloat(3.14f);
        SimpleInstruction instr = new SimpleInstruction(0x95, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testFCmpLGreater() {
        stack.pushFloat(5.0f);
        stack.pushFloat(2.0f);
        SimpleInstruction instr = new SimpleInstruction(0x95, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testFCmpLNaN() {
        stack.pushFloat(Float.NaN);
        stack.pushFloat(1.0f);
        SimpleInstruction instr = new SimpleInstruction(0x95, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testFCmpGLess() {
        stack.pushFloat(1.0f);
        stack.pushFloat(3.0f);
        SimpleInstruction instr = new SimpleInstruction(0x96, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testFCmpGEqual() {
        stack.pushFloat(2.5f);
        stack.pushFloat(2.5f);
        SimpleInstruction instr = new SimpleInstruction(0x96, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testFCmpGGreater() {
        stack.pushFloat(10.0f);
        stack.pushFloat(5.0f);
        SimpleInstruction instr = new SimpleInstruction(0x96, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testFCmpGNaN() {
        stack.pushFloat(Float.NaN);
        stack.pushFloat(1.0f);
        SimpleInstruction instr = new SimpleInstruction(0x96, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testDCmpLLess() {
        stack.pushDouble(1.0);
        stack.pushDouble(2.0);
        SimpleInstruction instr = new SimpleInstruction(0x97, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testDCmpLEqual() {
        stack.pushDouble(3.14159);
        stack.pushDouble(3.14159);
        SimpleInstruction instr = new SimpleInstruction(0x97, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testDCmpLGreater() {
        stack.pushDouble(100.0);
        stack.pushDouble(50.0);
        SimpleInstruction instr = new SimpleInstruction(0x97, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testDCmpLNaN() {
        stack.pushDouble(Double.NaN);
        stack.pushDouble(1.0);
        SimpleInstruction instr = new SimpleInstruction(0x97, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testDCmpGLess() {
        stack.pushDouble(5.0);
        stack.pushDouble(10.0);
        SimpleInstruction instr = new SimpleInstruction(0x98, 0, 1);
        dispatchSimple(instr);
        assertEquals(-1, stack.popInt());
    }

    @Test
    void testDCmpGEqual() {
        stack.pushDouble(7.5);
        stack.pushDouble(7.5);
        SimpleInstruction instr = new SimpleInstruction(0x98, 0, 1);
        dispatchSimple(instr);
        assertEquals(0, stack.popInt());
    }

    @Test
    void testDCmpGGreater() {
        stack.pushDouble(20.0);
        stack.pushDouble(10.0);
        SimpleInstruction instr = new SimpleInstruction(0x98, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testDCmpGNaN() {
        stack.pushDouble(Double.NaN);
        stack.pushDouble(1.0);
        SimpleInstruction instr = new SimpleInstruction(0x98, 0, 1);
        dispatchSimple(instr);
        assertEquals(1, stack.popInt());
    }

    @Test
    void testIfEqBranchTaken() {
        stack.pushInt(0);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 0, (short) 100);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(100, context.getBranchTarget());
    }

    @Test
    void testIfEqBranchNotTaken() {
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x99, 0, (short) 100);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfNeBranchTaken() {
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9A, 0, (short) 200);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(200, context.getBranchTarget());
    }

    @Test
    void testIfNeBranchNotTaken() {
        stack.pushInt(0);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9A, 0, (short) 200);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfLtBranchTaken() {
        stack.pushInt(-5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9B, 0, (short) 300);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(300, context.getBranchTarget());
    }

    @Test
    void testIfLtBranchNotTaken() {
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9B, 0, (short) 300);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfGeBranchTaken() {
        stack.pushInt(0);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9C, 0, (short) 400);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(400, context.getBranchTarget());
    }

    @Test
    void testIfGeBranchNotTaken() {
        stack.pushInt(-1);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9C, 0, (short) 400);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfGtBranchTaken() {
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9D, 0, (short) 500);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(500, context.getBranchTarget());
    }

    @Test
    void testIfGtBranchNotTaken() {
        stack.pushInt(0);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9D, 0, (short) 500);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfLeBranchTaken() {
        stack.pushInt(0);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9E, 0, (short) 600);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(600, context.getBranchTarget());
    }

    @Test
    void testIfLeBranchNotTaken() {
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9E, 0, (short) 600);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfICmpEqBranchTaken() {
        stack.pushInt(42);
        stack.pushInt(42);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9F, 0, (short) 700);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(700, context.getBranchTarget());
    }

    @Test
    void testIfICmpEqBranchNotTaken() {
        stack.pushInt(42);
        stack.pushInt(43);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0x9F, 0, (short) 700);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfICmpNeBranchTaken() {
        stack.pushInt(5);
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA0, 0, (short) 800);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(800, context.getBranchTarget());
    }

    @Test
    void testIfICmpNeBranchNotTaken() {
        stack.pushInt(7);
        stack.pushInt(7);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA0, 0, (short) 800);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfICmpLtBranchTaken() {
        stack.pushInt(3);
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA1, 0, (short) 900);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(900, context.getBranchTarget());
    }

    @Test
    void testIfICmpLtBranchNotTaken() {
        stack.pushInt(10);
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA1, 0, (short) 900);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfICmpGeBranchTaken() {
        stack.pushInt(10);
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA2, 0, (short) 1000);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1000, context.getBranchTarget());
    }

    @Test
    void testIfICmpGeBranchNotTaken() {
        stack.pushInt(5);
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA2, 0, (short) 1000);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfICmpGtBranchTaken() {
        stack.pushInt(20);
        stack.pushInt(10);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA3, 0, (short) 1100);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1100, context.getBranchTarget());
    }

    @Test
    void testIfICmpGtBranchNotTaken() {
        stack.pushInt(5);
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA3, 0, (short) 1100);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfICmpLeBranchTaken() {
        stack.pushInt(5);
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA4, 0, (short) 1200);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1200, context.getBranchTarget());
    }

    @Test
    void testIfICmpLeBranchNotTaken() {
        stack.pushInt(10);
        stack.pushInt(5);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA4, 0, (short) 1200);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfACmpEqBranchTaken() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        stack.pushReference(obj);
        stack.pushReference(obj);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA5, 0, (short) 1300);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1300, context.getBranchTarget());
    }

    @Test
    void testIfACmpEqBranchNotTaken() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/String");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/String");
        stack.pushReference(obj1);
        stack.pushReference(obj2);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA5, 0, (short) 1300);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfACmpNeBranchTaken() {
        ObjectInstance obj1 = new ObjectInstance(1, "java/lang/String");
        ObjectInstance obj2 = new ObjectInstance(2, "java/lang/String");
        stack.pushReference(obj1);
        stack.pushReference(obj2);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA6, 0, (short) 1400);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1400, context.getBranchTarget());
    }

    @Test
    void testIfACmpNeBranchNotTaken() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        stack.pushReference(obj);
        stack.pushReference(obj);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xA6, 0, (short) 1400);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testGoto() {
        GotoInstruction instr = new GotoInstruction(0xA7, 0, (short) 1500);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1500, context.getBranchTarget());
    }

    @Test
    void testIfNullBranchTaken() {
        stack.pushNull();
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xC6, 0, (short) 1600);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1600, context.getBranchTarget());
    }

    @Test
    void testIfNullBranchNotTaken() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        stack.pushReference(obj);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xC6, 0, (short) 1600);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }

    @Test
    void testIfNonNullBranchTaken() {
        ObjectInstance obj = new ObjectInstance(1, "java/lang/String");
        stack.pushReference(obj);
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xC7, 0, (short) 1700);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.BRANCH, result);
        assertEquals(1700, context.getBranchTarget());
    }

    @Test
    void testIfNonNullBranchNotTaken() {
        stack.pushNull();
        ConditionalBranchInstruction instr = new ConditionalBranchInstruction(0xC7, 0, (short) 1700);
        OpcodeDispatcher.DispatchResult result = dispatchSimple(instr);
        assertEquals(OpcodeDispatcher.DispatchResult.CONTINUE, result);
    }
}
