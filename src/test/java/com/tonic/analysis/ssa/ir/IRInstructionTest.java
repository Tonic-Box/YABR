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
            mutableArgs.add(IntConstant.of(2));

            assertEquals(1, info.getBootstrapArguments().size());
        }
    }

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

            assertEquals(block3, sw.getCase(999));
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
        void dynamicInvokeWithBootstrapInfo() {
            MethodHandleConstant handle = new MethodHandleConstant(
                    MethodHandleConstant.REF_invokeStatic,
                    "java/lang/invoke/LambdaMetafactory",
                    "metafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)V"
            );
            BootstrapMethodInfo bootstrapInfo = new BootstrapMethodInfo(handle, Collections.emptyList());
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Runnable"), "lambda");

            InvokeInstruction invoke = new InvokeInstruction(
                    result,
                    InvokeType.DYNAMIC,
                    "java/lang/invoke/LambdaMetafactory",
                    "run",
                    "()Ljava/lang/Runnable;",
                    Collections.emptyList(),
                    123,
                    bootstrapInfo
            );

            assertTrue(invoke.isDynamic());
            assertTrue(invoke.hasBootstrapInfo());
            assertEquals(bootstrapInfo, invoke.getBootstrapInfo());
            assertEquals(123, invoke.getOriginalCpIndex());
        }

        @Test
        void dynamicInvokeWithoutBootstrapInfo() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InvokeInstruction invoke = new InvokeInstruction(
                    result,
                    InvokeType.DYNAMIC,
                    "com/test/Bootstrap",
                    "method",
                    "()Ljava/lang/Object;",
                    Collections.emptyList()
            );

            assertTrue(invoke.isDynamic());
            assertFalse(invoke.hasBootstrapInfo());
            assertNull(invoke.getBootstrapInfo());
        }

        @Test
        void getReceiverForStaticReturnsNull() {
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC,
                    "java/lang/Math",
                    "abs",
                    "(I)I",
                    List.of(IntConstant.of(5))
            );

            assertNull(invoke.getReceiver());
        }

        @Test
        void getReceiverForVirtualReturnsFirstArg() {
            SSAValue receiver = new SSAValue(new ReferenceType("java/lang/String"), "str");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.VIRTUAL,
                    "java/lang/String",
                    "length",
                    "()I",
                    List.of(receiver)
            );

            assertEquals(receiver, invoke.getReceiver());
        }

        @Test
        void getReceiverForEmptyArgsReturnsNull() {
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.VIRTUAL,
                    "java/lang/String",
                    "length",
                    "()I",
                    Collections.emptyList()
            );

            assertNull(invoke.getReceiver());
        }

        @Test
        void getMethodArgumentsForStaticReturnsAll() {
            SSAValue arg1 = new SSAValue(PrimitiveType.INT, "arg1");
            SSAValue arg2 = new SSAValue(PrimitiveType.INT, "arg2");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC,
                    "com/test/A",
                    "method",
                    "(II)V",
                    List.of(arg1, arg2)
            );

            List<Value> methodArgs = invoke.getMethodArguments();
            assertEquals(2, methodArgs.size());
            assertEquals(arg1, methodArgs.get(0));
            assertEquals(arg2, methodArgs.get(1));
        }

        @Test
        void getMethodArgumentsForVirtualExcludesReceiver() {
            SSAValue receiver = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue arg1 = new SSAValue(PrimitiveType.INT, "arg1");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.VIRTUAL,
                    "com/test/A",
                    "method",
                    "(I)V",
                    List.of(receiver, arg1)
            );

            List<Value> methodArgs = invoke.getMethodArguments();
            assertEquals(1, methodArgs.size());
            assertEquals(arg1, methodArgs.get(0));
        }

        @Test
        void getMethodArgumentsForVirtualOnlyReceiverReturnsEmpty() {
            SSAValue receiver = new SSAValue(new ReferenceType("com/test/A"), "obj");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.VIRTUAL,
                    "com/test/A",
                    "method",
                    "()V",
                    List.of(receiver)
            );

            List<Value> methodArgs = invoke.getMethodArguments();
            assertTrue(methodArgs.isEmpty());
        }

        @Test
        void constructorWithCpIndex() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            InvokeInstruction invoke = new InvokeInstruction(
                    result,
                    InvokeType.STATIC,
                    "A",
                    "m",
                    "()I",
                    Collections.emptyList(),
                    42
            );

            assertEquals(42, invoke.getOriginalCpIndex());
        }

        @Test
        void constructorWithoutResultWithCpIndex() {
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC,
                    "A",
                    "m",
                    "()V",
                    Collections.emptyList(),
                    99
            );

            assertEquals(99, invoke.getOriginalCpIndex());
        }

        @Test
        void replaceOperandUpdatesArgument() {
            SSAValue oldArg = new SSAValue(PrimitiveType.INT, "old");
            SSAValue newArg = new SSAValue(PrimitiveType.INT, "new");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC,
                    "A",
                    "m",
                    "(I)V",
                    new ArrayList<>(List.of(oldArg))
            );

            int oldUses = oldArg.getUseCount();
            int newUsesBefore = newArg.getUseCount();

            invoke.replaceOperand(oldArg, newArg);

            assertEquals(newArg, invoke.getArguments().get(0));
            assertEquals(oldUses - 1, oldArg.getUseCount());
            assertEquals(newUsesBefore + 1, newArg.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue oldArg = new SSAValue(PrimitiveType.INT, "old");
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.STATIC,
                    "A",
                    "m",
                    "(I)V",
                    new ArrayList<>(List.of(oldArg))
            );

            int oldUses = oldArg.getUseCount();
            IntConstant constant = IntConstant.of(42);

            invoke.replaceOperand(oldArg, constant);

            assertEquals(constant, invoke.getArguments().get(0));
            assertEquals(oldUses - 1, oldArg.getUseCount());
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

        @Test
        void toStringWithoutResult() {
            InvokeInstruction invoke = new InvokeInstruction(
                    InvokeType.VIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V",
                    List.of(new SSAValue(new ReferenceType("java/io/PrintStream"), "out"),
                            new SSAValue(new ReferenceType("java/lang/String"), "msg"))
            );

            String str = invoke.toString();
            assertTrue(str.contains("invokevirtual"));
            assertFalse(str.startsWith("null"));
        }

        @Test
        void copyWithNewOperands() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue newResult = new SSAValue(PrimitiveType.INT, "newR");
            SSAValue arg1 = new SSAValue(PrimitiveType.INT, "arg1");
            SSAValue newArg1 = new SSAValue(PrimitiveType.INT, "newArg1");

            InvokeInstruction invoke = new InvokeInstruction(
                    result, InvokeType.STATIC, "A", "m", "(I)I",
                    List.of(arg1), 5, null
            );

            IRInstruction copy = invoke.copyWithNewOperands(newResult, List.of(newArg1));

            assertNotNull(copy);
            assertTrue(copy instanceof InvokeInstruction);
            InvokeInstruction invokeCopy = (InvokeInstruction) copy;
            assertEquals(newResult, invokeCopy.getResult());
            assertEquals(newArg1, invokeCopy.getArguments().get(0));
            assertEquals(5, invokeCopy.getOriginalCpIndex());
        }
    }

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
        void instanceFieldRegistersUses() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");

            int objUsesBefore = object.getUseCount();
            int valUsesBefore = value.getUseCount();

            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            assertEquals(objUsesBefore + 1, object.getUseCount());
            assertEquals(valUsesBefore + 1, value.getUseCount());
            assertTrue(object.getUses().contains(put));
            assertTrue(value.getUses().contains(put));
        }

        @Test
        void staticFieldRegistersUse() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");

            int valUsesBefore = value.getUseCount();

            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            assertEquals(valUsesBefore + 1, value.getUseCount());
            assertTrue(value.getUses().contains(put));
        }

        @Test
        void instanceFieldWithConstantValue() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            IntConstant value = IntConstant.of(42);
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            assertEquals(value, put.getValue());
        }

        @Test
        void staticFieldWithConstantValue() {
            IntConstant value = IntConstant.of(100);
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            assertEquals(value, put.getValue());
        }

        @Test
        void getOperandsForInstanceField() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            List<Value> operands = put.getOperands();
            assertEquals(2, operands.size());
            assertEquals(object, operands.get(0));
            assertEquals(value, operands.get(1));
        }

        @Test
        void getOperandsForStaticField() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            List<Value> operands = put.getOperands();
            assertEquals(1, operands.size());
            assertEquals(value, operands.get(0));
        }

        @Test
        void replaceObjectRefOperand() {
            SSAValue oldObject = new SSAValue(new ReferenceType("com/test/A"), "old");
            SSAValue newObject = new SSAValue(new ReferenceType("com/test/A"), "new");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", oldObject, value);

            int oldUses = oldObject.getUseCount();
            int newUsesBefore = newObject.getUseCount();

            put.replaceOperand(oldObject, newObject);

            assertEquals(newObject, put.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceValueOperand() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue oldValue = new SSAValue(PrimitiveType.INT, "old");
            SSAValue newValue = new SSAValue(PrimitiveType.INT, "new");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, oldValue);

            int oldUses = oldValue.getUseCount();
            int newUsesBefore = newValue.getUseCount();

            put.replaceOperand(oldValue, newValue);

            assertEquals(newValue, put.getValue());
            assertEquals(oldUses - 1, oldValue.getUseCount());
            assertEquals(newUsesBefore + 1, newValue.getUseCount());
        }

        @Test
        void replaceOperandWithConstantObjectRef() {
            SSAValue oldObject = new SSAValue(new ReferenceType("com/test/A"), "old");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", oldObject, value);

            int oldUses = oldObject.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            put.replaceOperand(oldObject, nullVal);

            assertEquals(nullVal, put.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
        }

        @Test
        void replaceOperandWithConstantValue() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue oldValue = new SSAValue(PrimitiveType.INT, "old");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, oldValue);

            int oldUses = oldValue.getUseCount();
            IntConstant constant = IntConstant.of(99);

            put.replaceOperand(oldValue, constant);

            assertEquals(constant, put.getValue());
            assertEquals(oldUses - 1, oldValue.getUseCount());
        }

        @Test
        void replaceOperandNoMatchForStaticField() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            SSAValue other = new SSAValue(PrimitiveType.INT, "other");
            SSAValue replacement = new SSAValue(PrimitiveType.INT, "repl");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            put.replaceOperand(other, replacement);

            assertEquals(value, put.getValue());
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

        @Test
        void toStringInstanceField() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            String str = put.toString();
            assertTrue(str.contains("putfield"));
            assertTrue(str.contains("obj"));
            assertTrue(str.contains("field"));
        }

        @Test
        void toStringStaticField() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            String str = put.toString();
            assertTrue(str.contains("putstatic"));
            assertTrue(str.contains("CONST"));
        }

        @Test
        void copyWithNewOperandsStaticField() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            SSAValue newValue = new SSAValue(PrimitiveType.INT, "newVal");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            IRInstruction copy = put.copyWithNewOperands(null, List.of(newValue));

            assertNotNull(copy);
            assertTrue(copy instanceof PutFieldInstruction);
            PutFieldInstruction putCopy = (PutFieldInstruction) copy;
            assertTrue(putCopy.isStatic());
            assertEquals(newValue, putCopy.getValue());
        }

        @Test
        void copyWithNewOperandsInstanceField() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            SSAValue newObject = new SSAValue(new ReferenceType("com/test/A"), "newObj");
            SSAValue newValue = new SSAValue(PrimitiveType.INT, "newVal");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            IRInstruction copy = put.copyWithNewOperands(null, List.of(newObject, newValue));

            assertNotNull(copy);
            assertTrue(copy instanceof PutFieldInstruction);
            PutFieldInstruction putCopy = (PutFieldInstruction) copy;
            assertFalse(putCopy.isStatic());
            assertEquals(newObject, putCopy.getObjectRef());
            assertEquals(newValue, putCopy.getValue());
        }

        @Test
        void copyWithNewOperandsStaticFieldEmptyReturnsNull() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "CONST", "I", value);

            IRInstruction copy = put.copyWithNewOperands(null, Collections.emptyList());

            assertNull(copy);
        }

        @Test
        void copyWithNewOperandsInstanceFieldInsufficientReturnsNull() {
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction put = new PutFieldInstruction("com/test/A", "field", "I", object, value);

            IRInstruction copy = put.copyWithNewOperands(null, List.of(object));

            assertNull(copy);
        }
    }

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
        void instanceFieldRegistersUse() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");

            int objUsesBefore = object.getUseCount();

            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            assertEquals(objUsesBefore + 1, object.getUseCount());
            assertTrue(object.getUses().contains(get));
        }

        @Test
        void instanceFieldWithConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            NullConstant nullObj = NullConstant.INSTANCE;
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", nullObj);

            assertEquals(nullObj, get.getObjectRef());
        }

        @Test
        void getOperandsForInstanceField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            List<Value> operands = get.getOperands();
            assertEquals(1, operands.size());
            assertEquals(object, operands.get(0));
        }

        @Test
        void getOperandsForStaticField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            List<Value> operands = get.getOperands();
            assertTrue(operands.isEmpty());
        }

        @Test
        void replaceOperandWithSSAValue() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue oldObject = new SSAValue(new ReferenceType("com/test/A"), "old");
            SSAValue newObject = new SSAValue(new ReferenceType("com/test/A"), "new");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", oldObject);

            int oldUses = oldObject.getUseCount();
            int newUsesBefore = newObject.getUseCount();

            get.replaceOperand(oldObject, newObject);

            assertEquals(newObject, get.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue oldObject = new SSAValue(new ReferenceType("com/test/A"), "old");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", oldObject);

            int oldUses = oldObject.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            get.replaceOperand(oldObject, nullVal);

            assertEquals(nullVal, get.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
        }

        @Test
        void replaceOperandConstantToSSA() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            NullConstant oldObject = NullConstant.INSTANCE;
            SSAValue newObject = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", oldObject);

            int newUsesBefore = newObject.getUseCount();

            get.replaceOperand(oldObject, newObject);

            assertEquals(newObject, get.getObjectRef());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandNoMatch() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue other = new SSAValue(new ReferenceType("com/test/A"), "other");
            SSAValue replacement = new SSAValue(new ReferenceType("com/test/A"), "repl");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            get.replaceOperand(other, replacement);

            assertEquals(object, get.getObjectRef());
        }

        @Test
        void replaceOperandNoMatchForStaticField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue other = new SSAValue(new ReferenceType("com/test/A"), "other");
            SSAValue replacement = new SSAValue(new ReferenceType("com/test/A"), "repl");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            get.replaceOperand(other, replacement);

            assertNull(get.getObjectRef());
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

        @Test
        void toStringInstanceField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            String str = get.toString();
            assertTrue(str.contains("getfield"));
            assertTrue(str.contains("val"));
            assertTrue(str.contains("obj"));
            assertTrue(str.contains("field"));
        }

        @Test
        void toStringStaticField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            String str = get.toString();
            assertTrue(str.contains("getstatic"));
            assertTrue(str.contains("CONST"));
        }

        @Test
        void copyWithNewOperandsStaticField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue newResult = new SSAValue(PrimitiveType.INT, "newVal");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            IRInstruction copy = get.copyWithNewOperands(newResult, Collections.emptyList());

            assertNotNull(copy);
            assertTrue(copy instanceof GetFieldInstruction);
            GetFieldInstruction getCopy = (GetFieldInstruction) copy;
            assertTrue(getCopy.isStatic());
            assertEquals(newResult, getCopy.getResult());
        }

        @Test
        void copyWithNewOperandsInstanceField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue newResult = new SSAValue(PrimitiveType.INT, "newVal");
            SSAValue newObject = new SSAValue(new ReferenceType("com/test/A"), "newObj");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            IRInstruction copy = get.copyWithNewOperands(newResult, List.of(newObject));

            assertNotNull(copy);
            assertTrue(copy instanceof GetFieldInstruction);
            GetFieldInstruction getCopy = (GetFieldInstruction) copy;
            assertFalse(getCopy.isStatic());
            assertEquals(newResult, getCopy.getResult());
            assertEquals(newObject, getCopy.getObjectRef());
        }

        @Test
        void copyWithNewOperandsInstanceFieldEmptyReturnsNull() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "val");
            SSAValue object = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction get = new GetFieldInstruction(result, "com/test/A", "field", "I", object);

            IRInstruction copy = get.copyWithNewOperands(result, Collections.emptyList());

            assertNull(copy);
        }
    }

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
        void primitiveArraySingleDimensionConstructor() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue size = new SSAValue(PrimitiveType.INT, "size");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, size);

            assertEquals(result, newArr.getResult());
            assertEquals(PrimitiveType.INT, newArr.getElementType());
            assertEquals(1, newArr.getDimensions().size());
            assertFalse(newArr.isMultiDimensional());
        }

        @Test
        void referenceArray() {
            SSAValue result = new SSAValue(new ArrayType(new ReferenceType("java/lang/String"), 1), "arr");
            SSAValue size = new SSAValue(PrimitiveType.INT, "size");
            NewArrayInstruction newArr = new NewArrayInstruction(result, new ReferenceType("java/lang/String"), size);

            assertEquals("java/lang/String", newArr.getElementType().toString());
        }

        @Test
        void multidimensionalArray() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            assertEquals(2, newArr.getDimensions().size());
            assertTrue(newArr.isMultiDimensional());
        }

        @Test
        void threeDimensionalArray() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.DOUBLE, 3), "arr");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            SSAValue dim3 = new SSAValue(PrimitiveType.INT, "d3");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.DOUBLE, List.of(dim1, dim2, dim3));

            assertEquals(3, newArr.getDimensions().size());
            assertTrue(newArr.isMultiDimensional());
        }

        @Test
        void singleDimensionRegistersUse() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue size = new SSAValue(PrimitiveType.INT, "size");

            int sizeUsesBefore = size.getUseCount();

            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, size);

            assertEquals(sizeUsesBefore + 1, size.getUseCount());
            assertTrue(size.getUses().contains(newArr));
        }

        @Test
        void multiDimensionRegistersUses() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");

            int dim1UsesBefore = dim1.getUseCount();
            int dim2UsesBefore = dim2.getUseCount();

            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            assertEquals(dim1UsesBefore + 1, dim1.getUseCount());
            assertEquals(dim2UsesBefore + 1, dim2.getUseCount());
            assertTrue(dim1.getUses().contains(newArr));
            assertTrue(dim2.getUses().contains(newArr));
        }

        @Test
        void singleDimensionWithConstant() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            IntConstant size = IntConstant.of(10);
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, size);

            assertEquals(1, newArr.getDimensions().size());
            assertEquals(size, newArr.getDimensions().get(0));
        }

        @Test
        void multiDimensionWithConstants() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            IntConstant dim1 = IntConstant.of(5);
            IntConstant dim2 = IntConstant.of(10);
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            assertEquals(2, newArr.getDimensions().size());
            assertEquals(dim1, newArr.getDimensions().get(0));
            assertEquals(dim2, newArr.getDimensions().get(1));
        }

        @Test
        void getOperands() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            List<Value> operands = newArr.getOperands();
            assertEquals(2, operands.size());
            assertEquals(dim1, operands.get(0));
            assertEquals(dim2, operands.get(1));
        }

        @Test
        void replaceOperandFirstDimension() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            SSAValue oldDim1 = new SSAValue(PrimitiveType.INT, "old1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            SSAValue newDim1 = new SSAValue(PrimitiveType.INT, "new1");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(oldDim1, dim2));

            int oldUses = oldDim1.getUseCount();
            int newUsesBefore = newDim1.getUseCount();

            newArr.replaceOperand(oldDim1, newDim1);

            assertEquals(newDim1, newArr.getDimensions().get(0));
            assertEquals(oldUses - 1, oldDim1.getUseCount());
            assertEquals(newUsesBefore + 1, newDim1.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue oldSize = new SSAValue(PrimitiveType.INT, "old");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, oldSize);

            int oldUses = oldSize.getUseCount();
            IntConstant constant = IntConstant.of(20);

            newArr.replaceOperand(oldSize, constant);

            assertEquals(constant, newArr.getDimensions().get(0));
            assertEquals(oldUses - 1, oldSize.getUseCount());
        }

        @Test
        void visitorAccept() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, IntConstant.of(10));

            String visitResult = newArr.accept(new AbstractIRVisitor<String>() {
                @Override
                public String visitNewArray(NewArrayInstruction i) {
                    return "visited_newarray";
                }
            });

            assertEquals("visited_newarray", visitResult);
        }

        @Test
        void toStringFormat() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "arr");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            NewArrayInstruction newArr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            String str = newArr.toString();
            assertTrue(str.contains("arr"));
            assertTrue(str.contains("newarray"));
            assertTrue(str.contains("["));
            assertTrue(str.contains("d1"));
            assertTrue(str.contains("d2"));
        }
    }

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
        void constructionRegistersUses() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");

            int arrayUsesBefore = array.getUseCount();
            int indexUsesBefore = index.getUseCount();
            int valueUsesBefore = value.getUseCount();

            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            assertEquals(arrayUsesBefore + 1, array.getUseCount());
            assertEquals(indexUsesBefore + 1, index.getUseCount());
            assertEquals(valueUsesBefore + 1, value.getUseCount());
            assertTrue(array.getUses().contains(store));
            assertTrue(index.getUses().contains(store));
            assertTrue(value.getUses().contains(store));
        }

        @Test
        void constructionWithConstantIndex() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            IntConstant index = IntConstant.of(5);
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            assertEquals(index, store.getIndex());
        }

        @Test
        void constructionWithConstantValue() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            IntConstant value = IntConstant.of(42);
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            assertEquals(value, store.getValue());
        }

        @Test
        void constructionWithAllConstants() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            IntConstant index = IntConstant.of(0);
            IntConstant value = IntConstant.of(100);
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            assertEquals(index, store.getIndex());
            assertEquals(value, store.getValue());
        }

        @Test
        void getOperands() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            List<Value> operands = store.getOperands();
            assertEquals(3, operands.size());
            assertEquals(array, operands.get(0));
            assertEquals(index, operands.get(1));
            assertEquals(value, operands.get(2));
        }

        @Test
        void replaceArrayOperand() {
            SSAValue oldArray = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "old");
            SSAValue newArray = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "new");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(oldArray, index, value);

            int oldUses = oldArray.getUseCount();
            int newUsesBefore = newArray.getUseCount();

            store.replaceOperand(oldArray, newArray);

            assertEquals(newArray, store.getArray());
            assertEquals(oldUses - 1, oldArray.getUseCount());
            assertEquals(newUsesBefore + 1, newArray.getUseCount());
        }

        @Test
        void replaceIndexOperand() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue oldIndex = new SSAValue(PrimitiveType.INT, "old");
            SSAValue newIndex = new SSAValue(PrimitiveType.INT, "new");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, oldIndex, value);

            int oldUses = oldIndex.getUseCount();
            int newUsesBefore = newIndex.getUseCount();

            store.replaceOperand(oldIndex, newIndex);

            assertEquals(newIndex, store.getIndex());
            assertEquals(oldUses - 1, oldIndex.getUseCount());
            assertEquals(newUsesBefore + 1, newIndex.getUseCount());
        }

        @Test
        void replaceValueOperand() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue oldValue = new SSAValue(PrimitiveType.INT, "old");
            SSAValue newValue = new SSAValue(PrimitiveType.INT, "new");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, oldValue);

            int oldUses = oldValue.getUseCount();
            int newUsesBefore = newValue.getUseCount();

            store.replaceOperand(oldValue, newValue);

            assertEquals(newValue, store.getValue());
            assertEquals(oldUses - 1, oldValue.getUseCount());
            assertEquals(newUsesBefore + 1, newValue.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue oldIndex = new SSAValue(PrimitiveType.INT, "old");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, oldIndex, value);

            int oldUses = oldIndex.getUseCount();
            IntConstant constant = IntConstant.of(7);

            store.replaceOperand(oldIndex, constant);

            assertEquals(constant, store.getIndex());
            assertEquals(oldUses - 1, oldIndex.getUseCount());
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

        @Test
        void toStringFormat() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction store = new ArrayStoreInstruction(array, index, value);

            String str = store.toString();
            assertTrue(str.contains("arr"));
            assertTrue(str.contains("["));
            assertTrue(str.contains("idx"));
            assertTrue(str.contains("="));
            assertTrue(str.contains("val"));
        }
    }

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
        void constructionRegistersUse() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");

            int usesBefore = exception.getUseCount();
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            assertEquals(usesBefore + 1, exception.getUseCount());
            assertTrue(exception.getUses().contains(throwInstr));
        }

        @Test
        void constructionWithConstant() {
            NullConstant nullVal = NullConstant.INSTANCE;
            ThrowInstruction throwInstr = new ThrowInstruction(nullVal);

            assertEquals(nullVal, throwInstr.getException());
        }

        @Test
        void throwRuntimeException() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/RuntimeException"), "rte");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            assertEquals(exception, throwInstr.getException());
        }

        @Test
        void throwThrowable() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Throwable"), "t");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            assertEquals(exception, throwInstr.getException());
        }

        @Test
        void getOperands() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            List<Value> operands = throwInstr.getOperands();
            assertEquals(1, operands.size());
            assertEquals(exception, operands.get(0));
        }

        @Test
        void replaceOperandWithSSAValue() {
            SSAValue oldException = new SSAValue(new ReferenceType("java/lang/Exception"), "old");
            SSAValue newException = new SSAValue(new ReferenceType("java/lang/RuntimeException"), "new");
            ThrowInstruction throwInstr = new ThrowInstruction(oldException);

            int oldUses = oldException.getUseCount();
            int newUsesBefore = newException.getUseCount();

            throwInstr.replaceOperand(oldException, newException);

            assertEquals(newException, throwInstr.getException());
            assertEquals(oldUses - 1, oldException.getUseCount());
            assertEquals(newUsesBefore + 1, newException.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue oldException = new SSAValue(new ReferenceType("java/lang/Exception"), "old");
            ThrowInstruction throwInstr = new ThrowInstruction(oldException);

            int oldUses = oldException.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            throwInstr.replaceOperand(oldException, nullVal);

            assertEquals(nullVal, throwInstr.getException());
            assertEquals(oldUses - 1, oldException.getUseCount());
        }

        @Test
        void replaceOperandConstantToSSA() {
            NullConstant oldException = NullConstant.INSTANCE;
            SSAValue newException = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction throwInstr = new ThrowInstruction(oldException);

            int newUsesBefore = newException.getUseCount();

            throwInstr.replaceOperand(oldException, newException);

            assertEquals(newException, throwInstr.getException());
            assertEquals(newUsesBefore + 1, newException.getUseCount());
        }

        @Test
        void replaceOperandNoMatch() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            SSAValue other = new SSAValue(new ReferenceType("java/lang/Exception"), "other");
            SSAValue replacement = new SSAValue(new ReferenceType("java/lang/Exception"), "repl");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            throwInstr.replaceOperand(other, replacement);

            assertEquals(exception, throwInstr.getException());
        }

        @Test
        void isTerminator() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

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

        @Test
        void toStringFormat() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction throwInstr = new ThrowInstruction(exception);

            String str = throwInstr.toString();
            assertTrue(str.contains("throw"));
            assertTrue(str.contains("ex"));
        }
    }

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
        void constructionRegistersUse() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "i");
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "obj");

            int usesBefore = operand.getUseCount();
            CastInstruction cast = new CastInstruction(result, operand, new ReferenceType("java/lang/String"));

            assertEquals(usesBefore + 1, operand.getUseCount());
            assertTrue(operand.getUses().contains(cast));
        }

        @Test
        void constructionWithConstant() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/String"), "str");
            NullConstant nullVal = NullConstant.INSTANCE;
            CastInstruction cast = new CastInstruction(result, nullVal, new ReferenceType("java/lang/String"));

            assertEquals(nullVal, cast.getObjectRef());
        }

        @Test
        void primitiveCast() {
            SSAValue result = new SSAValue(PrimitiveType.DOUBLE, "d");
            SSAValue operand = new SSAValue(PrimitiveType.FLOAT, "f");
            CastInstruction cast = new CastInstruction(result, operand, PrimitiveType.DOUBLE);

            assertEquals(PrimitiveType.DOUBLE, cast.getTargetType());
        }

        @Test
        void referenceCast() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/String"), "str");
            SSAValue operand = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            CastInstruction cast = new CastInstruction(result, operand, new ReferenceType("java/lang/String"));

            assertEquals("java/lang/String", cast.getTargetType().toString());
        }

        @Test
        void getOperands() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "i");
            CastInstruction cast = new CastInstruction(result, operand, PrimitiveType.LONG);

            List<Value> operands = cast.getOperands();
            assertEquals(1, operands.size());
            assertEquals(operand, operands.get(0));
        }

        @Test
        void replaceOperandWithSSAValue() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue oldOperand = new SSAValue(PrimitiveType.INT, "old");
            SSAValue newOperand = new SSAValue(PrimitiveType.INT, "new");
            CastInstruction cast = new CastInstruction(result, oldOperand, PrimitiveType.LONG);

            int oldUses = oldOperand.getUseCount();
            int newUsesBefore = newOperand.getUseCount();

            cast.replaceOperand(oldOperand, newOperand);

            assertEquals(newOperand, cast.getObjectRef());
            assertEquals(oldUses - 1, oldOperand.getUseCount());
            assertEquals(newUsesBefore + 1, newOperand.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/String"), "str");
            SSAValue oldOperand = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            CastInstruction cast = new CastInstruction(result, oldOperand, new ReferenceType("java/lang/String"));

            int oldUses = oldOperand.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            cast.replaceOperand(oldOperand, nullVal);

            assertEquals(nullVal, cast.getObjectRef());
            assertEquals(oldUses - 1, oldOperand.getUseCount());
        }

        @Test
        void replaceOperandConstantToSSA() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/String"), "str");
            NullConstant oldOperand = NullConstant.INSTANCE;
            SSAValue newOperand = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            CastInstruction cast = new CastInstruction(result, oldOperand, new ReferenceType("java/lang/String"));

            int newUsesBefore = newOperand.getUseCount();

            cast.replaceOperand(oldOperand, newOperand);

            assertEquals(newOperand, cast.getObjectRef());
            assertEquals(newUsesBefore + 1, newOperand.getUseCount());
        }

        @Test
        void replaceOperandNoMatch() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "i");
            SSAValue other = new SSAValue(PrimitiveType.INT, "other");
            SSAValue replacement = new SSAValue(PrimitiveType.INT, "repl");
            CastInstruction cast = new CastInstruction(result, operand, PrimitiveType.LONG);

            cast.replaceOperand(other, replacement);

            assertEquals(operand, cast.getObjectRef());
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

        @Test
        void toStringFormat() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/String"), "str");
            SSAValue operand = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            CastInstruction cast = new CastInstruction(result, operand, new ReferenceType("java/lang/String"));

            String str = cast.toString();
            assertTrue(str.contains("str"));
            assertTrue(str.contains("obj"));
            assertTrue(str.contains("java/lang/String"));
        }
    }

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
        void constructionRegistersUse() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");

            int usesBefore = object.getUseCount();
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            assertEquals(usesBefore + 1, object.getUseCount());
            assertTrue(object.getUses().contains(instOf));
        }

        @Test
        void constructionWithConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            NullConstant nullVal = NullConstant.INSTANCE;
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, nullVal, new ReferenceType("java/lang/String"));

            assertEquals(nullVal, instOf.getObjectRef());
        }

        @Test
        void checkDifferentTypes() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/util/List"));

            assertEquals("java/util/List", instOf.getCheckType().toString());
        }

        @Test
        void getOperands() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            List<Value> operands = instOf.getOperands();
            assertEquals(1, operands.size());
            assertEquals(object, operands.get(0));
        }

        @Test
        void replaceOperandWithSSAValue() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            SSAValue oldObject = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            SSAValue newObject = new SSAValue(new ReferenceType("java/lang/Object"), "new");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, oldObject, new ReferenceType("java/lang/String"));

            int oldUses = oldObject.getUseCount();
            int newUsesBefore = newObject.getUseCount();

            instOf.replaceOperand(oldObject, newObject);

            assertEquals(newObject, instOf.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            SSAValue oldObject = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, oldObject, new ReferenceType("java/lang/String"));

            int oldUses = oldObject.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            instOf.replaceOperand(oldObject, nullVal);

            assertEquals(nullVal, instOf.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
        }

        @Test
        void replaceOperandConstantToSSA() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            NullConstant oldObject = NullConstant.INSTANCE;
            SSAValue newObject = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, oldObject, new ReferenceType("java/lang/String"));

            int newUsesBefore = newObject.getUseCount();

            instOf.replaceOperand(oldObject, newObject);

            assertEquals(newObject, instOf.getObjectRef());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandNoMatch() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "bool");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            SSAValue other = new SSAValue(new ReferenceType("java/lang/Object"), "other");
            SSAValue replacement = new SSAValue(new ReferenceType("java/lang/Object"), "repl");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            instOf.replaceOperand(other, replacement);

            assertEquals(object, instOf.getObjectRef());
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

        @Test
        void toStringFormat() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "isStr");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instOf = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            String str = instOf.toString();
            assertTrue(str.contains("isStr"));
            assertTrue(str.contains("obj"));
            assertTrue(str.contains("instanceof"));
            assertTrue(str.contains("java/lang/String"));
        }
    }

    @Nested
    class MonitorEnterInstructionTests {

        @Test
        void construction() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            assertEquals(object, monEnter.getObjectRef());
        }

        @Test
        void constructionRegistersUse() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");

            int usesBefore = object.getUseCount();
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            assertEquals(usesBefore + 1, object.getUseCount());
            assertTrue(object.getUses().contains(monEnter));
        }

        @Test
        void constructionWithConstant() {
            NullConstant nullVal = NullConstant.INSTANCE;
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(nullVal);

            assertEquals(nullVal, monEnter.getObjectRef());
        }

        @Test
        void getOperands() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            List<Value> operands = monEnter.getOperands();
            assertEquals(1, operands.size());
            assertEquals(object, operands.get(0));
        }

        @Test
        void replaceOperandWithSSAValue() {
            SSAValue oldObject = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            SSAValue newObject = new SSAValue(new ReferenceType("java/lang/Object"), "new");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(oldObject);

            int oldUses = oldObject.getUseCount();
            int newUsesBefore = newObject.getUseCount();

            monEnter.replaceOperand(oldObject, newObject);

            assertEquals(newObject, monEnter.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue oldObject = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(oldObject);

            int oldUses = oldObject.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            monEnter.replaceOperand(oldObject, nullVal);

            assertEquals(nullVal, monEnter.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
        }

        @Test
        void replaceOperandConstantToSSA() {
            NullConstant oldObject = NullConstant.INSTANCE;
            SSAValue newObject = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(oldObject);

            int newUsesBefore = newObject.getUseCount();

            monEnter.replaceOperand(oldObject, newObject);

            assertEquals(newObject, monEnter.getObjectRef());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandNoMatch() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            SSAValue other = new SSAValue(new ReferenceType("java/lang/Object"), "other");
            SSAValue replacement = new SSAValue(new ReferenceType("java/lang/Object"), "repl");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            monEnter.replaceOperand(other, replacement);

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

        @Test
        void toStringFormat() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            String str = monEnter.toString();
            assertTrue(str.contains("monitorenter"));
            assertTrue(str.contains("lock"));
        }

        @Test
        void isNotTerminator() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction monEnter = new MonitorEnterInstruction(object);

            assertFalse(monEnter.isTerminator());
        }
    }

    @Nested
    class MonitorExitInstructionTests {

        @Test
        void construction() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            assertEquals(object, monExit.getObjectRef());
        }

        @Test
        void constructionRegistersUse() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");

            int usesBefore = object.getUseCount();
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            assertEquals(usesBefore + 1, object.getUseCount());
            assertTrue(object.getUses().contains(monExit));
        }

        @Test
        void constructionWithConstant() {
            NullConstant nullVal = NullConstant.INSTANCE;
            MonitorExitInstruction monExit = new MonitorExitInstruction(nullVal);

            assertEquals(nullVal, monExit.getObjectRef());
        }

        @Test
        void getOperands() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            List<Value> operands = monExit.getOperands();
            assertEquals(1, operands.size());
            assertEquals(object, operands.get(0));
        }

        @Test
        void replaceOperandWithSSAValue() {
            SSAValue oldObject = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            SSAValue newObject = new SSAValue(new ReferenceType("java/lang/Object"), "new");
            MonitorExitInstruction monExit = new MonitorExitInstruction(oldObject);

            int oldUses = oldObject.getUseCount();
            int newUsesBefore = newObject.getUseCount();

            monExit.replaceOperand(oldObject, newObject);

            assertEquals(newObject, monExit.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandWithConstant() {
            SSAValue oldObject = new SSAValue(new ReferenceType("java/lang/Object"), "old");
            MonitorExitInstruction monExit = new MonitorExitInstruction(oldObject);

            int oldUses = oldObject.getUseCount();
            NullConstant nullVal = NullConstant.INSTANCE;

            monExit.replaceOperand(oldObject, nullVal);

            assertEquals(nullVal, monExit.getObjectRef());
            assertEquals(oldUses - 1, oldObject.getUseCount());
        }

        @Test
        void replaceOperandConstantToSSA() {
            NullConstant oldObject = NullConstant.INSTANCE;
            SSAValue newObject = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            MonitorExitInstruction monExit = new MonitorExitInstruction(oldObject);

            int newUsesBefore = newObject.getUseCount();

            monExit.replaceOperand(oldObject, newObject);

            assertEquals(newObject, monExit.getObjectRef());
            assertEquals(newUsesBefore + 1, newObject.getUseCount());
        }

        @Test
        void replaceOperandNoMatch() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            SSAValue other = new SSAValue(new ReferenceType("java/lang/Object"), "other");
            SSAValue replacement = new SSAValue(new ReferenceType("java/lang/Object"), "repl");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            monExit.replaceOperand(other, replacement);

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

        @Test
        void toStringFormat() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            String str = monExit.toString();
            assertTrue(str.contains("monitorexit"));
            assertTrue(str.contains("lock"));
        }

        @Test
        void isNotTerminator() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction monExit = new MonitorExitInstruction(object);

            assertFalse(monExit.isTerminator());
        }
    }

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
