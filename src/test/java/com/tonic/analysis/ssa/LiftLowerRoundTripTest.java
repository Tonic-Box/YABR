package com.tonic.analysis.ssa;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end round-trip tests for SSA lift/lower operations.
 *
 * Tests that bytecode → lift → lower → bytecode preserves semantics.
 * Each test creates bytecode, lifts to IR, lowers back, then executes
 * both versions to verify identical behavior.
 */
class LiftLowerRoundTripTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    // ========== Simple Arithmetic Round Trips ==========

    @Nested
    class ArithmeticRoundTripTests {

        @Test
        void roundTripIntAddition() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtAdd")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");

            // Execute before transform
            Class<?> before = TestUtils.loadAndVerify(cf);
            Method mBefore = before.getMethod("add", int.class, int.class);
            int expected = (int) mBefore.invoke(null, 10, 20);

            // Create fresh class and transform
            ClassFile cf2 = BytecodeBuilder.forClass("com/test/RtAdd2")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method2 = findMethod(cf2, "add");
            SSA ssa = new SSA(cf2.getConstPool());
            IRMethod ir = ssa.lift(method2);
            ssa.lower(ir, method2);

            Class<?> after = TestUtils.loadAndVerify(cf2);
            Method mAfter = after.getMethod("add", int.class, int.class);
            int actual = (int) mAfter.invoke(null, 10, 20);

            assertEquals(expected, actual);
            assertEquals(30, actual);
        }

        @Test
        void roundTripIntSubtraction() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtSub")
                .publicStaticMethod("sub", "(II)I")
                    .iload(0)
                    .iload(1)
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "sub");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("sub", int.class, int.class);

            assertEquals(5, (int) m.invoke(null, 15, 10));
            assertEquals(-10, (int) m.invoke(null, 5, 15));
        }

        @Test
        void roundTripIntMultiplication() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtMul")
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
            Method m = clazz.getMethod("mul", int.class, int.class);

            assertEquals(200, (int) m.invoke(null, 10, 20));
            assertEquals(0, (int) m.invoke(null, 0, 100));
        }

        @Test
        void roundTripIntDivision() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtDiv")
                .publicStaticMethod("div", "(II)I")
                    .iload(0)
                    .iload(1)
                    .idiv()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "div");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("div", int.class, int.class);

            assertEquals(5, (int) m.invoke(null, 100, 20));
            assertEquals(3, (int) m.invoke(null, 10, 3));
        }

        @Test
        void roundTripIntRemainder() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtRem")
                .publicStaticMethod("rem", "(II)I")
                    .iload(0)
                    .iload(1)
                    .irem()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "rem");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("rem", int.class, int.class);

            assertEquals(1, (int) m.invoke(null, 10, 3));
            assertEquals(0, (int) m.invoke(null, 12, 4));
        }

        @Test
        void roundTripComplexExpression() throws Exception {
            // (a + b) * (a - b) = a^2 - b^2
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtComplex")
                .publicStaticMethod("compute", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(0)
                    .iload(1)
                    .isub()
                    .imul()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "compute");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("compute", int.class, int.class);

            // (5+3) * (5-3) = 8 * 2 = 16 = 5^2 - 3^2 = 25 - 9
            assertEquals(16, (int) m.invoke(null, 5, 3));
        }
    }

    // ========== Bitwise Operations Round Trips ==========

    @Nested
    class BitwiseRoundTripTests {

        @Test
        void roundTripBitwiseAnd() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtAnd")
                .publicStaticMethod("and", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iand()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "and");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("and", int.class, int.class);

            assertEquals(0b1000, (int) m.invoke(null, 0b1010, 0b1100));
        }

        @Test
        void roundTripBitwiseOr() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtOr")
                .publicStaticMethod("or", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ior()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "or");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("or", int.class, int.class);

            assertEquals(0b1110, (int) m.invoke(null, 0b1010, 0b1100));
        }

        @Test
        void roundTripBitwiseXor() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtXor")
                .publicStaticMethod("xor", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ixor()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "xor");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("xor", int.class, int.class);

            assertEquals(0b0110, (int) m.invoke(null, 0b1010, 0b1100));
        }

        @Test
        void roundTripLeftShift() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtShl")
                .publicStaticMethod("shl", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ishl()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "shl");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("shl", int.class, int.class);

            assertEquals(16, (int) m.invoke(null, 1, 4));
            assertEquals(8, (int) m.invoke(null, 2, 2));
        }

        @Test
        void roundTripRightShift() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtShr")
                .publicStaticMethod("shr", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ishr()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "shr");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("shr", int.class, int.class);

            assertEquals(4, (int) m.invoke(null, 16, 2));
            assertEquals(-4, (int) m.invoke(null, -16, 2)); // Sign-extended
        }
    }

    // ========== Constant Loading Round Trips ==========

    @Nested
    class ConstantRoundTripTests {

        @Test
        void roundTripSmallConstants() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtConst")
                .publicStaticMethod("const5", "()I")
                    .iconst(5)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "const5");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("const5");

            assertEquals(5, (int) m.invoke(null));
        }

        @Test
        void roundTripBipushConstant() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtBipush")
                .publicStaticMethod("const100", "()I")
                    .iconst(100)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "const100");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("const100");

            assertEquals(100, (int) m.invoke(null));
        }

        @Test
        void roundTripNegativeConstant() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtNeg")
                .publicStaticMethod("constNeg", "()I")
                    .iconst(-42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "constNeg");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("constNeg");

            assertEquals(-42, (int) m.invoke(null));
        }

        @Test
        void roundTripLargeConstant() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtLarge")
                .publicStaticMethod("constLarge", "()I")
                    .iconst(50000)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "constLarge");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("constLarge");

            assertEquals(50000, (int) m.invoke(null));
        }
    }

    // ========== Control Flow Round Trips ==========

    @Nested
    class ControlFlowRoundTripTests {

        @Test
        void roundTripSimpleReturn() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtReturn")
                .publicStaticMethod("identity", "(I)I")
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "identity");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("identity", int.class);

            assertEquals(42, (int) m.invoke(null, 42));
            assertEquals(-1, (int) m.invoke(null, -1));
        }

        @Test
        void roundTripVoidReturn() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtVoid")
                .publicStaticMethod("noop", "()V")
                    .vreturn()
                .build();

            MethodEntry method = findMethod(cf, "noop");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("noop");

            // Should not throw
            m.invoke(null);
        }
    }

    // ========== Local Variable Round Trips ==========

    @Nested
    class LocalVariableRoundTripTests {

        @Test
        void roundTripLocalVariableSwap() throws Exception {
            // Compute: a + b where we store and reload from locals
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtLocals")
                .publicStaticMethod("swap", "(II)I")
                    .iload(0)
                    .istore(2)  // temp = a
                    .iload(1)
                    .istore(0)  // a = b
                    .iload(2)
                    .istore(1)  // b = temp
                    .iload(0)
                    .iload(1)
                    .iadd()     // return new a + new b
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "swap");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("swap", int.class, int.class);

            // After swap: 10 + 20 still = 30
            assertEquals(30, (int) m.invoke(null, 10, 20));
        }

        @Test
        void roundTripMultipleLocals() throws Exception {
            // Use multiple local variables
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtMulti")
                .publicStaticMethod("multi", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .istore(3)  // local3 = a + b
                    .iload(3)
                    .iload(2)
                    .imul()     // return local3 * c
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "multi");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("multi", int.class, int.class, int.class);

            // (2 + 3) * 4 = 20
            assertEquals(20, (int) m.invoke(null, 2, 3, 4));
        }
    }

    // ========== IR Structure Verification ==========

    @Nested
    class IRStructureTests {

        @Test
        void liftPreservesBlockStructure() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/IRStruct")
                .publicStaticMethod("simple", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "simple");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);
            assertNotNull(ir.getEntryBlock());
            assertTrue(ir.getBlockCount() >= 1);
        }

        @Test
        void liftPreservesInstructions() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/IRInstr")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "add");
            IRMethod ir = TestUtils.liftMethod(method);

            assertNotNull(ir);

            // Count instructions
            int instrCount = 0;
            for (IRBlock block : ir.getBlocks()) {
                instrCount += block.getAllInstructions().size();
            }
            assertTrue(instrCount >= 1, "Should have at least one instruction");
        }

        @Test
        void lowerProducesValidBytecode() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/IRValid")
                .publicStaticMethod("test", "()I")
                    .iconst(1)
                    .iconst(2)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "test");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            // loadAndVerify will throw if bytecode is invalid
            Class<?> clazz = TestUtils.loadAndVerify(cf);
            assertNotNull(clazz);

            // Execute to confirm valid
            Method m = clazz.getMethod("test");
            assertEquals(3, (int) m.invoke(null));
        }
    }

    // ========== Edge Cases ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void roundTripZeroParameters() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtZero")
                .publicStaticMethod("zero", "()I")
                    .iconst(0)
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "zero");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("zero");

            assertEquals(0, (int) m.invoke(null));
        }

        @Test
        void roundTripManyParameters() throws Exception {
            // Method with 5 int parameters
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtMany")
                .publicStaticMethod("sum5", "(IIIII)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(2)
                    .iadd()
                    .iload(3)
                    .iadd()
                    .iload(4)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "sum5");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("sum5", int.class, int.class, int.class, int.class, int.class);

            assertEquals(15, (int) m.invoke(null, 1, 2, 3, 4, 5));
        }

        @Test
        void roundTripIntNegation() throws Exception {
            ClassFile cf = BytecodeBuilder.forClass("com/test/RtIneg")
                .publicStaticMethod("neg", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .build();

            MethodEntry method = findMethod(cf, "neg");
            SSA ssa = new SSA(cf.getConstPool());
            IRMethod ir = ssa.lift(method);
            ssa.lower(ir, method);

            Class<?> clazz = TestUtils.loadAndVerify(cf);
            Method m = clazz.getMethod("neg", int.class);

            assertEquals(-42, (int) m.invoke(null, 42));
            assertEquals(42, (int) m.invoke(null, -42));
        }
    }

    // ========== Helper Methods ==========

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method not found: " + name);
    }
}
