package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.ArrayType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.*;
import com.tonic.analysis.ssa.visitor.AbstractIRVisitor;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for all SSA IR instruction types.
 * Tests construction, getters, visitor pattern, and edge cases.
 */
class IRInstructionTest {

    private IRMethod method;
    private IRBlock block1;
    private IRBlock block2;
    private IRBlock block3;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "test", "()V", true);
        block1 = new IRBlock("block1");
        block2 = new IRBlock("block2");
        block3 = new IRBlock("block3");
        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(block3);
    }

    // ========== BootstrapMethodInfo Tests (0% coverage - CRITICAL) ==========

    @Nested
    class BootstrapMethodInfoTests {

        @Test
        void constructionWithValidArgs() {
            MethodHandleConstant handle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "java/lang/invoke/LambdaMetafactory",
                    "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)V"
            );
            List<Constant> args = List.of(IntConstant.of(42));

            BootstrapMethodInfo info = new BootstrapMethodInfo(handle, args);

            assertNotNull(info);
            assertEquals(handle, info.getBootstrapMethod());
            assertEquals(1, info.getBootstrapArguments().size());
        }

        @Test
        void constructionWithEmptyArgs() {
            MethodHandleConstant handle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "com/test/Bootstrap",
                    "bootstrap",
                    "()V"
            );

            BootstrapMethodInfo info = new BootstrapMethodInfo(handle, Collections.emptyList());

            assertTrue(info.getBootstrapArguments().isEmpty());
        }

        @Test
        void constructionThrowsOnNullHandle() {
            assertThrows(NullPointerException.class, () ->
                new BootstrapMethodInfo(null, Collections.emptyList())
            );
        }

        @Test
        void isLambdaMetafactoryDetection() {
            MethodHandleConstant lambdaHandle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "java/lang/invoke/LambdaMetafactory",
                    "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)V"
            );
            BootstrapMethodInfo lambdaInfo = new BootstrapMethodInfo(lambdaHandle, Collections.emptyList());

            assertTrue(lambdaInfo.isLambdaMetafactory());
        }

        @Test
        void isLambdaMetafactoryFalseForOther() {
            MethodHandleConstant otherHandle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "com/test/Bootstrap",
                    "metafactory",
                    "()V"
            );
            BootstrapMethodInfo info = new BootstrapMethodInfo(otherHandle, Collections.emptyList());

            assertFalse(info.isLambdaMetafactory());
        }

        @Test
        void isStringConcatFactoryDetection() {
            MethodHandleConstant concatHandle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "java/lang/invoke/StringConcatFactory",
                    "makeConcatWithConstants",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;)V"
            );
            BootstrapMethodInfo info = new BootstrapMethodInfo(concatHandle, Collections.emptyList());

            assertTrue(info.isStringConcatFactory());
        }

        @Test
        void isStringConcatFactoryFalseForOther() {
            MethodHandleConstant otherHandle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "com/test/StringFactory",
                    "makeConcat",
                    "()V"
            );
            BootstrapMethodInfo info = new BootstrapMethodInfo(otherHandle, Collections.emptyList());

            assertFalse(info.isStringConcatFactory());
        }

        @Test
        void toStringWithArgs() {
            MethodHandleConstant handle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "com/test/Bootstrap",
                    "bsm",
                    "()V"
            );
            List<Constant> args = List.of(IntConstant.of(1), IntConstant.of(2));
            BootstrapMethodInfo info = new BootstrapMethodInfo(handle, args);

            String str = info.toString();
            assertTrue(str.contains("Bootstrap"));
            assertTrue(str.contains("com/test/Bootstrap"));
            assertTrue(str.contains("bsm"));
            assertTrue(str.contains("args=2"));
        }

        @Test
        void toStringWithoutArgs() {
            MethodHandleConstant handle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "com/test/Bootstrap",
                    "bsm",
                    "()V"
            );
            BootstrapMethodInfo info = new BootstrapMethodInfo(handle, Collections.emptyList());

            String str = info.toString();
            assertFalse(str.contains("args="));
        }

        @Test
        void equalsAndHashCode() {
            MethodHandleConstant handle1 = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic, "com/test/A", "bsm", "()V"
            );
            MethodHandleConstant handle2 = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic, "com/test/A", "bsm", "()V"
            );
            List<Constant> args = List.of(IntConstant.of(42));

            BootstrapMethodInfo info1 = new BootstrapMethodInfo(handle1, args);
            BootstrapMethodInfo info2 = new BootstrapMethodInfo(handle2, args);

            assertEquals(info1, info2);
            assertEquals(info1.hashCode(), info2.hashCode());
        }

        @Test
        void notEqualsDifferentHandle() {
            MethodHandleConstant handle1 = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic, "com/test/A", "bsm", "()V"
            );
            MethodHandleConstant handle2 = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic, "com/test/B", "bsm", "()V"
            );

            BootstrapMethodInfo info1 = new BootstrapMethodInfo(handle1, Collections.emptyList());
            BootstrapMethodInfo info2 = new BootstrapMethodInfo(handle2, Collections.emptyList());

            assertNotEquals(info1, info2);
        }

        @Test
        void argumentsAreImmutable() {
            MethodHandleConstant handle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic, "com/test/A", "bsm", "()V"
            );
            List<Constant> mutableArgs = new ArrayList<>();
            mutableArgs.add(IntConstant.of(1));

            BootstrapMethodInfo info = new BootstrapMethodInfo(handle, mutableArgs);
            mutableArgs.add(IntConstant.of(2)); // Modify original list

            assertEquals(1, info.getBootstrapArguments().size()); // Should still be 1
        }
    }

    // ========== SwitchInstruction Tests (20% coverage) ==========

    @Nested
    class SwitchInstructionTests {

        @Test
        void constructionWithKeyAndDefault() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block2);

            assertNotNull(sw);
            assertEquals(key, sw.getKey());
            assertEquals(block2, sw.getDefaultTarget());
            assertTrue(sw.getCases().isEmpty());
        }

        @Test
        void addCase() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block3);

            sw.addCase(1, block1);
            sw.addCase(2, block2);

            assertEquals(2, sw.getCases().size());
            assertEquals(block1, sw.getCase(1));
            assertEquals(block2, sw.getCase(2));
        }

        @Test
        void getCaseReturnsDefaultForUnknown() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block3);
            sw.addCase(1, block1);

            assertEquals(block3, sw.getCase(999)); // Unknown case returns default
        }

        @Test
        void getOperandsReturnsKey() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block2);

            List<Value> operands = sw.getOperands();
            assertEquals(1, operands.size());
            assertEquals(key, operands.get(0));
        }

        @Test
        void replaceOperand() {
            SSAValue oldKey = new SSAValue(PrimitiveType.INT, "oldKey");
            SSAValue newKey = new SSAValue(PrimitiveType.INT, "newKey");
            SwitchInstruction sw = new SwitchInstruction(oldKey, block2);

            sw.replaceOperand(oldKey, newKey);

            assertEquals(newKey, sw.getKey());
        }

        @Test
        void isTerminator() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block2);

            assertTrue(sw.isTerminator());
        }

        @Test
        void replaceTarget() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block1);
            sw.addCase(1, block2);

            IRBlock newBlock = new IRBlock("newBlock");
            sw.replaceTarget(block1, newBlock);

            assertEquals(newBlock, sw.getDefaultTarget());
        }

        @Test
        void replaceTargetInCases() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block3);
            sw.addCase(1, block1);
            sw.addCase(2, block1);

            IRBlock newBlock = new IRBlock("newBlock");
            sw.replaceTarget(block1, newBlock);

            assertEquals(newBlock, sw.getCase(1));
            assertEquals(newBlock, sw.getCase(2));
        }

        @Test
        void setDefaultTarget() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block1);

            sw.setDefaultTarget(block2);

            assertEquals(block2, sw.getDefaultTarget());
        }

        @Test
        void visitorAccept() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block2);

            String result = sw.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitSwitch(SwitchInstruction instr) {
                    return "visited_switch";
                }
            });

            assertEquals("visited_switch", result);
        }

        @Test
        void toStringFormat() {
            SSAValue key = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction sw = new SwitchInstruction(key, block3);
            sw.addCase(1, block1);
            sw.addCase(2, block2);

            String str = sw.toString();
            assertTrue(str.contains("switch"));
            assertTrue(str.contains("case 1"));
            assertTrue(str.contains("case 2"));
            assertTrue(str.contains("default"));
        }
    }

    // ========== BranchInstruction Tests (33% coverage) ==========

    @Nested
    class BranchInstructionTests {

        @Test
        void constructionWithTwoOperands() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");

            BranchInstruction branch = new BranchInstruction(
                    CompareOp.EQ, left, right, block1, block2
            );

            assertEquals(CompareOp.EQ, branch.getCondition());
            assertEquals(left, branch.getLeft());
            assertEquals(right, branch.getRight());
            assertEquals(block1, branch.getTrueTarget());
            assertEquals(block2, branch.getFalseTarget());
        }

        @Test
        void constructionWithSingleOperand() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");

            BranchInstruction branch = new BranchInstruction(
                    CompareOp.EQ, operand, block1, block2
            );

            assertEquals(operand, branch.getLeft());
            assertNull(branch.getRight());
        }

        @Test
        void allCompareOps() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");

            for (CompareOp op : CompareOp.values()) {
                BranchInstruction branch = new BranchInstruction(op, left, right, block1, block2);
                assertEquals(op, branch.getCondition());
            }
        }

        @Test
        void getOperandsWithTwoArgs() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            BranchInstruction branch = new BranchInstruction(CompareOp.NE, left, right, block1, block2);

            List<Value> operands = branch.getOperands();
            assertEquals(2, operands.size());
            assertEquals(left, operands.get(0));
            assertEquals(right, operands.get(1));
        }

        @Test
        void getOperandsWithOneArg() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            List<Value> operands = branch.getOperands();
            assertEquals(1, operands.size());
        }

        @Test
        void replaceLeftOperand() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            SSAValue newLeft = new SSAValue(PrimitiveType.INT, "newLeft");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, left, right, block1, block2);

            branch.replaceOperand(left, newLeft);

            assertEquals(newLeft, branch.getLeft());
            assertEquals(right, branch.getRight());
        }

        @Test
        void replaceRightOperand() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            SSAValue newRight = new SSAValue(PrimitiveType.INT, "newRight");
            BranchInstruction branch = new BranchInstruction(CompareOp.LT, left, right, block1, block2);

            branch.replaceOperand(right, newRight);

            assertEquals(left, branch.getLeft());
            assertEquals(newRight, branch.getRight());
        }

        @Test
        void isTerminator() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            assertTrue(branch.isTerminator());
        }

        @Test
        void replaceTargetTrue() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);
            IRBlock newTarget = new IRBlock("new");

            branch.replaceTarget(block1, newTarget);

            assertEquals(newTarget, branch.getTrueTarget());
            assertEquals(block2, branch.getFalseTarget());
        }

        @Test
        void replaceTargetFalse() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);
            IRBlock newTarget = new IRBlock("new");

            branch.replaceTarget(block2, newTarget);

            assertEquals(block1, branch.getTrueTarget());
            assertEquals(newTarget, branch.getFalseTarget());
        }

        @Test
        void setTrueTarget() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            branch.setTrueTarget(block3);

            assertEquals(block3, branch.getTrueTarget());
        }

        @Test
        void setFalseTarget() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            branch.setFalseTarget(block3);

            assertEquals(block3, branch.getFalseTarget());
        }

        @Test
        void visitorAccept() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            String result = branch.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitBranch(BranchInstruction instr) {
                    return "visited_branch";
                }
            });

            assertEquals("visited_branch", result);
        }

        @Test
        void toStringWithTwoOperands() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, left, right, block1, block2);

            String str = branch.toString();
            assertTrue(str.contains("if"));
            assertTrue(str.contains("eq"));
            assertTrue(str.contains("goto"));
        }

        @Test
        void toStringWithSingleOperand() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            String str = branch.toString();
            assertTrue(str.contains("if"));
        }

        @Test
        void copyWithNewOperandsTwoArgs() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            SSAValue newLeft = new SSAValue(PrimitiveType.INT, "newLeft");
            SSAValue newRight = new SSAValue(PrimitiveType.INT, "newRight");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, left, right, block1, block2);

            IRInstruction copy = branch.copyWithNewOperands(null, List.of(newLeft, newRight));

            assertNotNull(copy);
            assertTrue(copy instanceof BranchInstruction);
            BranchInstruction branchCopy = (BranchInstruction) copy;
            assertEquals(newLeft, branchCopy.getLeft());
            assertEquals(newRight, branchCopy.getRight());
        }

        @Test
        void copyWithNewOperandsSingleArg() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            SSAValue newOperand = new SSAValue(PrimitiveType.INT, "newOperand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            IRInstruction copy = branch.copyWithNewOperands(null, List.of(newOperand));

            assertNotNull(copy);
            assertTrue(copy instanceof BranchInstruction);
        }

        @Test
        void copyWithNewOperandsEmptyReturnsNull() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "operand");
            BranchInstruction branch = new BranchInstruction(CompareOp.EQ, operand, block1, block2);

            IRInstruction copy = branch.copyWithNewOperands(null, Collections.emptyList());

            assertNull(copy);
        }
    }

    // ========== InvokeInstruction Tests (54% coverage) ==========

    @Nested
    class InvokeInstructionTests {

        @Test
        void staticInvoke() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "result");
            InvokeInstruction invoke = new InvokeInstruction(
                    result,
                    InvokeType.STATIC,
                    "java/lang/Math",
                    "abs",
                    "(I)I",
                    List.of(IntConstant.of(5))
            );

            assertEquals(InvokeType.STATIC, invoke.getInvokeType());
            assertEquals("java/lang/Math", invoke.getOwner());
            assertEquals("abs", invoke.getName());
            assertEquals("(I)I", invoke.getDescriptor());
            assertEquals(result, invoke.getResult());
        }

        @Test
        void virtualInvoke() {
            SSAValue receiver = new SSAValue(new ReferenceType("java/lang/String"), "str");
            SSAValue result = new SSAValue(PrimitiveType.INT, "len");
            InvokeInstruction invoke = new InvokeInstruction(
                    result,
                    InvokeType.VIRTUAL,
                    "java/lang/String",
                    "length",
                    "()I",
                    List.of(receiver)
            );

            assertEquals(InvokeType.VIRTUAL, invoke.getInvokeType());
            assertFalse(invoke.getArguments().isEmpty());
        }

        @Test
        void interfaceInvoke() {
            SSAValue receiver = new SSAValue(new ReferenceType("java/util/List"), "list");
            SSAValue result = new SSAValue(PrimitiveType.INT, "size");
            InvokeInstruction invoke = new InvokeInstruction(
                    result,
                    InvokeType.INTERFACE,
                    "java/util/List",
                    "size",
                    "()I",
                    List.of(receiver)
            );

            assertEquals(InvokeType.INTERFACE, invoke.getInvokeType());
        }

        @Test
        void specialInvoke() {
            SSAValue receiver = new SSAValue(new ReferenceType("com/test/MyClass"), "this");
            InvokeInstruction invoke = new InvokeInstruction(
                    null,
                    InvokeType.SPECIAL,
                    "java/lang/Object",
                    "<init>",
                    "()V",
                    List.of(receiver)
            );

            assertEquals(InvokeType.SPECIAL, invoke.getInvokeType());
            assertNull(invoke.getResult());
        }

        @Test
        void allInvokeTypes() {
            for (InvokeType type : InvokeType.values()) {
                InvokeInstruction invoke = new InvokeInstruction(
                        type, "com/test/A", "method", "()V", Collections.emptyList()
                );
                assertEquals(type, invoke.getInvokeType());
            }
        }

        @Test
        void getOperands() {
            SSAValue arg1 = new SSAValue(PrimitiveType.INT, "arg1");
            SSAValue arg2 = new SSAValue(PrimitiveType.INT, "arg2");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC, "A", "m", "(II)V", List.of(arg1, arg2)
            );

            List<Value> operands = invoke.getOperands();
            assertEquals(2, operands.size());
        }

        @Test
        void isTerminatorFalse() {
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC, "A", "m", "()V", Collections.emptyList()
            );

            assertFalse(invoke.isTerminator());
        }

        @Test
        void visitorAccept() {
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC, "A", "m", "()V", Collections.emptyList()
            );

            String result = invoke.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitInvoke(InvokeInstruction instr) {
                    return "visited_invoke";
                }
            });

            assertEquals("visited_invoke", result);
        }

        @Test
        void toStringStatic() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            InvokeInstruction invoke = new InvokeInstruction(
                    result, InvokeType.STATIC, "java/lang/Math", "abs", "(I)I",
                    List.of(IntConstant.of(5))
            );

            String str = invoke.toString();
            assertTrue(str.contains("invokestatic") || str.toLowerCase().contains("static"));
        }
    }

    // ========== BinaryOpInstruction Tests ==========

    @Nested
    class BinaryOpInstructionTests {

        @Test
        void allBinaryOps() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "l");
            SSAValue right = new SSAValue(PrimitiveType.INT, "r2");

            for (BinaryOp op : BinaryOp.values()) {
                SSAValue result = new SSAValue(PrimitiveType.INT, "r");
                BinaryOpInstruction instr = new BinaryOpInstruction(result, op, left, right);
                assertEquals(op, instr.getOp());
                assertEquals(result, instr.getResult());
                assertEquals(left, instr.getLeft());
                assertEquals(right, instr.getRight());
            }
        }

        @Test
        void getOperands() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue left = new SSAValue(PrimitiveType.INT, "l");
            SSAValue right = new SSAValue(PrimitiveType.INT, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            List<Value> operands = instr.getOperands();
            assertEquals(2, operands.size());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            BinaryOpInstruction instr = new BinaryOpInstruction(
                    result, BinaryOp.ADD, IntConstant.of(1), IntConstant.of(2)
            );

            String visitResult = instr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitBinaryOp(BinaryOpInstruction i) {
                    return "visited_binop";
                }
            });

            assertEquals("visited_binop", visitResult);
        }
    }

    // ========== UnaryOpInstruction Tests ==========

    @Nested
    class UnaryOpInstructionTests {

        @Test
        void allUnaryOps() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");

            for (UnaryOp op : UnaryOp.values()) {
                SSAValue result = new SSAValue(PrimitiveType.INT, "r");
                UnaryOpInstruction instr = new UnaryOpInstruction(result, op, operand);
                assertEquals(op, instr.getOp());
            }
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.NEG, operand);

            String visitResult = instr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitUnaryOp(UnaryOpInstruction i) {
                    return "visited_unop";
                }
            });

            assertEquals("visited_unop", visitResult);
        }
    }

    // ========== ConstantInstruction Tests ==========

    @Nested
    class ConstantInstructionTests {

        @Test
        void intConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            ConstantInstruction instr = new ConstantInstruction(result, IntConstant.of(42));

            assertEquals(result, instr.getResult());
            assertTrue(instr.getConstant() instanceof IntConstant);
            assertEquals(42, ((IntConstant) instr.getConstant()).getValue());
        }

        @Test
        void longConstant() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            ConstantInstruction instr = new ConstantInstruction(result, LongConstant.of(100L));

            assertTrue(instr.getConstant() instanceof LongConstant);
        }

        @Test
        void nullConstant() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "r");
            ConstantInstruction instr = new ConstantInstruction(result, NullConstant.INSTANCE);

            assertTrue(instr.getConstant() instanceof NullConstant);
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            ConstantInstruction instr = new ConstantInstruction(result, IntConstant.of(1));

            String visitResult = instr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitConstant(ConstantInstruction i) {
                    return "visited_const";
                }
            });

            assertEquals("visited_const", visitResult);
        }
    }

    // ========== PhiInstruction Tests ==========

    @Nested
    class PhiInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            PhiInstruction phi = new PhiInstruction(result);

            assertEquals(result, phi.getResult());
            assertTrue(phi.getIncomingValues().isEmpty());
        }

        @Test
        void addIncoming() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            SSAValue val1 = new SSAValue(PrimitiveType.INT, "v1");
            SSAValue val2 = new SSAValue(PrimitiveType.INT, "v2");
            PhiInstruction phi = new PhiInstruction(result);

            phi.addIncoming(val1, block1);
            phi.addIncoming(val2, block2);

            assertEquals(2, phi.getIncomingValues().size());
        }

        @Test
        void getIncomingValueForBlock() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            SSAValue val1 = new SSAValue(PrimitiveType.INT, "v1");
            PhiInstruction phi = new PhiInstruction(result);
            phi.addIncoming(val1, block1);

            assertEquals(val1, phi.getIncoming(block1));
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            PhiInstruction phi = new PhiInstruction(result);

            String visitResult = phi.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitPhi(PhiInstruction i) {
                    return "visited_phi";
                }
            });

            assertEquals("visited_phi", visitResult);
        }
    }

    // ========== ReturnInstruction Tests ==========

    @Nested
    class ReturnInstructionTests {

        @Test
        void voidReturn() {
            ReturnInstruction ret = new ReturnInstruction(null);

            assertNull(ret.getReturnValue());
            assertTrue(ret.isTerminator());
        }

        @Test
        void valueReturn() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "retVal");
            ReturnInstruction ret = new ReturnInstruction(value);

            assertEquals(value, ret.getReturnValue());
        }

        @Test
        void visitorAccept() {
            ReturnInstruction ret = new ReturnInstruction(null);

            String result = ret.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitReturn(ReturnInstruction i) {
                    return "visited_return";
                }
            });

            assertEquals("visited_return", result);
        }
    }

    // ========== GotoInstruction Tests ==========

    @Nested
    class GotoInstructionTests {

        @Test
        void construction() {
            GotoInstruction gotoInstr = new GotoInstruction(block2);

            assertEquals(block2, gotoInstr.getTarget());
            assertTrue(gotoInstr.isTerminator());
        }

        @Test
        void replaceTarget() {
            GotoInstruction gotoInstr = new GotoInstruction(block1);
            gotoInstr.replaceTarget(block1, block2);

            assertEquals(block2, gotoInstr.getTarget());
        }

        @Test
        void visitorAccept() {
            GotoInstruction gotoInstr = new GotoInstruction(block1);

            String result = gotoInstr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitGoto(GotoInstruction i) {
                    return "visited_goto";
                }
            });

            assertEquals("visited_goto", result);
        }
    }

    // ========== ThrowInstruction Tests ==========

    @Nested
    class ThrowInstructionTests {

        @Test
        void construction() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            assertEquals(exception, throwInstr.getException());
            assertTrue(throwInstr.isTerminator());
        }

        @Test
        void visitorAccept() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            String result = throwInstr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitThrow(ThrowInstruction i) {
                    return "visited_throw";
                }
            });

            assertEquals("visited_throw", result);
        }
    }

    // ========== NewInstruction Tests ==========

    @Nested
    class NewInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            NewInstruction newInstr = new NewInstruction(result, "java/lang/Object");

            assertEquals(result, newInstr.getResult());
            assertEquals("java/lang/Object", newInstr.getClassName());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            NewInstruction newInstr = new NewInstruction(result, "java/lang/Object");

            String visitResult = newInstr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitNew(NewInstruction i) {
                    return "visited_new";
                }
            });

            assertEquals("visited_new", visitResult);
        }
    }

    // ========== NewArrayInstruction Tests ==========

    @Nested
    class NewArrayInstructionTests {

        @Test
        void primitiveArray() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue size = new SSAValue(PrimitiveType.INT, "size");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(size));

            assertEquals(result, newArr.getResult());
            assertEquals(PrimitiveType.INT, newArr.getElementType());
        }

        @Test
        void multidimensionalArray() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            assertEquals(2, newArr.getDimensions().size());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(IntConstant.of(10)));

            String visitResult = newArr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitNewArray(NewArrayInstruction i) {
                    return "visited_newarray";
                }
            });

            assertEquals("visited_newarray", visitResult);
        }
    }

    // ========== ArrayLoadInstruction Tests ==========

    @Nested
    class ArrayLoadInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "elem");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            ArrayLoadInstruction load = new ArrayLoadInstruction(result, array, index);

            assertEquals(result, load.getResult());
            assertEquals(array, load.getArray());
            assertEquals(index, load.getIndex());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "elem");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayLoadInstruction load = new ArrayLoadInstruction(result, array, IntConstant.of(0));

            String visitResult = load.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitArrayLoad(ArrayLoadInstruction i) {
                    return "visited_arrload";
                }
            });

            assertEquals("visited_arrload", visitResult);
        }
    }

    // ========== ArrayStoreInstruction Tests ==========

    @Nested
    class ArrayStoreInstructionTests {

        @Test
        void construction() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            assertEquals(array, store.getArray());
            assertEquals(index, store.getIndex());
            assertEquals(value, store.getValue());
        }

        @Test
        void visitorAccept() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, IntConstant.of(0), IntConstant.of(42));

            String result = store.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitArrayStore(ArrayStoreInstruction i) {
                    return "visited_arrstore";
                }
            });

            assertEquals("visited_arrstore", result);
        }
    }

    // ========== ArrayLengthInstruction Tests ==========

    @Nested
    class ArrayLengthInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "len");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayLengthInstruction len = new ArrayLengthInstruction(result, array);

            assertEquals(result, len.getResult());
            assertEquals(array, len.getArray());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "len");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayLengthInstruction len = new ArrayLengthInstruction(result, array);

            String visitResult = len.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitArrayLength(ArrayLengthInstruction i) {
                    return "visited_arrlen";
                }
            });

            assertEquals("visited_arrlen", visitResult);
        }
    }

    // ========== GetFieldInstruction Tests ==========

    @Nested
    class GetFieldInstructionTests {

        @Test
        void instanceField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            assertEquals(result, get.getResult());
            assertEquals(object, get.getObjectRef());
            assertEquals("field", get.getName());
            assertFalse(get.isStatic());
        }

        @Test
        void staticField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            assertNull(get.getObjectRef());
            assertTrue(get.isStatic());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            GetFieldInstruction get = new GetFieldInstruction(result, "A", "f", "I");

            String visitResult = get.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitGetField(GetFieldInstruction i) {
                    return "visited_getfield";
                }
            });

            assertEquals("visited_getfield", visitResult);
        }
    }

    // ========== PutFieldInstruction Tests ==========

    @Nested
    class PutFieldInstructionTests {

        @Test
        void instanceField() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            assertEquals(object, put.getObjectRef());
            assertEquals(value, put.getValue());
            assertFalse(put.isStatic());
        }

        @Test
        void staticField() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            assertNull(put.getObjectRef());
            assertTrue(put.isStatic());
        }

        @Test
        void visitorAccept() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("A", "f", "I", value);

            String result = put.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitPutField(PutFieldInstruction i) {
                    return "visited_putfield";
                }
            });

            assertEquals("visited_putfield", result);
        }
    }

    // ========== CastInstruction Tests ==========

    @Nested
    class CastInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "i");
            CastInstruction cast = new CastInstruction(result, operand, PrimitiveType.LONG);

            assertEquals(result, cast.getResult());
            assertEquals(operand, cast.getObjectRef());
            assertEquals(PrimitiveType.LONG, cast.getTargetType());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "i");
            CastInstruction cast = new CastInstruction(result, operand, PrimitiveType.LONG);

            String visitResult = cast.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitCast(CastInstruction i) {
                    return "visited_cast";
                }
            });

            assertEquals("visited_cast", visitResult);
        }
    }

    // ========== InstanceOfInstruction Tests ==========

    @Nested
    class InstanceOfInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "isInstance");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            assertEquals(result, instOf.getResult());
            assertEquals(object, instOf.getObjectRef());
            assertEquals("java/lang/String", instOf.getCheckType().toString());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "b");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            String visitResult = instOf.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitInstanceOf(InstanceOfInstruction i) {
                    return "visited_instanceof";
                }
            });

            assertEquals("visited_instanceof", visitResult);
        }
    }

    // ========== MonitorEnterInstruction Tests ==========

    @Nested
    class MonitorEnterInstructionTests {

        @Test
        void construction() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            assertEquals(object, monEnter.getObjectRef());
        }

        @Test
        void visitorAccept() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            String result = monEnter.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitMonitorEnter(MonitorEnterInstruction i) {
                    return "visited_monenter";
                }
            });

            assertEquals("visited_monenter", result);
        }
    }

    // ========== MonitorExitInstruction Tests ==========

    @Nested
    class MonitorExitInstructionTests {

        @Test
        void construction() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            assertEquals(object, monExit.getObjectRef());
        }

        @Test
        void visitorAccept() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            String result = monExit.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitMonitorExit(MonitorExitInstruction i) {
                    return "visited_monexit";
                }
            });

            assertEquals("visited_monexit", result);
        }
    }

    // ========== CopyInstruction Tests ==========

    @Nested
    class CopyInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "copy");
            SSAValue source = new SSAValue(PrimitiveType.INT, "src");
            CopyInstruction copy = new CopyInstruction(result, source);

            assertEquals(result, copy.getResult());
            assertEquals(source, copy.getSource());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "copy");
            SSAValue source = new SSAValue(PrimitiveType.INT, "src");
            CopyInstruction copy = new CopyInstruction(result, source);

            String visitResult = copy.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitCopy(CopyInstruction i) {
                    return "visited_copy";
                }
            });

            assertEquals("visited_copy", visitResult);
        }
    }

    // ========== LoadLocalInstruction Tests ==========

    @Nested
    class LoadLocalInstructionTests {

        @Test
        void construction() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "loaded");
            LoadLocalInstruction load = new LoadLocalInstruction(result, 0);

            assertEquals(result, load.getResult());
            assertEquals(0, load.getLocalIndex());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "loaded");
            LoadLocalInstruction load = new LoadLocalInstruction(result, 1);

            String visitResult = load.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitLoadLocal(LoadLocalInstruction i) {
                    return "visited_loadlocal";
                }
            });

            assertEquals("visited_loadlocal", visitResult);
        }
    }

    // ========== StoreLocalInstruction Tests ==========

    @Nested
    class StoreLocalInstructionTests {

        @Test
        void construction() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            StoreLocalInstruction store = new StoreLocalInstruction(0, value);

            assertEquals(0, store.getLocalIndex());
            assertEquals(value, store.getValue());
        }

        @Test
        void visitorAccept() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            StoreLocalInstruction store = new StoreLocalInstruction(1, value);

            String result = store.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitStoreLocal(StoreLocalInstruction i) {
                    return "visited_storelocal";
                }
            });

            assertEquals("visited_storelocal", result);
        }
    }
}
