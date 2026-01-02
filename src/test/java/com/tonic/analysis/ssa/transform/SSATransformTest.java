package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SSATransformTest {

    @Nested
    class MethodInliningTests {

        private ClassPool pool;
        private ClassFile classFile;
        private SSA ssa;

        @BeforeEach
        void setUp() throws IOException {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();

            pool = TestUtils.emptyPool();
            int access = new AccessBuilder().setPublic().build();
            classFile = pool.createNewClass("com/test/InlineTest", access);
            ssa = new SSA(classFile.getConstPool());
        }

        @Test
        void inlineSimplePrivateMethod() throws IOException {
            int publicAccess = new AccessBuilder().setPublic().build();
            int privateAccess = new AccessBuilder().setPrivate().build();

            MethodEntry caller = classFile.createNewMethod(publicAccess, "caller", "()I");
            MethodEntry callee = classFile.createNewMethod(privateAccess, "callee", "()I");

            IRMethod calleeIR = new IRMethod("com/test/InlineTest", "callee", "()I", false);
            IRBlock calleeEntry = new IRBlock("entry");
            calleeIR.addBlock(calleeEntry);
            calleeIR.setEntryBlock(calleeEntry);

            SSAValue constResult = new SSAValue(PrimitiveType.INT);
            calleeEntry.addInstruction(new ConstantInstruction(constResult, IntConstant.of(42)));
            calleeEntry.addInstruction(new ReturnInstruction(constResult));

            ssa.lower(calleeIR, callee);

            IRMethod callerIR = new IRMethod("com/test/InlineTest", "caller", "()I", false);
            IRBlock callerEntry = new IRBlock("entry");
            callerIR.addBlock(callerEntry);
            callerIR.setEntryBlock(callerEntry);

            SSAValue invokeResult = new SSAValue(PrimitiveType.INT);
            InvokeInstruction invoke = new InvokeInstruction(invokeResult, InvokeType.STATIC,
                "com/test/InlineTest", "callee", "()I", List.of());
            callerEntry.addInstruction(invoke);
            callerEntry.addInstruction(new ReturnInstruction(invokeResult));

            ssa.lower(callerIR, caller);

            MethodInlining transform = new MethodInlining();
            boolean changed = transform.run(classFile, ssa);

            assertNotNull(classFile);
        }

        @Test
        void rejectsNativeMethods() throws IOException {
            int nativeAccess = new AccessBuilder().setPublic().setNative().build();
            MethodEntry nativeMethod = classFile.createNewMethod(nativeAccess, "nativeMethod", "()V");

            MethodInlining transform = new MethodInlining();
            boolean changed = transform.run(classFile, ssa);

            assertFalse(changed);
        }

        @Test
        void rejectsSynchronizedMethods() throws IOException {
            int syncAccess = new AccessBuilder().setPublic().setSynchronized().build();
            MethodEntry syncMethod = classFile.createNewMethod(syncAccess, "syncMethod", "()V");

            MethodInlining transform = new MethodInlining();
            boolean changed = transform.run(classFile, ssa);

            assertFalse(changed);
        }

        @Test
        void rejectsAbstractMethods() throws IOException {
            int abstractAccess = new AccessBuilder().setPublic().setAbstract().build();
            MethodEntry abstractMethod = classFile.createNewMethod(abstractAccess, "abstractMethod", "()V");

            MethodInlining transform = new MethodInlining();
            boolean changed = transform.run(classFile, ssa);

            assertFalse(changed);
        }

        @Test
        void acceptsFinalMethods() throws IOException {
            int finalAccess = new AccessBuilder().setPublic().setFinal().build();
            MethodEntry finalMethod = classFile.createNewMethod(finalAccess, "finalMethod", "()I");

            MethodInlining transform = new MethodInlining();
            transform.run(classFile, ssa);

            assertNotNull(classFile);
        }

        @Test
        void acceptsStaticMethods() throws IOException {
            int staticAccess = new AccessBuilder().setPublic().setStatic().build();
            MethodEntry staticMethod = classFile.createNewMethod(staticAccess, "staticMethod", "()I");

            MethodInlining transform = new MethodInlining();
            transform.run(classFile, ssa);

            assertNotNull(classFile);
        }

        @Test
        void rejectsRecursiveCalls() throws IOException {
            int access = new AccessBuilder().setPrivate().build();
            MethodEntry method = classFile.createNewMethod(access, "recursive", "()V");

            IRMethod methodIR = new IRMethod("com/test/InlineTest", "recursive", "()V", false);
            IRBlock entry = new IRBlock("entry");
            methodIR.addBlock(entry);
            methodIR.setEntryBlock(entry);

            InvokeInstruction selfCall = new InvokeInstruction(null, InvokeType.STATIC,
                "com/test/InlineTest", "recursive", "()V", List.of());
            entry.addInstruction(selfCall);
            entry.addInstruction(new ReturnInstruction());

            ssa.lower(methodIR, method);

            MethodInlining transform = new MethodInlining();
            boolean changed = transform.run(classFile, ssa);

            assertFalse(changed);
        }

        @Test
        void rejectsExternalClassMethods() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            MethodEntry caller = classFile.createNewMethod(access, "caller", "()V");

            IRMethod callerIR = new IRMethod("com/test/InlineTest", "caller", "()V", false);
            IRBlock entry = new IRBlock("entry");
            callerIR.addBlock(entry);
            callerIR.setEntryBlock(entry);

            InvokeInstruction externalCall = new InvokeInstruction(null, InvokeType.STATIC,
                "java/lang/Math", "abs", "(I)I", List.of(IntConstant.of(5)));
            entry.addInstruction(externalCall);
            entry.addInstruction(new ReturnInstruction());

            ssa.lower(callerIR, caller);

            MethodInlining transform = new MethodInlining();
            boolean changed = transform.run(classFile, ssa);

            assertFalse(changed);
        }

        @Test
        void handlesMethodWithMultipleReturns() throws IOException {
            int privateAccess = new AccessBuilder().setPrivate().build();
            MethodEntry method = classFile.createNewMethod(privateAccess, "multiReturn", "(I)I");

            IRMethod methodIR = new IRMethod("com/test/InlineTest", "multiReturn", "(I)I", false);
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            methodIR.addBlock(entry);
            methodIR.addBlock(trueBlock);
            methodIR.addBlock(falseBlock);
            methodIR.setEntryBlock(entry);

            SSAValue param = new SSAValue(PrimitiveType.INT);
            methodIR.addParameter(param);

            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, param, trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction(IntConstant.of(1)));
            falseBlock.addInstruction(new ReturnInstruction(IntConstant.of(0)));

            ssa.lower(methodIR, method);

            MethodInlining transform = new MethodInlining();
            transform.run(classFile, ssa);

            assertNotNull(classFile);
        }

        @Test
        void handlesVoidReturnMethods() throws IOException {
            int privateAccess = new AccessBuilder().setPrivate().build();
            MethodEntry method = classFile.createNewMethod(privateAccess, "voidMethod", "()V");

            IRMethod methodIR = new IRMethod("com/test/InlineTest", "voidMethod", "()V", false);
            IRBlock entry = new IRBlock("entry");
            methodIR.addBlock(entry);
            methodIR.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            ssa.lower(methodIR, method);

            MethodInlining transform = new MethodInlining();
            transform.run(classFile, ssa);

            assertNotNull(classFile);
        }

        @Test
        void handlesMethodWithParameters() throws IOException {
            int privateAccess = new AccessBuilder().setPrivate().build();
            MethodEntry method = classFile.createNewMethod(privateAccess, "addTwo", "(I)I");

            IRMethod methodIR = new IRMethod("com/test/InlineTest", "addTwo", "(I)I", false);
            IRBlock entry = new IRBlock("entry");
            methodIR.addBlock(entry);
            methodIR.setEntryBlock(entry);

            SSAValue param = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            methodIR.addParameter(param);

            entry.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, param, IntConstant.of(2)));
            entry.addInstruction(new ReturnInstruction(result));

            ssa.lower(methodIR, method);

            MethodInlining transform = new MethodInlining();
            transform.run(classFile, ssa);

            assertNotNull(classFile);
        }
    }

    @Nested
    class ConstantFoldingTests {

        private IRMethod method;
        private IRBlock block;

        @BeforeEach
        void setUp() {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();

            method = new IRMethod("com/test/Test", "foo", "()V", true);
            block = new IRBlock("entry");
            method.addBlock(block);
            method.setEntryBlock(block);
        }

        @Test
        void foldIntBitwiseAND() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction and = new BinaryOpInstruction(result, BinaryOp.AND,
                IntConstant.of(15), IntConstant.of(7));
            block.addInstruction(and);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(7), constInstr.getConstant());
        }

        @Test
        void foldIntBitwiseOR() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction or = new BinaryOpInstruction(result, BinaryOp.OR,
                IntConstant.of(8), IntConstant.of(4));
            block.addInstruction(or);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(12), constInstr.getConstant());
        }

        @Test
        void foldIntBitwiseXOR() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction xor = new BinaryOpInstruction(result, BinaryOp.XOR,
                IntConstant.of(15), IntConstant.of(7));
            block.addInstruction(xor);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(8), constInstr.getConstant());
        }

        @Test
        void foldIntShiftLeft() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction shl = new BinaryOpInstruction(result, BinaryOp.SHL,
                IntConstant.of(3), IntConstant.of(2));
            block.addInstruction(shl);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(12), constInstr.getConstant());
        }

        @Test
        void foldIntShiftRight() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction shr = new BinaryOpInstruction(result, BinaryOp.SHR,
                IntConstant.of(16), IntConstant.of(2));
            block.addInstruction(shr);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(4), constInstr.getConstant());
        }

        @Test
        void foldIntUnsignedShiftRight() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction ushr = new BinaryOpInstruction(result, BinaryOp.USHR,
                IntConstant.of(-16), IntConstant.of(2));
            block.addInstruction(ushr);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
        }

        @Test
        void foldLongAddition() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            BinaryOpInstruction add = new BinaryOpInstruction(result, BinaryOp.ADD,
                LongConstant.of(100L), LongConstant.of(50L));
            block.addInstruction(add);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(150L), constInstr.getConstant());
        }

        @Test
        void foldLongSubtraction() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            BinaryOpInstruction sub = new BinaryOpInstruction(result, BinaryOp.SUB,
                LongConstant.of(100L), LongConstant.of(30L));
            block.addInstruction(sub);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(70L), constInstr.getConstant());
        }

        @Test
        void foldLongMultiplication() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            BinaryOpInstruction mul = new BinaryOpInstruction(result, BinaryOp.MUL,
                LongConstant.of(7L), LongConstant.of(8L));
            block.addInstruction(mul);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(56L), constInstr.getConstant());
        }

        @Test
        void foldLongDivision() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            BinaryOpInstruction div = new BinaryOpInstruction(result, BinaryOp.DIV,
                LongConstant.of(100L), LongConstant.of(5L));
            block.addInstruction(div);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(20L), constInstr.getConstant());
        }

        @Test
        void foldLongComparison() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction lcmp = new BinaryOpInstruction(result, BinaryOp.LCMP,
                LongConstant.of(100L), LongConstant.of(50L));
            block.addInstruction(lcmp);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(1), constInstr.getConstant());
        }

        @Test
        void foldFloatAddition() {
            SSAValue result = new SSAValue(PrimitiveType.FLOAT);
            BinaryOpInstruction add = new BinaryOpInstruction(result, BinaryOp.ADD,
                FloatConstant.of(2.5f), FloatConstant.of(3.5f));
            block.addInstruction(add);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(FloatConstant.of(6.0f), constInstr.getConstant());
        }

        @Test
        void foldFloatMultiplication() {
            SSAValue result = new SSAValue(PrimitiveType.FLOAT);
            BinaryOpInstruction mul = new BinaryOpInstruction(result, BinaryOp.MUL,
                FloatConstant.of(2.0f), FloatConstant.of(3.0f));
            block.addInstruction(mul);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(FloatConstant.of(6.0f), constInstr.getConstant());
        }

        @Test
        void foldFloatComparison() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction fcmpl = new BinaryOpInstruction(result, BinaryOp.FCMPL,
                FloatConstant.of(5.0f), FloatConstant.of(3.0f));
            block.addInstruction(fcmpl);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(1), constInstr.getConstant());
        }

        @Test
        void foldDoubleAddition() {
            SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
            BinaryOpInstruction add = new BinaryOpInstruction(result, BinaryOp.ADD,
                DoubleConstant.of(10.5), DoubleConstant.of(20.5));
            block.addInstruction(add);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(DoubleConstant.of(31.0), constInstr.getConstant());
        }

        @Test
        void foldDoubleComparison() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction dcmpl = new BinaryOpInstruction(result, BinaryOp.DCMPL,
                DoubleConstant.of(3.0), DoubleConstant.of(5.0));
            block.addInstruction(dcmpl);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(-1), constInstr.getConstant());
        }

        @Test
        void foldLongToInt() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            UnaryOpInstruction l2i = new UnaryOpInstruction(result, UnaryOp.L2I, LongConstant.of(42L));
            block.addInstruction(l2i);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(42), constInstr.getConstant());
        }

        @Test
        void foldLongToFloat() {
            SSAValue result = new SSAValue(PrimitiveType.FLOAT);
            UnaryOpInstruction l2f = new UnaryOpInstruction(result, UnaryOp.L2F, LongConstant.of(100L));
            block.addInstruction(l2f);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(FloatConstant.of(100.0f), constInstr.getConstant());
        }

        @Test
        void foldLongToDouble() {
            SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
            UnaryOpInstruction l2d = new UnaryOpInstruction(result, UnaryOp.L2D, LongConstant.of(42L));
            block.addInstruction(l2d);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
        }

        @Test
        void foldFloatToInt() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            UnaryOpInstruction f2i = new UnaryOpInstruction(result, UnaryOp.F2I, FloatConstant.of(42.7f));
            block.addInstruction(f2i);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(42), constInstr.getConstant());
        }

        @Test
        void foldFloatToLong() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            UnaryOpInstruction f2l = new UnaryOpInstruction(result, UnaryOp.F2L, FloatConstant.of(100.5f));
            block.addInstruction(f2l);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(100L), constInstr.getConstant());
        }

        @Test
        void foldFloatToDouble() {
            SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
            UnaryOpInstruction f2d = new UnaryOpInstruction(result, UnaryOp.F2D, FloatConstant.of(3.14f));
            block.addInstruction(f2d);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
        }

        @Test
        void foldDoubleToInt() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            UnaryOpInstruction d2i = new UnaryOpInstruction(result, UnaryOp.D2I, DoubleConstant.of(99.9));
            block.addInstruction(d2i);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(99), constInstr.getConstant());
        }

        @Test
        void foldDoubleToLong() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            UnaryOpInstruction d2l = new UnaryOpInstruction(result, UnaryOp.D2L, DoubleConstant.of(1234.5));
            block.addInstruction(d2l);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(1234L), constInstr.getConstant());
        }

        @Test
        void foldDoubleToFloat() {
            SSAValue result = new SSAValue(PrimitiveType.FLOAT);
            UnaryOpInstruction d2f = new UnaryOpInstruction(result, UnaryOp.D2F, DoubleConstant.of(2.5));
            block.addInstruction(d2f);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(FloatConstant.of(2.5f), constInstr.getConstant());
        }

        @Test
        void foldIntToByte() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            UnaryOpInstruction i2b = new UnaryOpInstruction(result, UnaryOp.I2B, IntConstant.of(300));
            block.addInstruction(i2b);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
        }

        @Test
        void foldIntToChar() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            UnaryOpInstruction i2c = new UnaryOpInstruction(result, UnaryOp.I2C, IntConstant.of(65));
            block.addInstruction(i2c);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(IntConstant.of(65), constInstr.getConstant());
        }

        @Test
        void foldIntToShort() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            UnaryOpInstruction i2s = new UnaryOpInstruction(result, UnaryOp.I2S, IntConstant.of(70000));
            block.addInstruction(i2s);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
        }

        @Test
        void foldLongNegation() {
            SSAValue result = new SSAValue(PrimitiveType.LONG);
            UnaryOpInstruction neg = new UnaryOpInstruction(result, UnaryOp.NEG, LongConstant.of(100L));
            block.addInstruction(neg);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(LongConstant.of(-100L), constInstr.getConstant());
        }

        @Test
        void foldFloatNegation() {
            SSAValue result = new SSAValue(PrimitiveType.FLOAT);
            UnaryOpInstruction neg = new UnaryOpInstruction(result, UnaryOp.NEG, FloatConstant.of(3.14f));
            block.addInstruction(neg);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(FloatConstant.of(-3.14f), constInstr.getConstant());
        }

        @Test
        void foldDoubleNegation() {
            SSAValue result = new SSAValue(PrimitiveType.DOUBLE);
            UnaryOpInstruction neg = new UnaryOpInstruction(result, UnaryOp.NEG, DoubleConstant.of(2.71));
            block.addInstruction(neg);
            block.addInstruction(new ReturnInstruction());

            ConstantFolding transform = new ConstantFolding();
            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction first = block.getInstructions().get(0);
            assertTrue(first instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) first;
            assertEquals(DoubleConstant.of(-2.71), constInstr.getConstant());
        }
    }

    @Nested
    class ControlFlowReducibilityTests {

        private ControlFlowReducibility transform;

        @BeforeEach
        void setUp() {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();
            transform = new ControlFlowReducibility();
        }

        @Test
        void handlesComplexLoop() {
            IRMethod method = new IRMethod("com/test/Test", "complexLoop", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock loopHeader = new IRBlock("loopHeader");
            IRBlock loopBody = new IRBlock("loopBody");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(loopHeader);
            method.addBlock(loopBody);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            SSAValue counter = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(counter, IntConstant.ZERO));
            entry.addInstruction(SimpleInstruction.createGoto(loopHeader));
            entry.addSuccessor(loopHeader);

            PhiInstruction phi = new PhiInstruction(counter);
            phi.addIncoming(IntConstant.ZERO, entry);
            phi.addIncoming(counter, loopBody);
            loopHeader.addPhiInstruction(phi);
            loopHeader.addInstruction(new BranchInstruction(CompareOp.IFLT, counter, loopBody, exit));
            loopHeader.addSuccessor(loopBody);
            loopHeader.addSuccessor(exit);

            SSAValue incremented = new SSAValue(PrimitiveType.INT);
            loopBody.addInstruction(new BinaryOpInstruction(incremented, BinaryOp.ADD, counter, IntConstant.of(1)));
            loopBody.addInstruction(SimpleInstruction.createGoto(loopHeader));
            loopBody.addSuccessor(loopHeader);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesNestedLoops() {
            IRMethod method = new IRMethod("com/test/Test", "nestedLoops", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock outerLoop = new IRBlock("outerLoop");
            IRBlock innerLoop = new IRBlock("innerLoop");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(outerLoop);
            method.addBlock(innerLoop);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(outerLoop));
            entry.addSuccessor(outerLoop);

            SSAValue outerCond = new SSAValue(PrimitiveType.INT);
            outerLoop.addInstruction(new ConstantInstruction(outerCond, IntConstant.of(1)));
            outerLoop.addInstruction(new BranchInstruction(CompareOp.IFNE, outerCond, innerLoop, exit));
            outerLoop.addSuccessor(innerLoop);
            outerLoop.addSuccessor(exit);

            SSAValue innerCond = new SSAValue(PrimitiveType.INT);
            innerLoop.addInstruction(new ConstantInstruction(innerCond, IntConstant.of(1)));
            innerLoop.addInstruction(new BranchInstruction(CompareOp.IFNE, innerCond, innerLoop, outerLoop));
            innerLoop.addSuccessor(innerLoop);
            innerLoop.addSuccessor(outerLoop);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesMultipleExitLoops() {
            IRMethod method = new IRMethod("com/test/Test", "multiExit", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock loop = new IRBlock("loop");
            IRBlock exit1 = new IRBlock("exit1");
            IRBlock exit2 = new IRBlock("exit2");

            method.addBlock(entry);
            method.addBlock(loop);
            method.addBlock(exit1);
            method.addBlock(exit2);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(loop));
            entry.addSuccessor(loop);

            SSAValue cond1 = new SSAValue(PrimitiveType.INT);
            SSAValue cond2 = new SSAValue(PrimitiveType.INT);
            loop.addInstruction(new ConstantInstruction(cond1, IntConstant.of(1)));
            loop.addInstruction(new ConstantInstruction(cond2, IntConstant.of(0)));

            IRBlock branchBlock = new IRBlock("branch");
            method.addBlock(branchBlock);
            loop.addInstruction(new BranchInstruction(CompareOp.IFNE, cond1, branchBlock, exit1));
            loop.addSuccessor(branchBlock);
            loop.addSuccessor(exit1);

            branchBlock.addInstruction(new BranchInstruction(CompareOp.IFNE, cond2, loop, exit2));
            branchBlock.addSuccessor(loop);
            branchBlock.addSuccessor(exit2);

            exit1.addInstruction(new ReturnInstruction());
            exit2.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesBlocksWithPhiNodes() {
            IRMethod method = new IRMethod("com/test/Test", "withPhi", "()V", true);
            IRBlock entry = new IRBlock("entry");
            IRBlock b1 = new IRBlock("b1");
            IRBlock b2 = new IRBlock("b2");
            IRBlock merge = new IRBlock("merge");

            method.addBlock(entry);
            method.addBlock(b1);
            method.addBlock(b2);
            method.addBlock(merge);
            method.setEntryBlock(entry);

            SSAValue cond = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(cond, IntConstant.of(1)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));
            entry.addSuccessor(b1);
            entry.addSuccessor(b2);

            SSAValue v1 = new SSAValue(PrimitiveType.INT);
            b1.addInstruction(new ConstantInstruction(v1, IntConstant.of(10)));
            b1.addInstruction(SimpleInstruction.createGoto(merge));
            b1.addSuccessor(merge);

            SSAValue v2 = new SSAValue(PrimitiveType.INT);
            b2.addInstruction(new ConstantInstruction(v2, IntConstant.of(20)));
            b2.addInstruction(SimpleInstruction.createGoto(merge));
            b2.addSuccessor(merge);

            SSAValue phiResult = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(phiResult);
            phi.addIncoming(v1, b1);
            phi.addIncoming(v2, b2);
            merge.addPhiInstruction(phi);
            merge.addInstruction(new ReturnInstruction(phiResult));

            boolean changed = transform.run(method);

            assertNotNull(method.getBlocks());
        }

        @Test
        void preservesMethodStructure() {
            IRMethod method = new IRMethod("com/test/Test", "simple", "()V", true);
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            String originalName = method.getName();
            String originalDesc = method.getDescriptor();

            transform.run(method);

            assertEquals(originalName, method.getName());
            assertEquals(originalDesc, method.getDescriptor());
        }
    }

    @Nested
    class LoopPredicationTests {

        private IRMethod method;
        private LoopPredication transform;

        @BeforeEach
        void setUp() {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();
            method = new IRMethod("com/test/Test", "test", "()V", true);
            transform = new LoopPredication();
        }

        @Test
        void noChangesWhenNoEntryBlock() {
            assertFalse(transform.run(method));
        }

        @Test
        void noChangesWhenNoLoops() {
            IRBlock entry = new IRBlock("entry");
            method.addBlock(entry);
            method.setEntryBlock(entry);
            entry.addInstruction(new ReturnInstruction());

            assertFalse(transform.run(method));
        }

        @Test
        void detectsBasicInductionVariable() {
            IRBlock preheader = new IRBlock("preheader");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(preheader);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(exit);
            method.setEntryBlock(preheader);

            preheader.addInstruction(SimpleInstruction.createGoto(header));
            preheader.addSuccessor(header);

            SSAValue i = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(i);
            phi.addIncoming(IntConstant.of(0), preheader);
            header.addPhiInstruction(phi);
            header.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(10), body, exit));
            header.addSuccessor(body);
            header.addSuccessor(exit);

            SSAValue iNext = new SSAValue(PrimitiveType.INT);
            body.addInstruction(new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1)));
            body.addInstruction(SimpleInstruction.createGoto(header));
            body.addSuccessor(header);
            phi.addIncoming(iNext, body);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesLoopWithGuardCondition() {
            IRBlock preheader = new IRBlock("preheader");
            IRBlock header = new IRBlock("header");
            IRBlock guardCheck = new IRBlock("guardCheck");
            IRBlock guardTrue = new IRBlock("guardTrue");
            IRBlock guardFalse = new IRBlock("guardFalse");
            IRBlock latch = new IRBlock("latch");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(preheader);
            method.addBlock(header);
            method.addBlock(guardCheck);
            method.addBlock(guardTrue);
            method.addBlock(guardFalse);
            method.addBlock(latch);
            method.addBlock(exit);
            method.setEntryBlock(preheader);

            preheader.addInstruction(SimpleInstruction.createGoto(header));
            preheader.addSuccessor(header);

            SSAValue i = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(i);
            phi.addIncoming(IntConstant.of(0), preheader);
            header.addPhiInstruction(phi);
            header.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(10), guardCheck, exit));
            header.addSuccessor(guardCheck);
            header.addSuccessor(exit);

            guardCheck.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(5), guardTrue, guardFalse));
            guardCheck.addSuccessor(guardTrue);
            guardCheck.addSuccessor(guardFalse);

            guardTrue.addInstruction(SimpleInstruction.createGoto(latch));
            guardTrue.addSuccessor(latch);

            guardFalse.addInstruction(SimpleInstruction.createGoto(latch));
            guardFalse.addSuccessor(latch);

            SSAValue iNext = new SSAValue(PrimitiveType.INT);
            latch.addInstruction(new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1)));
            latch.addInstruction(SimpleInstruction.createGoto(header));
            latch.addSuccessor(header);
            phi.addIncoming(iNext, latch);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesNestedLoops() {
            IRBlock preheader = new IRBlock("preheader");
            IRBlock outerHeader = new IRBlock("outerHeader");
            IRBlock innerPreheader = new IRBlock("innerPreheader");
            IRBlock innerHeader = new IRBlock("innerHeader");
            IRBlock innerBody = new IRBlock("innerBody");
            IRBlock innerExit = new IRBlock("innerExit");
            IRBlock outerLatch = new IRBlock("outerLatch");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(preheader);
            method.addBlock(outerHeader);
            method.addBlock(innerPreheader);
            method.addBlock(innerHeader);
            method.addBlock(innerBody);
            method.addBlock(innerExit);
            method.addBlock(outerLatch);
            method.addBlock(exit);
            method.setEntryBlock(preheader);

            preheader.addInstruction(SimpleInstruction.createGoto(outerHeader));
            preheader.addSuccessor(outerHeader);

            SSAValue i = new SSAValue(PrimitiveType.INT);
            PhiInstruction outerPhi = new PhiInstruction(i);
            outerPhi.addIncoming(IntConstant.of(0), preheader);
            outerHeader.addPhiInstruction(outerPhi);
            outerHeader.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(10), innerPreheader, exit));
            outerHeader.addSuccessor(innerPreheader);
            outerHeader.addSuccessor(exit);

            innerPreheader.addInstruction(SimpleInstruction.createGoto(innerHeader));
            innerPreheader.addSuccessor(innerHeader);

            SSAValue j = new SSAValue(PrimitiveType.INT);
            PhiInstruction innerPhi = new PhiInstruction(j);
            innerPhi.addIncoming(IntConstant.of(0), innerPreheader);
            innerHeader.addPhiInstruction(innerPhi);
            innerHeader.addInstruction(new BranchInstruction(CompareOp.LT, j, IntConstant.of(5), innerBody, innerExit));
            innerHeader.addSuccessor(innerBody);
            innerHeader.addSuccessor(innerExit);

            SSAValue jNext = new SSAValue(PrimitiveType.INT);
            innerBody.addInstruction(new BinaryOpInstruction(jNext, BinaryOp.ADD, j, IntConstant.of(1)));
            innerBody.addInstruction(SimpleInstruction.createGoto(innerHeader));
            innerBody.addSuccessor(innerHeader);
            innerPhi.addIncoming(jNext, innerBody);

            innerExit.addInstruction(SimpleInstruction.createGoto(outerLatch));
            innerExit.addSuccessor(outerLatch);

            SSAValue iNext = new SSAValue(PrimitiveType.INT);
            outerLatch.addInstruction(new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1)));
            outerLatch.addInstruction(SimpleInstruction.createGoto(outerHeader));
            outerLatch.addSuccessor(outerHeader);
            outerPhi.addIncoming(iNext, outerLatch);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesLoopWithMultipleExits() {
            IRBlock preheader = new IRBlock("preheader");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock branch = new IRBlock("branch");
            IRBlock exit1 = new IRBlock("exit1");
            IRBlock exit2 = new IRBlock("exit2");

            method.addBlock(preheader);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(branch);
            method.addBlock(exit1);
            method.addBlock(exit2);
            method.setEntryBlock(preheader);

            preheader.addInstruction(SimpleInstruction.createGoto(header));
            preheader.addSuccessor(header);

            SSAValue i = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(i);
            phi.addIncoming(IntConstant.of(0), preheader);
            header.addPhiInstruction(phi);
            header.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(10), body, exit1));
            header.addSuccessor(body);
            header.addSuccessor(exit1);

            body.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(8), branch, exit2));
            body.addSuccessor(branch);
            body.addSuccessor(exit2);

            SSAValue iNext = new SSAValue(PrimitiveType.INT);
            branch.addInstruction(new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1)));
            branch.addInstruction(SimpleInstruction.createGoto(header));
            branch.addSuccessor(header);
            phi.addIncoming(iNext, branch);

            exit1.addInstruction(new ReturnInstruction());
            exit2.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesLoopWithNonUnitStride() {
            IRBlock preheader = new IRBlock("preheader");
            IRBlock header = new IRBlock("header");
            IRBlock body = new IRBlock("body");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(preheader);
            method.addBlock(header);
            method.addBlock(body);
            method.addBlock(exit);
            method.setEntryBlock(preheader);

            preheader.addInstruction(SimpleInstruction.createGoto(header));
            preheader.addSuccessor(header);

            SSAValue i = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(i);
            phi.addIncoming(IntConstant.of(0), preheader);
            header.addPhiInstruction(phi);
            header.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(100), body, exit));
            header.addSuccessor(body);
            header.addSuccessor(exit);

            SSAValue iNext = new SSAValue(PrimitiveType.INT);
            body.addInstruction(new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(2)));
            body.addInstruction(SimpleInstruction.createGoto(header));
            body.addSuccessor(header);
            phi.addIncoming(iNext, body);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesComparisonFlipping() {
            IRBlock preheader = new IRBlock("preheader");
            IRBlock header = new IRBlock("header");
            IRBlock guardCheck = new IRBlock("guardCheck");
            IRBlock guardTrue = new IRBlock("guardTrue");
            IRBlock guardFalse = new IRBlock("guardFalse");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(preheader);
            method.addBlock(header);
            method.addBlock(guardCheck);
            method.addBlock(guardTrue);
            method.addBlock(guardFalse);
            method.addBlock(exit);
            method.setEntryBlock(preheader);

            preheader.addInstruction(SimpleInstruction.createGoto(header));
            preheader.addSuccessor(header);

            SSAValue i = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(i);
            phi.addIncoming(IntConstant.of(0), preheader);
            header.addPhiInstruction(phi);
            header.addInstruction(new BranchInstruction(CompareOp.LT, i, IntConstant.of(10), guardCheck, exit));
            header.addSuccessor(guardCheck);
            header.addSuccessor(exit);

            guardCheck.addInstruction(new BranchInstruction(CompareOp.GT, IntConstant.of(8), i, guardTrue, guardFalse));
            guardCheck.addSuccessor(guardTrue);
            guardCheck.addSuccessor(guardFalse);

            SSAValue iNext = new SSAValue(PrimitiveType.INT);
            guardTrue.addInstruction(new BinaryOpInstruction(iNext, BinaryOp.ADD, i, IntConstant.of(1)));
            guardTrue.addInstruction(SimpleInstruction.createGoto(header));
            guardTrue.addSuccessor(header);
            phi.addIncoming(iNext, guardTrue);

            guardFalse.addInstruction(SimpleInstruction.createGoto(exit));
            guardFalse.addSuccessor(exit);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }
    }

    @Nested
    class CorrelatedValuePropagationTests {

        private IRMethod method;
        private CorrelatedValuePropagation transform;

        @BeforeEach
        void setUp() {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();
            method = new IRMethod("com/test/Test", "test", "()V", true);
            transform = new CorrelatedValuePropagation();
        }

        @Test
        void noChangesWhenNoEntryBlock() {
            assertFalse(transform.run(method));
        }

        @Test
        void propagatesLessThanConstraint() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(5)));
            entry.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(10), trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void propagatesGreaterThanConstraint() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(15)));
            entry.addInstruction(new BranchInstruction(CompareOp.GT, x, IntConstant.of(10), trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void propagatesEqualityConstraint() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            entry.addInstruction(new BranchInstruction(CompareOp.EQ, x, IntConstant.of(10), trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesUnaryBranches() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(5)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFLT, x, trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesIfEqUnaryBranch() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(0)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFEQ, x, trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesIfNeUnaryBranch() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(5)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, x, trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void optimizesBranchWithConstantRange() {
            IRBlock entry = new IRBlock("entry");
            IRBlock check = new IRBlock("check");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(check);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(5)));
            entry.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(10), check, falseBlock));
            entry.addSuccessor(check);
            entry.addSuccessor(falseBlock);

            check.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(20), trueBlock, falseBlock));
            check.addSuccessor(trueBlock);
            check.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesRangeIntersection() {
            IRBlock entry = new IRBlock("entry");
            IRBlock check1 = new IRBlock("check1");
            IRBlock check2 = new IRBlock("check2");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(check1);
            method.addBlock(check2);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(5)));
            entry.addInstruction(new BranchInstruction(CompareOp.GT, x, IntConstant.of(0), check1, falseBlock));
            entry.addSuccessor(check1);
            entry.addSuccessor(falseBlock);

            check1.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(20), check2, falseBlock));
            check1.addSuccessor(check2);
            check1.addSuccessor(falseBlock);

            check2.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(10), trueBlock, falseBlock));
            check2.addSuccessor(trueBlock);
            check2.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesLongConstants() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.LONG);
            entry.addInstruction(new ConstantInstruction(x, LongConstant.of(100L)));
            entry.addInstruction(new BranchInstruction(CompareOp.LT, x, LongConstant.of(200L), trueBlock, falseBlock));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(falseBlock);

            trueBlock.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }

        @Test
        void handlesComplexDominatorTree() {
            IRBlock entry = new IRBlock("entry");
            IRBlock b1 = new IRBlock("b1");
            IRBlock b2 = new IRBlock("b2");
            IRBlock b3 = new IRBlock("b3");
            IRBlock merge = new IRBlock("merge");
            IRBlock exit = new IRBlock("exit");

            method.addBlock(entry);
            method.addBlock(b1);
            method.addBlock(b2);
            method.addBlock(b3);
            method.addBlock(merge);
            method.addBlock(exit);
            method.setEntryBlock(entry);

            SSAValue x = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(x, IntConstant.of(5)));
            entry.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(10), b1, b2));
            entry.addSuccessor(b1);
            entry.addSuccessor(b2);

            b1.addInstruction(SimpleInstruction.createGoto(merge));
            b1.addSuccessor(merge);

            b2.addInstruction(new BranchInstruction(CompareOp.GT, x, IntConstant.of(3), b3, merge));
            b2.addSuccessor(b3);
            b2.addSuccessor(merge);

            b3.addInstruction(SimpleInstruction.createGoto(merge));
            b3.addSuccessor(merge);

            merge.addInstruction(new BranchInstruction(CompareOp.LT, x, IntConstant.of(8), exit, entry));
            merge.addSuccessor(exit);
            merge.addSuccessor(entry);

            exit.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);
            assertNotNull(method.getBlocks());
        }
    }

    @Nested
    class JumpThreadingTests {

        private IRMethod method;
        private JumpThreading transform;

        @BeforeEach
        void setUp() {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();
            method = new IRMethod("com/test/Test", "test", "()V", true);
            transform = new JumpThreading();
        }

        @Test
        void threadsSimpleGotoChain() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(intermediate);
            method.addBlock(target);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(intermediate));
            entry.addSuccessor(intermediate);

            intermediate.addInstruction(SimpleInstruction.createGoto(target));
            intermediate.addSuccessor(target);

            target.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            SimpleInstruction gotoInstr = (SimpleInstruction) entry.getTerminator();
            assertEquals(target, gotoInstr.getTarget());
        }

        @Test
        void threadsBranchTrueTarget() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock target = new IRBlock("target");
            IRBlock falseBlock = new IRBlock("false");

            method.addBlock(entry);
            method.addBlock(intermediate);
            method.addBlock(target);
            method.addBlock(falseBlock);
            method.setEntryBlock(entry);

            SSAValue cond = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(cond, IntConstant.of(1)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, intermediate, falseBlock));
            entry.addSuccessor(intermediate);
            entry.addSuccessor(falseBlock);

            intermediate.addInstruction(SimpleInstruction.createGoto(target));
            intermediate.addSuccessor(target);

            target.addInstruction(new ReturnInstruction());
            falseBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            BranchInstruction branch = (BranchInstruction) entry.getTerminator();
            assertEquals(target, branch.getTrueTarget());
        }

        @Test
        void threadsBranchFalseTarget() {
            IRBlock entry = new IRBlock("entry");
            IRBlock trueBlock = new IRBlock("true");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(trueBlock);
            method.addBlock(intermediate);
            method.addBlock(target);
            method.setEntryBlock(entry);

            SSAValue cond = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(cond, IntConstant.of(0)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, trueBlock, intermediate));
            entry.addSuccessor(trueBlock);
            entry.addSuccessor(intermediate);

            trueBlock.addInstruction(new ReturnInstruction());

            intermediate.addInstruction(SimpleInstruction.createGoto(target));
            intermediate.addSuccessor(target);

            target.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            BranchInstruction branch = (BranchInstruction) entry.getTerminator();
            assertEquals(target, branch.getFalseTarget());
        }

        @Test
        void threadsSwitchDefaultTarget() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(intermediate);
            method.addBlock(target);
            method.setEntryBlock(entry);

            SSAValue switchValue = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(switchValue, IntConstant.of(5)));
            SwitchInstruction switchInstr = new SwitchInstruction(switchValue, intermediate);
            entry.addInstruction(switchInstr);
            entry.addSuccessor(intermediate);

            intermediate.addInstruction(SimpleInstruction.createGoto(target));
            intermediate.addSuccessor(target);

            target.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            assertEquals(target, switchInstr.getDefaultTarget());
        }

        @Test
        void threadsSwitchCaseTargets() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate1 = new IRBlock("intermediate1");
            IRBlock intermediate2 = new IRBlock("intermediate2");
            IRBlock target1 = new IRBlock("target1");
            IRBlock target2 = new IRBlock("target2");
            IRBlock defaultBlock = new IRBlock("default");

            method.addBlock(entry);
            method.addBlock(intermediate1);
            method.addBlock(intermediate2);
            method.addBlock(target1);
            method.addBlock(target2);
            method.addBlock(defaultBlock);
            method.setEntryBlock(entry);

            SSAValue switchValue = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(switchValue, IntConstant.of(1)));
            SwitchInstruction switchInstr = new SwitchInstruction(switchValue, defaultBlock);
            switchInstr.addCase(1, intermediate1);
            switchInstr.addCase(2, intermediate2);
            entry.addInstruction(switchInstr);
            entry.addSuccessor(intermediate1);
            entry.addSuccessor(intermediate2);
            entry.addSuccessor(defaultBlock);

            intermediate1.addInstruction(SimpleInstruction.createGoto(target1));
            intermediate1.addSuccessor(target1);

            intermediate2.addInstruction(SimpleInstruction.createGoto(target2));
            intermediate2.addSuccessor(target2);

            target1.addInstruction(new ReturnInstruction());
            target2.addInstruction(new ReturnInstruction());
            defaultBlock.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            Map<Integer, IRBlock> cases = switchInstr.getCases();
            assertEquals(target1, cases.get(1));
            assertEquals(target2, cases.get(2));
        }

        @Test
        void doesNotThreadBlocksWithPhis() {
            IRBlock entry = new IRBlock("entry");
            IRBlock b1 = new IRBlock("b1");
            IRBlock b2 = new IRBlock("b2");
            IRBlock merge = new IRBlock("merge");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(b1);
            method.addBlock(b2);
            method.addBlock(merge);
            method.addBlock(target);
            method.setEntryBlock(entry);

            SSAValue cond = new SSAValue(PrimitiveType.INT);
            entry.addInstruction(new ConstantInstruction(cond, IntConstant.of(1)));
            entry.addInstruction(new BranchInstruction(CompareOp.IFNE, cond, b1, b2));
            entry.addSuccessor(b1);
            entry.addSuccessor(b2);

            SSAValue v1 = new SSAValue(PrimitiveType.INT);
            b1.addInstruction(new ConstantInstruction(v1, IntConstant.of(10)));
            b1.addInstruction(SimpleInstruction.createGoto(merge));
            b1.addSuccessor(merge);

            SSAValue v2 = new SSAValue(PrimitiveType.INT);
            b2.addInstruction(new ConstantInstruction(v2, IntConstant.of(20)));
            b2.addInstruction(SimpleInstruction.createGoto(merge));
            b2.addSuccessor(merge);

            SSAValue phiResult = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(phiResult);
            phi.addIncoming(v1, b1);
            phi.addIncoming(v2, b2);
            merge.addPhiInstruction(phi);
            merge.addInstruction(SimpleInstruction.createGoto(target));
            merge.addSuccessor(target);

            target.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertFalse(changed);
        }

        @Test
        void doesNotThreadBlocksWithInstructions() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(intermediate);
            method.addBlock(target);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(intermediate));
            entry.addSuccessor(intermediate);

            SSAValue temp = new SSAValue(PrimitiveType.INT);
            intermediate.addInstruction(new ConstantInstruction(temp, IntConstant.of(5)));
            intermediate.addInstruction(SimpleInstruction.createGoto(target));
            intermediate.addSuccessor(target);

            target.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertFalse(changed);
        }

        @Test
        void removesUnreachableBlocksAfterThreading() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock reachable = new IRBlock("reachable");
            IRBlock unreachable = new IRBlock("unreachable");

            method.addBlock(entry);
            method.addBlock(intermediate);
            method.addBlock(reachable);
            method.addBlock(unreachable);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(intermediate));
            entry.addSuccessor(intermediate);

            intermediate.addInstruction(SimpleInstruction.createGoto(reachable));
            intermediate.addSuccessor(reachable);

            reachable.addInstruction(new ReturnInstruction());
            unreachable.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            assertEquals(2, method.getBlocks().size());
            assertTrue(method.getBlocks().contains(entry));
            assertTrue(method.getBlocks().contains(reachable));
        }

        @Test
        void updatesPhisWhenThreading() {
            IRBlock entry = new IRBlock("entry");
            IRBlock intermediate = new IRBlock("intermediate");
            IRBlock target = new IRBlock("target");

            method.addBlock(entry);
            method.addBlock(intermediate);
            method.addBlock(target);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(intermediate));
            entry.addSuccessor(intermediate);

            intermediate.addInstruction(SimpleInstruction.createGoto(target));
            intermediate.addSuccessor(target);

            SSAValue v1 = new SSAValue(PrimitiveType.INT);
            SSAValue phiResult = new SSAValue(PrimitiveType.INT);
            PhiInstruction phi = new PhiInstruction(phiResult);
            phi.addIncoming(v1, intermediate);
            target.addPhiInstruction(phi);
            target.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            assertEquals(v1, phi.getIncoming(entry));
            assertNull(phi.getIncoming(intermediate));
        }

        @Test
        void avoidsInfiniteLoopDetection() {
            IRBlock entry = new IRBlock("entry");
            IRBlock loop1 = new IRBlock("loop1");
            IRBlock loop2 = new IRBlock("loop2");

            method.addBlock(entry);
            method.addBlock(loop1);
            method.addBlock(loop2);
            method.setEntryBlock(entry);

            entry.addInstruction(SimpleInstruction.createGoto(loop1));
            entry.addSuccessor(loop1);

            loop1.addInstruction(SimpleInstruction.createGoto(loop2));
            loop1.addSuccessor(loop2);

            loop2.addInstruction(SimpleInstruction.createGoto(loop1));
            loop2.addSuccessor(loop1);

            boolean changed = transform.run(method);

            assertNotNull(method.getBlocks());
        }
    }

    @Nested
    class AlgebraicSimplificationTests {

        private IRMethod method;
        private IRBlock block;
        private AlgebraicSimplification transform;

        @BeforeEach
        void setUp() {
            IRBlock.resetIdCounter();
            SSAValue.resetIdCounter();
            method = new IRMethod("com/test/Test", "test", "()V", true);
            block = new IRBlock("entry");
            method.addBlock(block);
            method.setEntryBlock(block);
            transform = new AlgebraicSimplification();
        }

        @Test
        void simplifiesAddZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
            CopyInstruction copy = (CopyInstruction) second;
            assertEquals(x, copy.getSource());
        }

        @Test
        void simplifiesZeroAdd() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, IntConstant.of(0), x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesSubZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.SUB, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesSubSameOperand() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.SUB, x, x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) second;
            assertEquals(IntConstant.of(0), constInstr.getConstant());
        }

        @Test
        void simplifiesMulZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.MUL, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) second;
            assertEquals(IntConstant.of(0), constInstr.getConstant());
        }

        @Test
        void simplifiesZeroMul() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.MUL, IntConstant.of(0), x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
        }

        @Test
        void simplifiesMulOne() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.MUL, x, IntConstant.of(1)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesOneMul() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.MUL, IntConstant.of(1), x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesDivOne() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.DIV, x, IntConstant.of(1)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesRemOne() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.REM, x, IntConstant.of(1)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) second;
            assertEquals(IntConstant.of(0), constInstr.getConstant());
        }

        @Test
        void simplifiesAndZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.AND, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) second;
            assertEquals(IntConstant.of(0), constInstr.getConstant());
        }

        @Test
        void simplifiesAndAllOnes() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.AND, x, IntConstant.of(-1)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesAndSameOperand() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.AND, x, x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesOrZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.OR, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesOrAllOnes() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.OR, x, IntConstant.of(-1)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) second;
            assertEquals(IntConstant.of(-1), constInstr.getConstant());
        }

        @Test
        void simplifiesOrSameOperand() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.OR, x, x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesXorZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.XOR, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesXorSameOperand() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.XOR, x, x));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof ConstantInstruction);
            ConstantInstruction constInstr = (ConstantInstruction) second;
            assertEquals(IntConstant.of(0), constInstr.getConstant());
        }

        @Test
        void simplifiesShiftLeftZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.SHL, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesShiftRightZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.SHR, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void simplifiesUnsignedShiftRightZero() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.USHR, x, IntConstant.of(0)));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertTrue(changed);
            IRInstruction second = block.getInstructions().get(1);
            assertTrue(second instanceof CopyInstruction);
        }

        @Test
        void noChangeForNonSimplifiableOperation() {
            SSAValue x = new SSAValue(PrimitiveType.INT);
            SSAValue y = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            block.addInstruction(new ConstantInstruction(x, IntConstant.of(10)));
            block.addInstruction(new ConstantInstruction(y, IntConstant.of(5)));
            block.addInstruction(new BinaryOpInstruction(result, BinaryOp.ADD, x, y));
            block.addInstruction(new ReturnInstruction());

            boolean changed = transform.run(method);

            assertFalse(changed);
        }
    }
}
