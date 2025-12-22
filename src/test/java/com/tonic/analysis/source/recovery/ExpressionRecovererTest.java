package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExpressionRecoverer - converting IR instructions to AST expressions.
 * Comprehensive coverage of expression recovery operations.
 */
class ExpressionRecovererTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Constant Recovery Tests ==========

    @Nested
    class ConstantRecoveryTests {

        @Test
        void recoverIntConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getInt", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            IntConstant intConst = IntConstant.of(42);
            Expression expr = recoverer.recoverConstant(intConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(42, lit.getValue());
        }

        @Test
        void recoverIntConstantAsBooleanTrue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getTrue", "()Z")
                    .iconst(1)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            IntConstant intConst = IntConstant.of(1);
            Expression expr = recoverer.recoverConstant(intConst, PrimitiveSourceType.BOOLEAN);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(true, lit.getValue());
        }

        @Test
        void recoverIntConstantAsBooleanFalse() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getFalse", "()Z")
                    .iconst(0)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            IntConstant intConst = IntConstant.of(0);
            Expression expr = recoverer.recoverConstant(intConst, PrimitiveSourceType.BOOLEAN);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(false, lit.getValue());
        }

        @Test
        void recoverIntConstantAsChar() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getChar", "()C")
                    .iconst(65)  // 'A'
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            IntConstant intConst = IntConstant.of(65);
            Expression expr = recoverer.recoverConstant(intConst, PrimitiveSourceType.CHAR);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals('A', lit.getValue());
        }

        @Test
        void recoverLongConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getLong", "()J")
                    .lconst(12345678901L)
                    .lreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            LongConstant longConst = new LongConstant(12345678901L);
            Expression expr = recoverer.recoverConstant(longConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(12345678901L, lit.getValue());
        }

        @Test
        void recoverFloatConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getFloat", "()F")
                    .fconst(3.14f)
                    .freturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            FloatConstant floatConst = new FloatConstant(3.14f);
            Expression expr = recoverer.recoverConstant(floatConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(3.14f, lit.getValue());
        }

        @Test
        void recoverDoubleConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getDouble", "()D")
                    .dconst(2.71828)
                    .dreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            DoubleConstant doubleConst = new DoubleConstant(2.71828);
            Expression expr = recoverer.recoverConstant(doubleConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(2.71828, lit.getValue());
        }

        @Test
        void recoverStringConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getString", "()Ljava/lang/String;")
                    .ldc("Hello, World!")
                    .areturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            StringConstant stringConst = new StringConstant("Hello, World!");
            Expression expr = recoverer.recoverConstant(stringConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals("Hello, World!", lit.getValue());
        }

        @Test
        void recoverNullConstant() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getNull", "()Ljava/lang/Object;")
                    .aconst_null()
                    .areturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            NullConstant nullConst = NullConstant.INSTANCE;
            Expression expr = recoverer.recoverConstant(nullConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertNull(lit.getValue());
        }

        @Test
        void recoverClassConstantDirect() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            ClassConstant classConst = new ClassConstant("java/lang/String");
            Expression expr = recoverer.recoverConstant(classConst, null);

            assertNotNull(expr);
            assertTrue(expr instanceof ClassExpr);
            ClassExpr classExpr = (ClassExpr) expr;
            assertNotNull(classExpr.getType());
        }
    }

    // ========== Binary Operations Tests ==========

    @Nested
    class BinaryOperationsTests {

        @Test
        void recoverIntAddition() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("add", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.ADD, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverIntSubtraction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("sub", "(II)I")
                    .iload(0)
                    .iload(1)
                    .isub()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.SUB, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverIntMultiplication() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("mul", "(II)I")
                    .iload(0)
                    .iload(1)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.MUL, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverIntDivision() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("div", "(II)I")
                    .iload(0)
                    .iload(1)
                    .idiv()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.DIV, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverIntRemainder() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("rem", "(II)I")
                    .iload(0)
                    .iload(1)
                    .irem()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.MOD, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverLeftShift() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("shl", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ishl()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.SHL, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverRightShift() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("shr", "(II)I")
                    .iload(0)
                    .iload(1)
                    .ishr()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.SHR, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverUnsignedRightShift() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("ushr", "(II)I")
                    .iload(0)
                    .iload(1)
                    .iushr()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.USHR, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverLongAddition() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("add", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .ladd()
                    .lreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.ADD, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverLongSubtraction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("sub", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lsub()
                    .lreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.SUB, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverLongMultiplication() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("mul", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .lmul()
                    .lreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.MUL, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverLongDivision() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("div", "(JJ)J")
                    .lload(0)
                    .lload(2)
                    .ldiv()
                    .lreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        BinaryExpr binExpr = (BinaryExpr) expr;
                        assertEquals(BinaryOperator.DIV, binExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverLongComparison() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("compare", "(JJ)I")
                    .lload(0)
                    .lload(2)
                    .lcmp()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        return;
                    }
                }
            }
        }

        @Test
        void recoverFloatComparisonLess() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("compare", "(FF)I")
                    .fload(0)
                    .fload(1)
                    .fcmpl()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        return;
                    }
                }
            }
        }

        @Test
        void recoverDoubleComparisonGreater() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("compare", "(DD)I")
                    .dload(0)
                    .dload(2)
                    .dcmpg()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof BinaryExpr);
                        return;
                    }
                }
            }
        }

        @Test
        void recoverBooleanAnd() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("and", "(ZZ)Z")
                    .iload(0)
                    .iload(1)
                    .iand()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                        if (binOp.getOp() == BinaryOp.AND) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof BinaryExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverBooleanOr() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("or", "(ZZ)Z")
                    .iload(0)
                    .iload(1)
                    .ior()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                        if (binOp.getOp() == BinaryOp.OR) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof BinaryExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverBooleanXor() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("xor", "(ZZ)Z")
                    .iload(0)
                    .iload(1)
                    .ixor()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                        if (binOp.getOp() == BinaryOp.XOR) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof BinaryExpr);
                            return;
                        }
                    }
                }
            }
        }
    }

    // ========== Unary Operations Tests ==========

    @Nested
    class UnaryOperationsTests {

        @Test
        void recoverIntNegation() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("negate", "(I)I")
                    .iload(0)
                    .ineg()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof UnaryExpr);
                        UnaryExpr unaryExpr = (UnaryExpr) expr;
                        assertEquals(UnaryOperator.NEG, unaryExpr.getOperator());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverIntToLongConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toLong", "(I)J")
                    .iload(0)
                    .i2l()
                    .lreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.I2L) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverLongToIntConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toInt", "(J)I")
                    .lload(0)
                    .l2i()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.L2I) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverIntToByteConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toByte", "(I)B")
                    .iload(0)
                    .i2b()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.I2B) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverIntToCharConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toChar", "(I)C")
                    .iload(0)
                    .i2c()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.I2C) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverIntToShortConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toShort", "(I)S")
                    .iload(0)
                    .i2s()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.I2S) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverIntToFloatConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toFloat", "(I)F")
                    .iload(0)
                    .i2f()
                    .freturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.I2F) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }

        @Test
        void recoverIntToDoubleConversion() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("toDouble", "(I)D")
                    .iload(0)
                    .i2d()
                    .dreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof UnaryOpInstruction) {
                        UnaryOpInstruction unary = (UnaryOpInstruction) instr;
                        if (unary.getOp() == UnaryOp.I2D) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof CastExpr);
                            return;
                        }
                    }
                }
            }
        }
    }

    // ========== Array Operations Tests ==========

    @Nested
    class ArrayOperationsTests {

        @Test
        void recoverArrayLoadInt() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getElement", "([II)I")
                    .aload(0)
                    .iload(1)
                    .iaload()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof ArrayLoadInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof ArrayAccessExpr);
                        return;
                    }
                }
            }
        }

        @Test
        void recoverArrayLoadWithComputedIndex() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getElement", "([II)I")
                    .aload(0)
                    .iload(1)
                    .iconst(2)
                    .imul()
                    .iaload()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof ArrayLoadInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof ArrayAccessExpr);
                        ArrayAccessExpr arrayAccess = (ArrayAccessExpr) expr;
                        assertTrue(arrayAccess.getIndex() instanceof BinaryExpr);
                        return;
                    }
                }
            }
        }

        @Test
        void recoverArrayLength() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("len", "([I)I")
                    .aload(0)
                    .arraylength()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof ArrayLengthInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof FieldAccessExpr);
                        FieldAccessExpr fieldExpr = (FieldAccessExpr) expr;
                        assertEquals("length", fieldExpr.getFieldName());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverNewArrayInstruction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("createArray", "(I)[I")
                    .iload(0)
                    .newarray(10) // T_INT
                    .areturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof NewArrayInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof NewArrayExpr);
                        return;
                    }
                }
            }
        }
    }

    // ========== Field Access Tests ==========

    @Nested
    class FieldAccessTests {

        @Test
        void recoverGetFieldInstruction() throws IOException {
            int publicAccess = new com.tonic.utill.AccessBuilder().setPublic().build();
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .field(publicAccess, "value", "I")
                .publicMethod("getValue", "()I")
                    .aload(0)
                    .getfield("com/test/Test", "value", "I")
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof GetFieldInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof FieldAccessExpr);
                        FieldAccessExpr fieldExpr = (FieldAccessExpr) expr;
                        assertEquals("value", fieldExpr.getFieldName());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverGetStaticField() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("getOut", "()Ljava/io/PrintStream;")
                    .getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                    .areturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof GetFieldInstruction) {
                        GetFieldInstruction getField = (GetFieldInstruction) instr;
                        if (getField.isStatic()) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof FieldAccessExpr);
                            FieldAccessExpr fieldExpr = (FieldAccessExpr) expr;
                            assertTrue(fieldExpr.isStatic());
                            assertEquals("out", fieldExpr.getFieldName());
                            return;
                        }
                    }
                }
            }
        }
    }

    // ========== Type Operations Tests ==========

    @Nested
    class TypeOperationsTests {

        @Test
        void recoverCastInstruction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("cast", "(Ljava/lang/Object;)Ljava/lang/String;")
                    .aload(0)
                    .checkcast("java/lang/String")
                    .areturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof CastInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof CastExpr);
                        return;
                    }
                }
            }
        }

        @Test
        void recoverInstanceOfInstruction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("check", "(Ljava/lang/Object;)Z")
                    .aload(0)
                    .instanceof_("java/lang/String")
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof InstanceOfInstruction) {
                        Expression expr = recoverer.recover(instr);
                        assertNotNull(expr);
                        assertTrue(expr instanceof InstanceOfExpr);
                        InstanceOfExpr instanceOf = (InstanceOfExpr) expr;
                        assertNotNull(instanceOf.getExpression());
                        assertNotNull(instanceOf.getCheckType());
                        return;
                    }
                }
            }
        }

        @Test
        void recoverNewInstructionDirect() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            NewInstruction newInstr = new NewInstruction(result, "java/lang/Object");

            Expression expr = recoverer.recover(newInstr);
            assertNotNull(expr);
            assertTrue(expr instanceof NewExpr);
            NewExpr newExpr = (NewExpr) expr;
            assertEquals("java/lang/Object", newExpr.getClassName());
        }
    }

    // ========== Method Invocation Tests ==========

    @Nested
    class MethodInvocationTests {

        @Test
        void recoverStaticMethodCall() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue result = new SSAValue(PrimitiveType.INT, "res");
            SSAValue arg = new SSAValue(PrimitiveType.INT, "arg");
            InvokeInstruction invoke = new InvokeInstruction(
                result, InvokeType.STATIC, "java/lang/Math", "abs", "(I)I",
                List.of(arg)
            );
            Expression expr = recoverer.recover(invoke);
            assertNotNull(expr);
            assertTrue(expr instanceof MethodCallExpr);
            MethodCallExpr methodExpr = (MethodCallExpr) expr;
            assertEquals("abs", methodExpr.getMethodName());
            assertTrue(methodExpr.isStatic());
        }

        @Test
        void recoverVirtualMethodCall() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue receiver = new SSAValue(new ReferenceType("java/lang/String"), "str");
            SSAValue result = new SSAValue(PrimitiveType.INT, "len");
            InvokeInstruction invoke = new InvokeInstruction(
                result, InvokeType.VIRTUAL, "java/lang/String", "length", "()I",
                List.of(receiver)
            );
            Expression expr = recoverer.recover(invoke);
            assertNotNull(expr);
            assertTrue(expr instanceof MethodCallExpr);
            MethodCallExpr methodExpr = (MethodCallExpr) expr;
            assertEquals("length", methodExpr.getMethodName());
            assertFalse(methodExpr.isStatic());
        }

        @Test
        void recoverInterfaceMethodCall() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue receiver = new SSAValue(new ReferenceType("java/util/List"), "list");
            SSAValue result = new SSAValue(PrimitiveType.INT, "size");
            InvokeInstruction invoke = new InvokeInstruction(
                result, InvokeType.INTERFACE, "java/util/List", "size", "()I",
                List.of(receiver)
            );
            Expression expr = recoverer.recover(invoke);
            assertNotNull(expr);
            assertTrue(expr instanceof MethodCallExpr);
            MethodCallExpr methodExpr = (MethodCallExpr) expr;
            assertEquals("size", methodExpr.getMethodName());
            assertFalse(methodExpr.isStatic());
        }

        @Test
        void recoverConstructorCallDirect() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue receiver = new SSAValue(new ReferenceType("java/lang/String"), "str");
            ctx.registerPendingNew(receiver, "java/lang/String");
            SSAValue arg = new SSAValue(new ReferenceType("java/lang/String"), "arg");
            InvokeInstruction invoke = new InvokeInstruction(
                null, InvokeType.SPECIAL, "java/lang/String", "<init>", "(Ljava/lang/String;)V",
                List.of(receiver, arg)
            );
            Expression expr = recoverer.recover(invoke);
            assertNotNull(expr);
            assertTrue(expr instanceof NewExpr);
            NewExpr newExpr = (NewExpr) expr;
            assertEquals("java/lang/String", newExpr.getClassName());
            assertEquals(1, newExpr.getArguments().size());
        }

        @Test
        void recoverMethodCallWithMultipleArguments() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue arg1 = new SSAValue(PrimitiveType.INT, "a");
            SSAValue arg2 = new SSAValue(PrimitiveType.INT, "b");
            SSAValue arg3 = new SSAValue(new ReferenceType("java/lang/String"), "c");

            InvokeInstruction invoke = new InvokeInstruction(
                null, InvokeType.STATIC, "com/test/Test", "method", "(IILjava/lang/String;)V",
                List.of(arg1, arg2, arg3)
            );
            Expression expr = recoverer.recover(invoke);
            assertNotNull(expr);
            assertTrue(expr instanceof MethodCallExpr);
            MethodCallExpr methodExpr = (MethodCallExpr) expr;
            assertEquals("method", methodExpr.getMethodName());
            assertEquals(3, methodExpr.getArguments().size());
        }
    }

    // ========== Load Local Tests ==========

    @Nested
    class LoadLocalTests {

        @Test
        void recoverLoadLocalAsThis() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicMethod("getThis", "()Lcom/test/Test;")
                    .aload(0)
                    .areturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof LoadLocalInstruction) {
                        LoadLocalInstruction load = (LoadLocalInstruction) instr;
                        if (load.getLocalIndex() == 0) {
                            Expression expr = recoverer.recover(instr);
                            assertNotNull(expr);
                            assertTrue(expr instanceof ThisExpr);
                            return;
                        }
                    }
                }
            }
        }

    }

    // ========== Context Caching Tests ==========

    @Nested
    class ContextCachingTests {

        @Test
        void expressionCachingWorks() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()I")
                    .iconst(42)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue ssa = new SSAValue(PrimitiveType.INT, "v");
            LiteralExpr lit = LiteralExpr.ofInt(42);
            ctx.cacheExpression(ssa, lit);

            assertTrue(ctx.isRecovered(ssa));
            assertEquals(lit, ctx.getCachedExpression(ssa));
        }

        @Test
        void pendingNewInstructionHandling() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);

            SSAValue ssa = new SSAValue(new ReferenceType("java/lang/StringBuilder"), "sb");
            ctx.registerPendingNew(ssa, "java/lang/StringBuilder");

            assertTrue(ctx.isPendingNew(ssa));
            assertEquals("java/lang/StringBuilder", ctx.consumePendingNew(ssa));
            assertFalse(ctx.isPendingNew(ssa));
        }
    }

    // ========== Edge Cases Tests ==========

    @Nested
    class EdgeCaseTests {

        @Test
        void recoverOperandWithMaterializedValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "(I)I")
                    .iload(0)
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue ssa = new SSAValue(PrimitiveType.INT, "x");
            ctx.setVariableName(ssa, "myVar");
            ctx.markMaterialized(ssa);

            Expression expr = recoverer.recoverOperand(ssa);
            assertNotNull(expr);
            assertTrue(expr instanceof VarRefExpr);
            VarRefExpr varRef = (VarRefExpr) expr;
            assertEquals("myVar", varRef.getName());
        }

        @Test
        void recoverOperandWithThisReference() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue ssa = new SSAValue(new ReferenceType("com/test/Test"), "this");
            ctx.setVariableName(ssa, "this");
            ctx.markMaterialized(ssa);

            Expression expr = recoverer.recoverOperand(ssa);
            assertNotNull(expr);
            assertTrue(expr instanceof ThisExpr);
        }

        @Test
        void recoverOperandWithConstantValue() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            IntConstant constant = IntConstant.of(100);
            Expression expr = recoverer.recoverOperand(constant);
            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
            LiteralExpr lit = (LiteralExpr) expr;
            assertEquals(100, lit.getValue());
        }

        @Test
        void recoverDefaultValueForUnsupportedInstruction() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("test", "()V")
                    .vreturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            PhiInstruction phi = new PhiInstruction(result);

            Expression expr = recoverer.recover(phi);
            assertNotNull(expr);
            assertTrue(expr instanceof LiteralExpr);
        }

        @Test
        void recoverNestedBinaryOperations() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("com/test/Test")
                .publicStaticMethod("complex", "(III)I")
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .iload(2)
                    .imul()
                    .ireturn()
                .build();

            MethodEntry method = cf.getMethods().get(0);
            IRMethod ir = TestUtils.liftMethod(method);
            DefUseChains defUse = new DefUseChains(ir);
            defUse.compute();

            RecoveryContext ctx = new RecoveryContext(ir, method, defUse);
            ExpressionRecoverer recoverer = new ExpressionRecoverer(ctx);

            BinaryOpInstruction mulInstr = null;
            for (IRBlock block : ir.getBlocks()) {
                for (IRInstruction instr : block.getInstructions()) {
                    if (instr instanceof BinaryOpInstruction) {
                        BinaryOpInstruction binOp = (BinaryOpInstruction) instr;
                        if (binOp.getOp() == BinaryOp.MUL) {
                            mulInstr = binOp;
                        }
                    }
                }
            }

            if (mulInstr != null) {
                Expression expr = recoverer.recover(mulInstr);
                assertNotNull(expr);
                assertTrue(expr instanceof BinaryExpr);
            }
        }
    }
}
