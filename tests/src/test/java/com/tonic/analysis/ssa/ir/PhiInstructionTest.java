package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PhiInstructionTest {

    private PhiInstruction phi;
    private SSAValue result;
    private IRBlock block1;
    private IRBlock block2;
    private IRBlock block3;

    @BeforeEach
    void setUp() {
        SSAValue.resetIdCounter();
        IRBlock.resetIdCounter();
        IRInstruction.resetIdCounter();

        result = new SSAValue(PrimitiveType.INT, "result");
        phi = new PhiInstruction(result);
        block1 = new IRBlock("pred1");
        block2 = new IRBlock("pred2");
        block3 = new IRBlock("pred3");
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructorSetsResultValue() {
            assertNotNull(phi.getResult());
            assertEquals(result, phi.getResult());
        }

        @Test
        void constructorInitializesEmptyIncomingValues() {
            assertTrue(phi.getIncomingValues().isEmpty());
            assertTrue(phi.getIncomingBlocks().isEmpty());
        }

        @Test
        void constructorSetsResultDefinition() {
            assertEquals(phi, result.getDefinition());
        }
    }

    @Nested
    class AddIncomingTests {

        @Test
        void addIncomingSingleValue() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);

            phi.addIncoming(value1, block1);

            assertEquals(1, phi.getIncomingValues().size());
            assertEquals(value1, phi.getIncoming(block1));
        }

        @Test
        void addIncomingMultipleValues() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue value2 = new SSAValue(PrimitiveType.INT);
            SSAValue value3 = new SSAValue(PrimitiveType.INT);

            phi.addIncoming(value1, block1);
            phi.addIncoming(value2, block2);
            phi.addIncoming(value3, block3);

            assertEquals(3, phi.getIncomingValues().size());
            assertEquals(value1, phi.getIncoming(block1));
            assertEquals(value2, phi.getIncoming(block2));
            assertEquals(value3, phi.getIncoming(block3));
        }

        @Test
        void addIncomingUpdatesValueUses() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);

            phi.addIncoming(value1, block1);

            assertEquals(1, value1.getUseCount());
            assertTrue(value1.getUses().contains(phi));
        }

        @Test
        void addIncomingReplacesExistingBlockMapping() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue value2 = new SSAValue(PrimitiveType.INT);

            phi.addIncoming(value1, block1);
            phi.addIncoming(value2, block1);

            assertEquals(1, phi.getIncomingValues().size());
            assertEquals(value2, phi.getIncoming(block1));
        }

        @Test
        void addIncomingWithNonSSAValue() {
            Value constantValue = new TestConstantValue();

            phi.addIncoming(constantValue, block1);

            assertEquals(constantValue, phi.getIncoming(block1));
        }
    }

    @Nested
    class RemoveIncomingTests {

        @Test
        void removeIncomingRemovesMapping() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);

            phi.removeIncoming(block1);

            assertNull(phi.getIncoming(block1));
            assertTrue(phi.getIncomingValues().isEmpty());
        }

        @Test
        void removeIncomingUpdatesValueUses() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);

            phi.removeIncoming(block1);

            assertEquals(0, value1.getUseCount());
            assertFalse(value1.getUses().contains(phi));
        }

        @Test
        void removeIncomingNonExistentBlockDoesNothing() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);

            phi.removeIncoming(block2);

            assertEquals(1, phi.getIncomingValues().size());
            assertEquals(value1, phi.getIncoming(block1));
        }

        @Test
        void removeIncomingPreservesOtherMappings() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue value2 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);
            phi.addIncoming(value2, block2);

            phi.removeIncoming(block1);

            assertEquals(1, phi.getIncomingValues().size());
            assertEquals(value2, phi.getIncoming(block2));
        }
    }

    @Nested
    class GetIncomingTests {

        @Test
        void getIncomingReturnsCorrectValue() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);

            assertEquals(value1, phi.getIncoming(block1));
        }

        @Test
        void getIncomingReturnsNullForNonExistentBlock() {
            assertNull(phi.getIncoming(block1));
        }

        @Test
        void getIncomingBlocksReturnsAllBlocks() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue value2 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);
            phi.addIncoming(value2, block2);

            Set<IRBlock> blocks = phi.getIncomingBlocks();

            assertEquals(2, blocks.size());
            assertTrue(blocks.contains(block1));
            assertTrue(blocks.contains(block2));
        }

        @Test
        void getIncomingBlocksReturnsEmptySetWhenNoIncoming() {
            Set<IRBlock> blocks = phi.getIncomingBlocks();

            assertTrue(blocks.isEmpty());
        }
    }

    @Nested
    class OperandTests {

        @Test
        void getOperandsReturnsAllIncomingValues() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue value2 = new SSAValue(PrimitiveType.INT);
            SSAValue value3 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);
            phi.addIncoming(value2, block2);
            phi.addIncoming(value3, block3);

            List<Value> operands = phi.getOperands();

            assertEquals(3, operands.size());
            assertTrue(operands.contains(value1));
            assertTrue(operands.contains(value2));
            assertTrue(operands.contains(value3));
        }

        @Test
        void getOperandsReturnsEmptyListWhenNoIncoming() {
            List<Value> operands = phi.getOperands();

            assertTrue(operands.isEmpty());
        }

        @Test
        void getOperandsReturnsNewList() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);

            List<Value> operands1 = phi.getOperands();
            List<Value> operands2 = phi.getOperands();

            assertNotSame(operands1, operands2);
        }
    }

    @Nested
    class ReplaceOperandTests {

        @Test
        void replaceOperandReplacesMatchingValue() {
            SSAValue oldValue = new SSAValue(PrimitiveType.INT);
            SSAValue newValue = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(oldValue, block1);

            phi.replaceOperand(oldValue, newValue);

            assertEquals(newValue, phi.getIncoming(block1));
        }

        @Test
        void replaceOperandUpdatesOldValueUses() {
            SSAValue oldValue = new SSAValue(PrimitiveType.INT);
            SSAValue newValue = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(oldValue, block1);

            phi.replaceOperand(oldValue, newValue);

            assertEquals(0, oldValue.getUseCount());
            assertFalse(oldValue.getUses().contains(phi));
        }

        @Test
        void replaceOperandUpdatesNewValueUses() {
            SSAValue oldValue = new SSAValue(PrimitiveType.INT);
            SSAValue newValue = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(oldValue, block1);

            phi.replaceOperand(oldValue, newValue);

            assertEquals(1, newValue.getUseCount());
            assertTrue(newValue.getUses().contains(phi));
        }

        @Test
        void replaceOperandReplacesMultipleOccurrences() {
            SSAValue oldValue = new SSAValue(PrimitiveType.INT);
            SSAValue newValue = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(oldValue, block1);
            phi.addIncoming(oldValue, block2);

            phi.replaceOperand(oldValue, newValue);

            assertEquals(newValue, phi.getIncoming(block1));
            assertEquals(newValue, phi.getIncoming(block2));
        }

        @Test
        void replaceOperandDoesNothingForNonExistentValue() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT);
            SSAValue oldValue = new SSAValue(PrimitiveType.INT);
            SSAValue newValue = new SSAValue(PrimitiveType.INT);
            phi.addIncoming(value1, block1);

            phi.replaceOperand(oldValue, newValue);

            assertEquals(value1, phi.getIncoming(block1));
            assertEquals(0, newValue.getUseCount());
        }
    }

    @Nested
    class VisitorTests {

        @Test
        void acceptCallsVisitPhi() {
            TestVisitor visitor = new TestVisitor();

            phi.accept(visitor);

            assertTrue(visitor.visitedPhi);
            assertEquals(phi, visitor.lastPhi);
        }

        @Test
        void acceptReturnsVisitorResult() {
            TestVisitor visitor = new TestVisitor();
            visitor.returnValue = 42;

            Integer result = phi.accept(visitor);

            assertEquals(42, result);
        }
    }

    @Nested
    class PredicateTests {

        @Test
        void isPhiReturnsTrue() {
            assertTrue(phi.isPhi());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringWithNoIncoming() {
            String str = phi.toString();

            assertTrue(str.contains("result"));
            assertTrue(str.contains("phi"));
        }

        @Test
        void toStringWithSingleIncoming() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT, "v1");
            phi.addIncoming(value1, block1);

            String str = phi.toString();

            assertTrue(str.contains("result"));
            assertTrue(str.contains("phi"));
            assertTrue(str.contains("v1"));
            assertTrue(str.contains(block1.getName()));
        }

        @Test
        void toStringWithMultipleIncoming() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT, "v1");
            SSAValue value2 = new SSAValue(PrimitiveType.INT, "v2");
            phi.addIncoming(value1, block1);
            phi.addIncoming(value2, block2);

            String str = phi.toString();

            assertTrue(str.contains("v1"));
            assertTrue(str.contains("v2"));
            assertTrue(str.contains(block1.getName()));
            assertTrue(str.contains(block2.getName()));
        }

        @Test
        void toStringFormatMatchesExpectedPattern() {
            SSAValue value1 = new SSAValue(PrimitiveType.INT, "v1");
            phi.addIncoming(value1, block1);

            String str = phi.toString();

            assertTrue(str.matches(".*=\\s*phi\\s*\\[.*\\].*"));
        }
    }

    private static class TestVisitor implements IRVisitor<Integer> {
        boolean visitedPhi = false;
        PhiInstruction lastPhi = null;
        Integer returnValue = 0;

        @Override
        public Integer visitPhi(PhiInstruction phi) {
            visitedPhi = true;
            lastPhi = phi;
            return returnValue;
        }

        @Override
        public Integer visitBinaryOp(BinaryOpInstruction binaryOp) { return null; }

        @Override
        public Integer visitUnaryOp(UnaryOpInstruction unaryOp) { return null; }

        @Override
        public Integer visitBranch(BranchInstruction branch) { return null; }

        @Override
        public Integer visitSwitch(SwitchInstruction switchInstr) { return null; }

        @Override
        public Integer visitReturn(ReturnInstruction returnInstr) { return null; }

        @Override
        public Integer visitLoadLocal(LoadLocalInstruction loadLocal) { return null; }

        @Override
        public Integer visitStoreLocal(StoreLocalInstruction storeLocal) { return null; }

        @Override
        public Integer visitInvoke(InvokeInstruction invoke) { return null; }

        @Override
        public Integer visitNew(NewInstruction newInstr) { return null; }

        @Override
        public Integer visitNewArray(NewArrayInstruction newArray) { return null; }

        @Override
        public Integer visitCopy(CopyInstruction copy) { return null; }

        @Override
        public Integer visitConstant(ConstantInstruction constant) { return null; }

        @Override
        public Integer visitFieldAccess(FieldAccessInstruction fieldAccess) { return null; }

        @Override
        public Integer visitArrayAccess(ArrayAccessInstruction arrayAccess) { return null; }

        @Override
        public Integer visitTypeCheck(TypeCheckInstruction typeCheck) { return null; }

        @Override
        public Integer visitSimple(SimpleInstruction simple) { return null; }
    }

    private static class TestConstantValue implements Value {
        @Override
        public com.tonic.analysis.ssa.type.IRType getType() {
            return PrimitiveType.INT;
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }
}
