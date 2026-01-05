package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.type.VoidType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BytecodeLifter and related SSA lifting components.
 *
 * Tests bytecode-to-IR lifting including:
 * - Simple methods (return constants, arithmetic)
 * - Parameter handling (primitives, objects)
 * - Control flow (blocks, terminators)
 * - Phi node insertion
 */
class BytecodeLifterTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    /**
     * Helper to find method by name in ClassFile.
     */
    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method not found: " + name);
    }

    // ========== Simple Method Lifting Tests ==========

    @Nested
    class SimpleMethodLiftingTests {

        @Test
        void liftEmptyVoidMethod() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Simple")
                .publicStaticMethod("empty", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "empty");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertEquals("empty", ir.getName());
            assertEquals("()V", ir.getDescriptor());
            assertTrue(ir.isStatic());
            assertNotNull(ir.getEntryBlock());
            assertTrue(ir.getBlockCount() >= 1);
        }

        @Test
        void liftReturnConstantInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Const")
                .publicStaticMethod("getFortyTwo", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "getFortyTwo");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertEquals(PrimitiveType.INT, ir.getReturnType());

            IRBlock entry = ir.getEntryBlock();
            assertNotNull(entry);
            assertTrue(entry.hasTerminator());

            // Should have at least a return instruction
            assertFalse(entry.getInstructions().isEmpty());
        }

        @Test
        void liftReturnConstantLong() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Long")
                .publicStaticMethod("getLong", "()J")
                    .lconst(100L)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "getLong");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertEquals(PrimitiveType.LONG, ir.getReturnType());
        }

        @Test
        void liftVoidReturnType() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Void")
                .publicStaticMethod("doNothing", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "doNothing");
            IRMethod ir = TestUtils.liftMethod(method);

            assertEquals(VoidType.INSTANCE, ir.getReturnType());
        }
    }

    // ========== Parameter Handling Tests ==========

    @Nested
    class ParameterHandlingTests {

        @Test
        void liftStaticMethodWithIntParams() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Params")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");
            IRMethod ir = TestUtils.liftMethod(method);

            List<SSAValue> params = ir.getParameters();
            assertEquals(2, params.size());
            assertEquals(PrimitiveType.INT, params.get(0).getType());
            assertEquals(PrimitiveType.INT, params.get(1).getType());
        }

        @Test
        void liftStaticMethodWithLongParam() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/LongParam")
                .publicStaticMethod("identity", "(J)J")
                    .lload(0)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "identity");
            IRMethod ir = TestUtils.liftMethod(method);

            List<SSAValue> params = ir.getParameters();
            assertEquals(1, params.size());
            assertEquals(PrimitiveType.LONG, params.get(0).getType());
        }

        @Test
        void liftStaticMethodWithMixedParams() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Mixed")
                .publicStaticMethod("compute", "(IJI)J")
                    .iload(0)
                    .i2l()
                    .lload(1)
                    .ladd()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            List<SSAValue> params = ir.getParameters();
            assertEquals(3, params.size());
            assertEquals(PrimitiveType.INT, params.get(0).getType());
            assertEquals(PrimitiveType.LONG, params.get(1).getType());
            assertEquals(PrimitiveType.INT, params.get(2).getType());
        }

        @Test
        void liftMethodWithObjectParam() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ObjParam")
                .publicStaticMethod("check", "(Ljava/lang/String;)I")
                    .aload(0)
                    .aconst_null()
                    .pop()
                    .pop()
                    .iconst(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "check");
            IRMethod ir = TestUtils.liftMethod(method);

            List<SSAValue> params = ir.getParameters();
            assertEquals(1, params.size());

            IRType paramType = params.get(0).getType();
            assertTrue(paramType instanceof ReferenceType);
        }

        @Test
        void liftMethodWithArrayParam() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ArrayParam")
                .publicStaticMethod("length", "([I)I")
                    .aload(0)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "length");
            IRMethod ir = TestUtils.liftMethod(method);

            List<SSAValue> params = ir.getParameters();
            assertEquals(1, params.size());
        }
    }

    // ========== Arithmetic Instruction Tests ==========

    @Nested
    class ArithmeticInstructionTests {

        @Test
        void liftIntegerAddition() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Add")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertNotNull(ir.getEntryBlock());

            // Should contain BinaryOpInstruction for add
            boolean foundBinaryOp = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.ADD) {
                        foundBinaryOp = true;
                        break;
                    }
                }
            }
            assertTrue(foundBinaryOp, "Should have ADD binary operation");
        }

        @Test
        void liftIntegerSubtraction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Sub")
                .publicStaticMethod("sub", "(II)I")
                    .iload(0)
                    .iload(1)
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "sub");
            IRMethod ir = TestUtils.liftMethod(method);

            boolean foundSub = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.SUB) {
                        foundSub = true;
                        break;
                    }
                }
            }
            assertTrue(foundSub, "Should have SUB binary operation");
        }

        @Test
        void liftIntegerMultiplication() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Mul")
                .publicStaticMethod("mul", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "mul");
            IRMethod ir = TestUtils.liftMethod(method);

            boolean foundMul = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.MUL) {
                        foundMul = true;
                        break;
                    }
                }
            }
            assertTrue(foundMul, "Should have MUL binary operation");
        }

        @Test
        void liftIntegerDivision() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Div")
                .publicStaticMethod("div", "(II)I")
                    .iload(0)
                    .iload(1)
                    .idiv()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "div");
            IRMethod ir = TestUtils.liftMethod(method);

            boolean foundDiv = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.DIV) {
                        foundDiv = true;
                        break;
                    }
                }
            }
            assertTrue(foundDiv, "Should have DIV binary operation");
        }

        @Test
        void liftIntegerNegation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Neg")
                .publicStaticMethod("negate", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "negate");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            // Should contain UnaryOpInstruction for negation
        }

        @Test
        void liftComplexArithmetic() throws IOException {
            // (a + b) * (a - b)
            ClassFile cf = BytecodeBuilder.forClass("com/test/Complex")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)  // a
                    .iload(1)  // b
                    .iadd()    // a + b
                    .iload(0)  // a
                    .iload(1)  // b
                    .isub()    // a - b
                    .imul()    // (a + b) * (a - b)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            // Count binary operations
            int binOpCount = 0;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    binOpCount++;
                }
            }
            assertEquals(3, binOpCount, "Should have 3 binary operations (add, sub, mul)");
        }
    }

    // ========== Bitwise Instruction Tests ==========

    @Nested
    class BitwiseInstructionTests {

        @Test
        void liftBitwiseAnd() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/And")
                .publicStaticMethod("and", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iand()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "and");
            IRMethod ir = TestUtils.liftMethod(method);

            boolean foundAnd = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.AND) {
                        foundAnd = true;
                        break;
                    }
                }
            }
            assertTrue(foundAnd, "Should have AND binary operation");
        }

        @Test
        void liftBitwiseOr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Or")
                .publicStaticMethod("or", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ior()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "or");
            IRMethod ir = TestUtils.liftMethod(method);

            boolean foundOr = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.OR) {
                        foundOr = true;
                        break;
                    }
                }
            }
            assertTrue(foundOr, "Should have OR binary operation");
        }

        @Test
        void liftBitwiseXor() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Xor")
                .publicStaticMethod("xor", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ixor()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "xor");
            IRMethod ir = TestUtils.liftMethod(method);

            boolean foundXor = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof BinaryOpInstruction) {
                    BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                    if (binOp.getOp() == BinaryOp.XOR) {
                        foundXor = true;
                        break;
                    }
                }
            }
            assertTrue(foundXor, "Should have XOR binary operation");
        }

        @Test
        void liftShiftLeft() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Shl")
                .publicStaticMethod("shl", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ishl()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "shl");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }

        @Test
        void liftShiftRight() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Shr")
                .publicStaticMethod("shr", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ishr()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "shr");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }
    }

    // ========== Type Conversion Tests ==========

    @Nested
    class TypeConversionTests {

        @Test
        void liftIntToLong() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/I2L")
                .publicStaticMethod("toLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "toLong");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertEquals(PrimitiveType.LONG, ir.getReturnType());
        }

        @Test
        void liftLongToInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/L2I")
                .publicStaticMethod("toInt", "(J)I")
                    .lload(0)
                    .l2i()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "toInt");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertEquals(PrimitiveType.INT, ir.getReturnType());
        }

        @Test
        void liftNarrowingConversions() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Narrow")
                .publicStaticMethod("toByte", "(I)I")
                    .iload(0)
                    .i2b()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "toByte");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }
    }

    // ========== Stack Operation Tests ==========

    @Nested
    class StackOperationTests {

        @Test
        void liftDup() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Dup")
                .publicStaticMethod("twice", "(I)I")
                    .iload(0)
                    .dup()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "twice");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }

        @Test
        void liftPop() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Pop")
                .publicStaticMethod("first", "(II)I")
                    .iload(0)
                    .iload(1)
                    .pop()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "first");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }

        @Test
        void liftSwap() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Swap")
                .publicStaticMethod("swapSub", "(II)I")
                    .iload(0)
                    .iload(1)
                    .swap()
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "swapSub");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }

        @Test
        void liftDupX2Form1_ThreeCategoryOneValues() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/DupX2Form1")
                .publicStaticMethod("compute", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .dup_x2()
                    .iadd()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("compute", int.class, int.class, int.class);
            int result = (int) m.invoke(null, 1, 2, 3);
            assertEquals(9, result);
        }

        @Test
        void liftDupX2Form2_CategoryOneOverCategoryTwo() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/DupX2Form2")
                .publicStaticMethod("compute", "(JI)I")
                    .lload(0)
                    .iload(2)
                    .dup_x2()
                    .pop()
                    .pop2()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("compute", long.class, int.class);
            int result = (int) m.invoke(null, 100L, 5);
            assertEquals(5, result);
        }

        @Test
        void liftDupX2Form2_DoubleAndInt() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/DupX2Double")
                .publicStaticMethod("compute", "(DI)I")
                    .dload(0)
                    .iload(2)
                    .dup_x2()
                    .pop()
                    .pop2()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("compute", double.class, int.class);
            int result = (int) m.invoke(null, 10.5, 3);
            assertEquals(3, result);
        }

        @Test
        void liftDup2X1Form1_TwoCategoryOneOverOne() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Dup2X1")
                .publicStaticMethod("compute", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .dup2_x1()
                    .iadd()
                    .iadd()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("compute", int.class, int.class, int.class);
            int result = (int) m.invoke(null, 1, 2, 3);
            assertEquals(11, result);
        }

        @Test
        void liftDup2X1Form2_CategoryTwoOverOne() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Dup2X1Long")
                .publicStaticMethod("compute", "(IJ)J")
                    .iload(0)
                    .lload(1)
                    .dup2_x1()
                    .lstore(3)
                    .pop()
                    .pop2()
                    .lload(3)
                    .lreturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("compute", int.class, long.class);
            long result = (long) m.invoke(null, 5, 100L);
            assertEquals(100L, result);
        }

        @Test
        void liftDup2X2Form1_FourCategoryOneValues() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Dup2X2")
                .publicStaticMethod("compute", "(IIII)I")
                    .iload(0)
                    .iload(1)
                    .iload(2)
                    .iload(3)
                    .dup2_x2()
                    .iadd()
                    .iadd()
                    .iadd()
                    .iadd()
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            SSA ssa = new SSA(cf.getConstPool());
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("compute", int.class, int.class, int.class, int.class);
            int result = (int) m.invoke(null, 1, 2, 3, 4);
            assertEquals(17, result);
        }
    }

    // ========== Local Variable Tests ==========

    @Nested
    class LocalVariableTests {

        @Test
        void liftLocalStore() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Store")
                .publicStaticMethod("storeAndReturn", "(I)I")
                    .iload(0)
                    .iconst(1)
                    .iadd()
                    .istore(1)
                    .iload(1)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "storeAndReturn");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }

        @Test
        void liftMultipleLocals() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/MultiLocal")
                .publicStaticMethod("useLocals", "(I)I")
                    .iload(0)
                    .istore(1)  // local 1 = param
                    .iconst(10)
                    .istore(2)  // local 2 = 10
                    .iload(1)
                    .iload(2)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "useLocals");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertTrue(ir.getBlockCount() >= 1);
        }
    }

    // ========== Array Access Tests ==========

    @Nested
    class ArrayAccessTests {

        @Test
        void liftArrayLength() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/ArrLen")
                .publicStaticMethod("len", "([I)I")
                    .aload(0)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "len");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }

        @Test
        void liftNewArray() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NewArr")
                .publicStaticMethod("createIntArray", "(I)[I")
                    .iload(0)
                    .newarray(10) // T_INT = 10
                    .areturn()
                .build();

            MethodEntry method = findMethod(cf, "createIntArray");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
        }
    }

    // ========== Method Invocation Tests ==========

    @Nested
    class MethodInvocationTests {

        @Test
        void liftInvokeStatic() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Invoke")
                .publicStaticMethod("callStatic", "()I")
                    .iconst(5)
                    .invokestatic("java/lang/Math", "abs", "(I)I")
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "callStatic");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            boolean foundInvoke = false;
            for (IRInstruction instr : ir.getEntryBlock().getInstructions()) {
                if (instr instanceof InvokeInstruction) {
                    foundInvoke = true;
                    break;
                }
            }
            assertTrue(foundInvoke, "Should have invoke instruction");
        }
    }

    // ========== Block Structure Tests ==========

    @Nested
    class BlockStructureTests {

        @Test
        void linearMethodHasSingleBlock() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Linear")
                .publicStaticMethod("linear", "(I)I")
                    .iload(0)
                    .iconst(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "linear");
            IRMethod ir = TestUtils.liftMethod(method);

            assertEquals(1, ir.getBlockCount());
        }

        @Test
        void entryBlockIsSet() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Entry")
                .publicStaticMethod("entry", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "entry");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir.getEntryBlock());
            assertTrue(ir.getBlocks().contains(ir.getEntryBlock()));
        }

        @Test
        void terminatorIsAtEndOfBlock() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Term")
                .publicStaticMethod("terminate", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "terminate");
            IRMethod ir = TestUtils.liftMethod(method);

            IRBlock entry = ir.getEntryBlock();
            assertTrue(entry.hasTerminator());

            IRInstruction terminator = entry.getTerminator();
            assertTrue(terminator instanceof ReturnInstruction);
        }
    }

    // ========== BytecodeLifter Direct Tests ==========

    @Nested
    class BytecodeLifterDirectTests {

        @Test
        void lifterCreation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Direct")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            BytecodeLifter lifter = new BytecodeLifter(cf.getConstPool());
            assertNotNull(lifter);
        }

        @Test
        void liftMethodWithNoCode() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/NoCode")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            BytecodeLifter lifter = new BytecodeLifter(cf.getConstPool());
            MethodEntry method = findMethod(cf, "test");

            IRMethod ir = lifter.lift(method);
            assertNotNull(ir);
        }
    }

    // ========== InstructionTranslator Tests ==========

    @Nested
    class InstructionTranslatorTests {

        @Test
        void translatorCreation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Trans")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            InstructionTranslator translator = new InstructionTranslator(cf.getConstPool());
            assertNotNull(translator);
        }

        @Test
        void translatorBlockRegistration() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Block")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            InstructionTranslator translator = new InstructionTranslator(cf.getConstPool());
            IRBlock block = new IRBlock("test");

            translator.registerBlock(0, block);
            // Should not throw
        }
    }

    // ========== AbstractState Tests ==========

    @Nested
    class AbstractStateTests {

        @Test
        void abstractStateCreation() {
            AbstractState state = new AbstractState();
            assertNotNull(state);
        }

        @Test
        void abstractStatePushPop() {
            AbstractState state = new AbstractState();
            SSAValue value = new SSAValue(PrimitiveType.INT, "test");

            state.push(value);
            assertEquals(1, state.getStackSize());

            assertEquals(value, state.pop());
            assertEquals(0, state.getStackSize());
        }

        @Test
        void abstractStateLocalVariables() {
            AbstractState state = new AbstractState();
            SSAValue value = new SSAValue(PrimitiveType.INT, "local");

            state.setLocal(0, value);
            assertEquals(value, state.getLocal(0));
        }

        @Test
        void abstractStateCopy() {
            AbstractState original = new AbstractState();
            SSAValue value = new SSAValue(PrimitiveType.INT, "test");
            original.push(value);
            original.setLocal(0, value);

            AbstractState copy = original.copy();

            assertNotSame(original, copy);
            assertEquals(original.getStackSize(), copy.getStackSize());
        }

        @Test
        void abstractStatePeek() {
            AbstractState state = new AbstractState();
            SSAValue value = new SSAValue(PrimitiveType.INT, "test");

            state.push(value);
            assertEquals(value, state.peek());
            // peek should not remove
            assertEquals(1, state.getStackSize());
        }

        @Test
        void abstractStateIsEmpty() {
            AbstractState state = new AbstractState();
            assertTrue(state.isStackEmpty());

            state.push(new SSAValue(PrimitiveType.INT, "v"));
            assertFalse(state.isStackEmpty());
        }

        @Test
        void abstractStateHasLocal() {
            AbstractState state = new AbstractState();
            assertFalse(state.hasLocal(0));

            state.setLocal(0, new SSAValue(PrimitiveType.INT, "v"));
            assertTrue(state.hasLocal(0));
        }

        @Test
        void abstractStateClearStack() {
            AbstractState state = new AbstractState();
            state.push(new SSAValue(PrimitiveType.INT, "v1"));
            state.push(new SSAValue(PrimitiveType.INT, "v2"));

            state.clearStack();
            assertTrue(state.isStackEmpty());
        }

        @Test
        void abstractStateGetStackValues() {
            AbstractState state = new AbstractState();
            SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
            SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

            state.push(v1);
            state.push(v2);

            List<?> values = state.getStackValues();
            assertEquals(2, values.size());
        }

        @Test
        void abstractStateGetLocalIndices() {
            AbstractState state = new AbstractState();
            state.setLocal(0, new SSAValue(PrimitiveType.INT, "v0"));
            state.setLocal(2, new SSAValue(PrimitiveType.INT, "v2"));

            var indices = state.getLocalIndices();
            assertEquals(2, indices.size());
            assertTrue(indices.contains(0));
            assertTrue(indices.contains(2));
        }
    }

    // ========== Round-Trip Tests ==========

    @Nested
    class RoundTripTests {

        @Test
        void liftLowerRoundTrip() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RoundTrip")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");

            // Lift
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            assertNotNull(ir);

            // Lower
            ssa.lower(ir, method);

            // Verify class can be loaded
            Class<?> clazz = TestUtils.loadAndVerify(cf);
            assertNotNull(clazz);

            // Execute and verify result
            java.lang.reflect.Method m = clazz.getMethod("compute", int.class, int.class);
            int result = (int) m.invoke(null, 3, 5);
            assertEquals(8, result);
        }

        @Test
        void liftLowerWithMultiplication() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Multiply")
                .publicStaticMethod("mul", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "mul");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("mul", int.class, int.class);
            int result = (int) m.invoke(null, 6, 7);
            assertEquals(42, result);
        }

        @Test
        void liftLowerWithConstant() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Constant")
                .publicStaticMethod("get42", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "get42");

            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            java.lang.reflect.Method m = clazz.getMethod("get42");
            int result = (int) m.invoke(null);
            assertEquals(42, result);
        }
    }
}
