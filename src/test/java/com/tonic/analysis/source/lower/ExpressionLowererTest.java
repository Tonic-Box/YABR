package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExpressionLowerer.
 * Covers lowering of all AST expression types to IR instructions.
 */
class ExpressionLowererTest {

    private ClassPool pool;
    private ConstPool constPool;
    private LoweringContext ctx;
    private ExpressionLowerer lowerer;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = pool.createNewClass("com/test/ExpressionLowererTest", access);
        constPool = classFile.getConstPool();

        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        IRMethod irMethod = new IRMethod("com/test/Test", "test", "()V", true);
        IRBlock entryBlock = new IRBlock();
        irMethod.addBlock(entryBlock);
        irMethod.setEntryBlock(entryBlock);

        ctx = new LoweringContext(irMethod, constPool);
        ctx.setCurrentBlock(entryBlock);
        lowerer = new ExpressionLowerer(ctx);
    }

    // ========== Literal Tests ==========

    @Test
    void lowerIntLiteral() {
        LiteralExpr lit = LiteralExpr.ofInt(42);
        Value result = lowerer.lower(lit);

        assertNotNull(result);
        assertTrue(result instanceof SSAValue);
        assertEquals(PrimitiveType.INT, ((SSAValue) result).getType());

        IRBlock block = ctx.getCurrentBlock();
        assertEquals(1, block.getInstructions().size());
        assertTrue(block.getInstructions().get(0) instanceof ConstantInstruction);

        ConstantInstruction instr = (ConstantInstruction) block.getInstructions().get(0);
        assertEquals(IntConstant.of(42), instr.getConstant());
    }

    @Test
    void lowerLongLiteral() {
        LiteralExpr lit = LiteralExpr.ofLong(123456789L);
        Value result = lowerer.lower(lit);

        assertNotNull(result);
        assertTrue(result instanceof SSAValue);
        assertEquals(PrimitiveType.LONG, ((SSAValue) result).getType());

        ConstantInstruction instr = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertTrue(instr.getConstant() instanceof LongConstant);
        assertEquals(123456789L, ((LongConstant) instr.getConstant()).getValue());
    }

    @Test
    void lowerFloatLiteral() {
        LiteralExpr lit = LiteralExpr.ofFloat(3.14f);
        Value result = lowerer.lower(lit);

        assertNotNull(result);
        assertEquals(PrimitiveType.FLOAT, ((SSAValue) result).getType());

        ConstantInstruction instr = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertTrue(instr.getConstant() instanceof FloatConstant);
        assertEquals(3.14f, ((FloatConstant) instr.getConstant()).getValue(), 0.001);
    }

    @Test
    void lowerDoubleLiteral() {
        LiteralExpr lit = LiteralExpr.ofDouble(2.718);
        Value result = lowerer.lower(lit);

        assertNotNull(result);
        assertEquals(PrimitiveType.DOUBLE, ((SSAValue) result).getType());

        ConstantInstruction instr = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertTrue(instr.getConstant() instanceof DoubleConstant);
        assertEquals(2.718, ((DoubleConstant) instr.getConstant()).getValue(), 0.001);
    }

    @Test
    void lowerBooleanLiteral() {
        LiteralExpr litTrue = LiteralExpr.ofBoolean(true);
        Value resultTrue = lowerer.lower(litTrue);

        assertNotNull(resultTrue);
        ConstantInstruction instrTrue = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertEquals(IntConstant.ONE, instrTrue.getConstant());

        ctx.getCurrentBlock().getInstructions().clear();

        LiteralExpr litFalse = LiteralExpr.ofBoolean(false);
        Value resultFalse = lowerer.lower(litFalse);

        assertNotNull(resultFalse);
        ConstantInstruction instrFalse = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertEquals(IntConstant.ZERO, instrFalse.getConstant());
    }

    @Test
    void lowerStringLiteral() {
        LiteralExpr lit = LiteralExpr.ofString("Hello");
        Value result = lowerer.lower(lit);

        assertNotNull(result);
        ConstantInstruction instr = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertTrue(instr.getConstant() instanceof StringConstant);
        assertEquals("Hello", ((StringConstant) instr.getConstant()).getValue());
    }

    @Test
    void lowerNullLiteral() {
        LiteralExpr lit = LiteralExpr.ofNull();
        Value result = lowerer.lower(lit);

        assertNotNull(result);
        ConstantInstruction instr = (ConstantInstruction) ctx.getCurrentBlock().getInstructions().get(0);
        assertEquals(NullConstant.INSTANCE, instr.getConstant());
    }

    // ========== Variable Reference Tests ==========

    @Test
    void lowerVarRef() {
        SSAValue varValue = new SSAValue(PrimitiveType.INT);
        ctx.setVariable("x", varValue);

        VarRefExpr varRef = new VarRefExpr("x", PrimitiveSourceType.INT);
        Value result = lowerer.lower(varRef);

        assertEquals(varValue, result);
    }

    @Test
    void lowerVarRefWithSSAValue() {
        SSAValue ssaValue = new SSAValue(PrimitiveType.INT);
        VarRefExpr varRef = new VarRefExpr("x", PrimitiveSourceType.INT, ssaValue);
        Value result = lowerer.lower(varRef);

        assertEquals(ssaValue, result);
    }

    @Test
    void lowerVarRefUndefinedThrowsException() {
        VarRefExpr varRef = new VarRefExpr("undefined", PrimitiveSourceType.INT);
        assertThrows(LoweringException.class, () -> lowerer.lower(varRef));
    }

    // ========== Binary Expression Tests ==========

    @Test
    void lowerBinaryAdd() {
        LiteralExpr left = LiteralExpr.ofInt(10);
        LiteralExpr right = LiteralExpr.ofInt(20);
        BinaryExpr add = new BinaryExpr(BinaryOperator.ADD, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(add);

        assertNotNull(result);
        assertTrue(result instanceof SSAValue);

        List<IRInstruction> instructions = ctx.getCurrentBlock().getInstructions();
        assertTrue(instructions.stream().anyMatch(i -> i instanceof BinaryOpInstruction &&
            ((BinaryOpInstruction) i).getOp() == BinaryOp.ADD));
    }

    @Test
    void lowerBinarySubtract() {
        LiteralExpr left = LiteralExpr.ofInt(30);
        LiteralExpr right = LiteralExpr.ofInt(15);
        BinaryExpr sub = new BinaryExpr(BinaryOperator.SUB, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(sub);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.SUB));
    }

    @Test
    void lowerBinaryMultiply() {
        LiteralExpr left = LiteralExpr.ofInt(5);
        LiteralExpr right = LiteralExpr.ofInt(6);
        BinaryExpr mul = new BinaryExpr(BinaryOperator.MUL, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(mul);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.MUL));
    }

    @Test
    void lowerBinaryDivide() {
        LiteralExpr left = LiteralExpr.ofInt(100);
        LiteralExpr right = LiteralExpr.ofInt(4);
        BinaryExpr div = new BinaryExpr(BinaryOperator.DIV, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(div);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.DIV));
    }

    @Test
    void lowerBinaryModulo() {
        LiteralExpr left = LiteralExpr.ofInt(17);
        LiteralExpr right = LiteralExpr.ofInt(5);
        BinaryExpr mod = new BinaryExpr(BinaryOperator.MOD, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(mod);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.REM));
    }

    @Test
    void lowerBitwiseAnd() {
        LiteralExpr left = LiteralExpr.ofInt(15);
        LiteralExpr right = LiteralExpr.ofInt(7);
        BinaryExpr band = new BinaryExpr(BinaryOperator.BAND, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(band);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.AND));
    }

    @Test
    void lowerBitwiseOr() {
        LiteralExpr left = LiteralExpr.ofInt(8);
        LiteralExpr right = LiteralExpr.ofInt(4);
        BinaryExpr bor = new BinaryExpr(BinaryOperator.BOR, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(bor);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.OR));
    }

    @Test
    void lowerBitwiseXor() {
        LiteralExpr left = LiteralExpr.ofInt(12);
        LiteralExpr right = LiteralExpr.ofInt(10);
        BinaryExpr bxor = new BinaryExpr(BinaryOperator.BXOR, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(bxor);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.XOR));
    }

    @Test
    void lowerShiftLeft() {
        LiteralExpr left = LiteralExpr.ofInt(1);
        LiteralExpr right = LiteralExpr.ofInt(3);
        BinaryExpr shl = new BinaryExpr(BinaryOperator.SHL, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(shl);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.SHL));
    }

    @Test
    void lowerShiftRight() {
        LiteralExpr left = LiteralExpr.ofInt(16);
        LiteralExpr right = LiteralExpr.ofInt(2);
        BinaryExpr shr = new BinaryExpr(BinaryOperator.SHR, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(shr);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.SHR));
    }

    @Test
    void lowerUnsignedShiftRight() {
        LiteralExpr left = LiteralExpr.ofInt(-1);
        LiteralExpr right = LiteralExpr.ofInt(1);
        BinaryExpr ushr = new BinaryExpr(BinaryOperator.USHR, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(ushr);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.USHR));
    }

    // ========== Assignment Tests ==========

    @Test
    void lowerSimpleAssignment() {
        LiteralExpr value = LiteralExpr.ofInt(100);
        VarRefExpr var = new VarRefExpr("x", PrimitiveSourceType.INT);
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, var, value, PrimitiveSourceType.INT);

        Value result = lowerer.lower(assign);

        assertNotNull(result);
        assertTrue(ctx.hasVariable("x"));
        assertEquals(result, ctx.getVariable("x"));
    }

    @Test
    void lowerCompoundAssignment() {
        SSAValue existingVar = new SSAValue(PrimitiveType.INT);
        ctx.setVariable("x", existingVar);

        VarRefExpr var = new VarRefExpr("x", PrimitiveSourceType.INT);
        LiteralExpr value = LiteralExpr.ofInt(5);
        BinaryExpr addAssign = new BinaryExpr(BinaryOperator.ADD_ASSIGN, var, value, PrimitiveSourceType.INT);

        Value result = lowerer.lower(addAssign);

        assertNotNull(result);
        assertTrue(result instanceof SSAValue);

        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.ADD));
    }

    // ========== Comparison Tests ==========

    @Test
    void lowerIntComparison() {
        LiteralExpr left = LiteralExpr.ofInt(10);
        LiteralExpr right = LiteralExpr.ofInt(20);
        BinaryExpr eq = new BinaryExpr(BinaryOperator.EQ, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(eq);

        assertNotNull(result);
        assertTrue(result instanceof SSAValue);
        assertTrue(ctx.getIrMethod().getBlockCount() > 1);
    }

    @Test
    void lowerLongComparison() {
        LiteralExpr left = LiteralExpr.ofLong(100L);
        LiteralExpr right = LiteralExpr.ofLong(200L);
        BinaryExpr lt = new BinaryExpr(BinaryOperator.LT, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(lt);

        assertNotNull(result);
        // Check all blocks in the method for the LCMP instruction since comparison creates multiple blocks
        assertTrue(ctx.getIrMethod().getBlocks().stream()
            .flatMap(b -> b.getInstructions().stream())
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.LCMP));
    }

    // ========== Short-Circuit Logic Tests ==========

    @Test
    void lowerLogicalAnd() {
        LiteralExpr left = LiteralExpr.ofBoolean(true);
        LiteralExpr right = LiteralExpr.ofBoolean(false);
        BinaryExpr and = new BinaryExpr(BinaryOperator.AND, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(and);

        assertNotNull(result);
        assertTrue(result instanceof SSAValue);
        assertTrue(ctx.getIrMethod().getBlockCount() > 1);
    }

    @Test
    void lowerLogicalOr() {
        LiteralExpr left = LiteralExpr.ofBoolean(false);
        LiteralExpr right = LiteralExpr.ofBoolean(true);
        BinaryExpr or = new BinaryExpr(BinaryOperator.OR, left, right, PrimitiveSourceType.INT);

        Value result = lowerer.lower(or);

        assertNotNull(result);
        assertTrue(ctx.getIrMethod().getBlockCount() > 1);
    }

    // ========== Unary Expression Tests ==========

    @Test
    void lowerUnaryNegate() {
        LiteralExpr operand = LiteralExpr.ofInt(42);
        UnaryExpr neg = new UnaryExpr(UnaryOperator.NEG, operand, PrimitiveSourceType.INT);

        Value result = lowerer.lower(neg);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof UnaryOpInstruction &&
                ((UnaryOpInstruction) i).getOp() == UnaryOp.NEG));
    }

    @Test
    void lowerUnaryPlus() {
        LiteralExpr operand = LiteralExpr.ofInt(42);
        UnaryExpr pos = new UnaryExpr(UnaryOperator.POS, operand, PrimitiveSourceType.INT);

        Value result = lowerer.lower(pos);
        assertNotNull(result);
    }

    @Test
    void lowerUnaryBitwiseNot() {
        LiteralExpr operand = LiteralExpr.ofInt(0xFF);
        UnaryExpr bnot = new UnaryExpr(UnaryOperator.BNOT, operand, PrimitiveSourceType.INT);

        Value result = lowerer.lower(bnot);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.XOR));
    }

    @Test
    void lowerPreIncrement() {
        SSAValue var = new SSAValue(PrimitiveType.INT);
        ctx.setVariable("x", var);

        VarRefExpr varRef = new VarRefExpr("x", PrimitiveSourceType.INT);
        UnaryExpr preInc = new UnaryExpr(UnaryOperator.PRE_INC, varRef, PrimitiveSourceType.INT);

        Value result = lowerer.lower(preInc);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof BinaryOpInstruction &&
                ((BinaryOpInstruction) i).getOp() == BinaryOp.ADD));
    }

    @Test
    void lowerPostIncrement() {
        SSAValue var = new SSAValue(PrimitiveType.INT);
        ctx.setVariable("x", var);

        VarRefExpr varRef = new VarRefExpr("x", PrimitiveSourceType.INT);
        UnaryExpr postInc = new UnaryExpr(UnaryOperator.POST_INC, varRef, PrimitiveSourceType.INT);

        Value result = lowerer.lower(postInc);

        assertNotNull(result);
        assertEquals(var, result);
    }

    // ========== Method Call Tests ==========

    @Test
    void lowerStaticMethodCall() {
        MethodCallExpr call = new MethodCallExpr(
            null, "add", "com/test/Math",
            List.of(LiteralExpr.ofInt(1), LiteralExpr.ofInt(2)),
            true, PrimitiveSourceType.INT
        );

        Value result = lowerer.lower(call);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof InvokeInstruction &&
                ((InvokeInstruction) i).getInvokeType() == InvokeType.STATIC));
    }

    @Test
    void lowerVirtualMethodCall() {
        VarRefExpr receiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
        SSAValue receiverValue = new SSAValue(new ReferenceType("java/lang/Object"));
        ctx.setVariable("obj", receiverValue);

        MethodCallExpr call = new MethodCallExpr(
            receiver, "doSomething", "com/test/Test",
            List.of(), false,
            com.tonic.analysis.source.ast.type.VoidSourceType.INSTANCE
        );

        Value result = lowerer.lower(call);

        assertEquals(NullConstant.INSTANCE, result);

        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof InvokeInstruction &&
                ((InvokeInstruction) i).getInvokeType() == InvokeType.VIRTUAL));
    }

    // ========== Field Access Tests ==========

    @Test
    void lowerStaticFieldAccess() {
        FieldAccessExpr field = new FieldAccessExpr(
            null, "VERSION", "com/test/Config",
            true, PrimitiveSourceType.INT
        );

        Value result = lowerer.lower(field);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof FieldAccessInstruction && ((FieldAccessInstruction) i).isLoad()));
    }

    @Test
    void lowerInstanceFieldAccess() {
        VarRefExpr receiver = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
        SSAValue receiverValue = new SSAValue(new ReferenceType("com/test/Test"));
        ctx.setVariable("obj", receiverValue);

        FieldAccessExpr field = new FieldAccessExpr(
            receiver, "count", "com/test/Test",
            false, PrimitiveSourceType.INT
        );

        Value result = lowerer.lower(field);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof FieldAccessInstruction && ((FieldAccessInstruction) i).isLoad()));
    }

    // ========== Array Operations Tests ==========

    @Test
    void lowerArrayAccess() {
        VarRefExpr array = new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT, 1));
        SSAValue arrayValue = new SSAValue(new com.tonic.analysis.ssa.type.ArrayType(PrimitiveType.INT, 1));
        ctx.setVariable("arr", arrayValue);

        LiteralExpr index = LiteralExpr.ofInt(0);
        ArrayAccessExpr access = new ArrayAccessExpr(array, index, PrimitiveSourceType.INT);

        Value result = lowerer.lower(access);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) i).isLoad()));
    }

    @Test
    void lowerArrayAssignment() {
        VarRefExpr array = new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT, 1));
        SSAValue arrayValue = new SSAValue(new com.tonic.analysis.ssa.type.ArrayType(PrimitiveType.INT, 1));
        ctx.setVariable("arr", arrayValue);

        LiteralExpr index = LiteralExpr.ofInt(0);
        ArrayAccessExpr access = new ArrayAccessExpr(array, index, PrimitiveSourceType.INT);

        LiteralExpr value = LiteralExpr.ofInt(42);
        BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN, access, value, PrimitiveSourceType.INT);

        Value result = lowerer.lower(assign);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof ArrayAccessInstruction && ((ArrayAccessInstruction) i).isStore()));
    }

    // ========== Object Creation Tests ==========

    @Test
    void lowerNewObject() {
        NewExpr newExpr = new NewExpr("java/lang/Object", List.of(), ReferenceSourceType.OBJECT);

        Value result = lowerer.lower(newExpr);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof NewInstruction));
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof InvokeInstruction &&
                "<init>".equals(((InvokeInstruction) i).getName())));
    }

    @Test
    void lowerNewArray() {
        List<Expression> dims = List.of(LiteralExpr.ofInt(10));
        NewArrayExpr newArr = new NewArrayExpr(PrimitiveSourceType.INT, dims);

        Value result = lowerer.lower(newArr);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof NewArrayInstruction));
    }

    // ========== Cast Tests ==========

    @Test
    void lowerPrimitiveCast() {
        LiteralExpr expr = LiteralExpr.ofInt(42);
        CastExpr cast = new CastExpr(PrimitiveSourceType.LONG, expr);

        Value result = lowerer.lower(cast);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof UnaryOpInstruction &&
                ((UnaryOpInstruction) i).getOp() == UnaryOp.I2L));
    }

    @Test
    void lowerReferenceCast() {
        VarRefExpr expr = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
        SSAValue objValue = new SSAValue(new ReferenceType("java/lang/Object"));
        ctx.setVariable("obj", objValue);

        CastExpr cast = new CastExpr(ReferenceSourceType.STRING, expr);

        Value result = lowerer.lower(cast);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof TypeCheckInstruction && ((TypeCheckInstruction) i).isCast()));
    }

    // ========== Ternary Tests ==========

    @Test
    void lowerTernary() {
        LiteralExpr condition = LiteralExpr.ofBoolean(true);
        LiteralExpr thenExpr = LiteralExpr.ofInt(10);
        LiteralExpr elseExpr = LiteralExpr.ofInt(20);

        TernaryExpr ternary = new TernaryExpr(condition, thenExpr, elseExpr, PrimitiveSourceType.INT);

        Value result = lowerer.lower(ternary);

        assertNotNull(result);
        assertTrue(ctx.getIrMethod().getBlockCount() > 1);
    }

    // ========== InstanceOf Tests ==========

    @Test
    void lowerInstanceOf() {
        VarRefExpr expr = new VarRefExpr("obj", ReferenceSourceType.OBJECT);
        SSAValue objValue = new SSAValue(new ReferenceType("java/lang/Object"));
        ctx.setVariable("obj", objValue);

        InstanceOfExpr instanceOf = new InstanceOfExpr(expr, ReferenceSourceType.STRING);

        Value result = lowerer.lower(instanceOf);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof TypeCheckInstruction && ((TypeCheckInstruction) i).isInstanceOf()));
    }

    // ========== This Tests ==========

    @Test
    void lowerThis() {
        SSAValue thisValue = new SSAValue(new ReferenceType("com/test/Test"));
        ctx.setVariable("this", thisValue);

        ThisExpr thisExpr = new ThisExpr(ReferenceSourceType.OBJECT);

        Value result = lowerer.lower(thisExpr);

        assertEquals(thisValue, result);
    }

    // ========== Array Initializer Tests ==========

    @Test
    void lowerArrayInit() {
        List<Expression> elements = List.of(
            LiteralExpr.ofInt(1),
            LiteralExpr.ofInt(2),
            LiteralExpr.ofInt(3)
        );

        ArrayInitExpr arrayInit = new ArrayInitExpr(
            elements,
            new ArraySourceType(PrimitiveSourceType.INT, 1)
        );

        Value result = lowerer.lower(arrayInit);

        assertNotNull(result);
        assertTrue(ctx.getCurrentBlock().getInstructions().stream()
            .anyMatch(i -> i instanceof NewArrayInstruction));
    }
}
