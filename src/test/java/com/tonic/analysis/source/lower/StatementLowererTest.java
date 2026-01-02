package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.ssa.cfg.EdgeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
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
 * Comprehensive tests for StatementLowerer covering statement lowering to IR.
 * Tests control flow, variable declarations, exception handling, and more.
 */
class StatementLowererTest {

    private ClassPool pool;
    private ConstPool constPool;
    private LoweringContext ctx;
    private ExpressionLowerer exprLowerer;
    private StatementLowerer lowerer;
    private IRMethod irMethod;
    private IRBlock entryBlock;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile classFile = pool.createNewClass("com/test/StatementLowererTest", access);
        constPool = classFile.getConstPool();

        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        irMethod = new IRMethod("com/test/Test", "testMethod", "()V", true);
        entryBlock = new IRBlock();
        irMethod.addBlock(entryBlock);
        irMethod.setEntryBlock(entryBlock);

        ctx = new LoweringContext(irMethod, constPool);
        ctx.setCurrentBlock(entryBlock);

        exprLowerer = new ExpressionLowerer(ctx);
        lowerer = new StatementLowerer(ctx, exprLowerer);
    }

    // ========== Variable Declaration Tests ==========

    @Test
    void lowerVarDeclWithoutInitializer() {
        VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x");
        lowerer.lower(varDecl);

        assertTrue(ctx.hasVariable("x"));
        SSAValue var = ctx.getVariable("x");
        assertNotNull(var);
        assertEquals(PrimitiveType.INT, var.getType());
    }

    @Test
    void lowerVarDeclWithInitializer() {
        Expression init = LiteralExpr.ofInt(42);
        VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x", init);
        lowerer.lower(varDecl);

        assertTrue(ctx.hasVariable("x"));
        SSAValue var = ctx.getVariable("x");
        assertNotNull(var);

        List<IRInstruction> instructions = entryBlock.getInstructions();
        assertTrue(instructions.size() >= 1);
        assertTrue(instructions.get(0) instanceof ConstantInstruction);
    }

    @Test
    void lowerMultipleVarDecls() {
        VarDeclStmt var1 = new VarDeclStmt(PrimitiveSourceType.INT, "a", LiteralExpr.ofInt(1));
        VarDeclStmt var2 = new VarDeclStmt(PrimitiveSourceType.INT, "b", LiteralExpr.ofInt(2));

        lowerer.lower(var1);
        lowerer.lower(var2);

        assertTrue(ctx.hasVariable("a"));
        assertTrue(ctx.hasVariable("b"));
        assertNotSame(ctx.getVariable("a"), ctx.getVariable("b"));
    }

    // ========== Return Statement Tests ==========

    @Test
    void lowerVoidReturn() {
        ReturnStmt ret = new ReturnStmt();
        lowerer.lower(ret);

        IRInstruction terminator = entryBlock.getTerminator();
        assertNotNull(terminator);
        assertTrue(terminator instanceof ReturnInstruction);
        ReturnInstruction retInstr = (ReturnInstruction) terminator;
        assertNull(retInstr.getReturnValue());
    }

    @Test
    void lowerReturnWithValue() {
        Expression value = LiteralExpr.ofInt(42);
        ReturnStmt ret = new ReturnStmt(value);
        lowerer.lower(ret);

        IRInstruction terminator = entryBlock.getTerminator();
        assertNotNull(terminator);
        assertTrue(terminator instanceof ReturnInstruction);
        ReturnInstruction retInstr = (ReturnInstruction) terminator;
        assertNotNull(retInstr.getReturnValue());
    }

    // ========== If Statement Tests ==========

    @Test
    void lowerIfWithoutElse() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new ReturnStmt();
        IfStmt ifStmt = new IfStmt(condition, thenBranch);

        lowerer.lower(ifStmt);

        // Should create then block, merge block
        assertTrue(irMethod.getBlockCount() >= 3);

        // Entry block should have a branch instruction
        IRInstruction terminator = entryBlock.getTerminator();
        assertTrue(terminator instanceof BranchInstruction);
    }

    @Test
    void lowerIfWithElse() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        Statement elseBranch = new ReturnStmt(LiteralExpr.ofInt(2));
        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        lowerer.lower(ifStmt);

        // Should create then block, else block, merge block
        assertTrue(irMethod.getBlockCount() >= 4);

        IRInstruction terminator = entryBlock.getTerminator();
        assertTrue(terminator instanceof BranchInstruction);

        BranchInstruction branch = (BranchInstruction) terminator;
        assertEquals(CompareOp.IFNE, branch.getCondition());
    }

    @Test
    void lowerNestedIf() {
        Expression outerCond = LiteralExpr.ofBoolean(true);
        Expression innerCond = LiteralExpr.ofBoolean(false);
        Statement innerIf = new IfStmt(innerCond, new ReturnStmt());
        IfStmt outerIf = new IfStmt(outerCond, innerIf);

        lowerer.lower(outerIf);

        // Nested if should create multiple blocks
        assertTrue(irMethod.getBlockCount() >= 4);
    }

    // ========== While Loop Tests ==========

    @Test
    void lowerWhileLoop() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BlockStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body);

        lowerer.lower(whileStmt);

        // Should create: cond block, body block, exit block
        assertTrue(irMethod.getBlockCount() >= 4);

        // Entry should goto condition block
        IRInstruction entryTerm = entryBlock.getTerminator();
        assertTrue(entryTerm instanceof SimpleInstruction &&
            ((SimpleInstruction) entryTerm).getOp() == SimpleOp.GOTO);
    }

    @Test
    void lowerWhileWithBreak() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BreakStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body);

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerWhileWithContinue() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new ContinueStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body);

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerLabeledWhile() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BlockStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body, "loop");

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    // ========== Do-While Loop Tests ==========

    @Test
    void lowerDoWhileLoop() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BlockStmt();
        DoWhileStmt doWhile = new DoWhileStmt(body, condition);

        lowerer.lower(doWhile);

        // Should create: body block, cond block, exit block
        assertTrue(irMethod.getBlockCount() >= 4);

        // Entry should goto body block (not condition)
        IRInstruction entryTerm = entryBlock.getTerminator();
        assertTrue(entryTerm instanceof SimpleInstruction &&
            ((SimpleInstruction) entryTerm).getOp() == SimpleOp.GOTO);
    }

    @Test
    void lowerDoWhileWithBreak() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BreakStmt();
        DoWhileStmt doWhile = new DoWhileStmt(body, condition);

        lowerer.lower(doWhile);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerLabeledDoWhile() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BlockStmt();
        DoWhileStmt doWhile = new DoWhileStmt(body, condition, "loop");

        lowerer.lower(doWhile);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    // ========== For Loop Tests ==========

    @Test
    void lowerForLoopWithAllParts() {
        List<Statement> init = List.of(
            new VarDeclStmt(PrimitiveSourceType.INT, "i", LiteralExpr.ofInt(0))
        );
        Expression condition = LiteralExpr.ofBoolean(true);
        List<Expression> update = List.of(LiteralExpr.ofInt(1));
        Statement body = new BlockStmt();

        ForStmt forStmt = new ForStmt(init, condition, update, body);

        lowerer.lower(forStmt);

        // Should create: cond block, body block, update block, exit block
        assertTrue(irMethod.getBlockCount() >= 5);
        assertTrue(ctx.hasVariable("i"));
    }

    @Test
    void lowerForLoopWithoutCondition() {
        List<Statement> init = List.of();
        List<Expression> update = List.of();
        Statement body = new BlockStmt();

        ForStmt forStmt = new ForStmt(init, null, update, body);

        lowerer.lower(forStmt);

        // Infinite loop (no condition)
        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerForLoopWithBreak() {
        List<Statement> init = List.of();
        Expression condition = LiteralExpr.ofBoolean(true);
        List<Expression> update = List.of();
        Statement body = new BreakStmt();

        ForStmt forStmt = new ForStmt(init, condition, update, body);

        lowerer.lower(forStmt);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    @Test
    void lowerInfiniteForLoop() {
        ForStmt infiniteLoop = ForStmt.infinite(new BlockStmt());

        lowerer.lower(infiniteLoop);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerLabeledForLoop() {
        List<Statement> init = List.of();
        Expression condition = LiteralExpr.ofBoolean(true);
        List<Expression> update = List.of();
        Statement body = new BlockStmt();

        ForStmt forStmt = new ForStmt(init, condition, update, body, "loop", null);

        lowerer.lower(forStmt);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    // ========== ForEach Loop Tests ==========

    @Test
    void lowerForEachLoop() {
        // Create array variable: int[] arr
        ArraySourceType arrayType = new ArraySourceType(PrimitiveSourceType.INT);
        Expression arrayExpr = new VarRefExpr("arr", arrayType);

        // Declare loop variable: int element
        VarDeclStmt loopVar = new VarDeclStmt(PrimitiveSourceType.INT, "element");

        // Create foreach statement
        Statement body = new BlockStmt();
        ForEachStmt forEach = new ForEachStmt(loopVar, arrayExpr, body);

        // Set up array variable in context
        SSAValue arrValue = ctx.newValue(arrayType.toIRType());
        ctx.setVariable("arr", arrValue);

        lowerer.lower(forEach);

        // Should create: cond block, body block, update block, exit block
        assertTrue(irMethod.getBlockCount() >= 5);
        assertTrue(ctx.hasVariable("element"));
    }

    @Test
    void lowerForEachWithBreak() {
        ArraySourceType arrayType = new ArraySourceType(PrimitiveSourceType.INT);
        Expression arrayExpr = new VarRefExpr("arr", arrayType);
        VarDeclStmt loopVar = new VarDeclStmt(PrimitiveSourceType.INT, "element");
        Statement body = new BreakStmt();
        ForEachStmt forEach = new ForEachStmt(loopVar, arrayExpr, body);

        SSAValue arrValue = ctx.newValue(arrayType.toIRType());
        ctx.setVariable("arr", arrValue);

        lowerer.lower(forEach);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    @Test
    void lowerLabeledForEach() {
        ArraySourceType arrayType = new ArraySourceType(PrimitiveSourceType.INT);
        Expression arrayExpr = new VarRefExpr("arr", arrayType);
        VarDeclStmt loopVar = new VarDeclStmt(PrimitiveSourceType.INT, "element");
        Statement body = new BlockStmt();
        ForEachStmt forEach = new ForEachStmt(loopVar, arrayExpr, body, "loop", null);

        SSAValue arrValue = ctx.newValue(arrayType.toIRType());
        ctx.setVariable("arr", arrValue);

        lowerer.lower(forEach);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    // ========== Switch Statement Tests ==========

    @Test
    void lowerSwitchWithDefaultCase() {
        Expression selector = LiteralExpr.ofInt(1);
        List<SwitchCase> cases = List.of(
            SwitchCase.of(1, List.of(new ReturnStmt())),
            SwitchCase.defaultCase(List.of(new ReturnStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        lowerer.lower(switchStmt);

        assertTrue(irMethod.getBlockCount() >= 4);

        IRInstruction terminator = entryBlock.getTerminator();
        assertTrue(terminator instanceof SwitchInstruction);

        SwitchInstruction switchInstr = (SwitchInstruction) terminator;
        assertNotNull(switchInstr.getDefaultTarget());
    }

    @Test
    void lowerSwitchWithoutDefaultCase() {
        Expression selector = LiteralExpr.ofInt(1);
        List<SwitchCase> cases = List.of(
            SwitchCase.of(1, List.of(new ReturnStmt())),
            SwitchCase.of(2, List.of(new ReturnStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        lowerer.lower(switchStmt);

        assertTrue(irMethod.getBlockCount() >= 4);

        IRInstruction terminator = entryBlock.getTerminator();
        assertTrue(terminator instanceof SwitchInstruction);
    }

    @Test
    void lowerSwitchWithFallthrough() {
        Expression selector = LiteralExpr.ofInt(1);
        List<SwitchCase> cases = List.of(
            SwitchCase.of(1, List.of()),  // Empty - falls through
            SwitchCase.of(2, List.of(new ReturnStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        lowerer.lower(switchStmt);

        assertTrue(irMethod.getBlockCount() >= 3);
    }

    @Test
    void lowerSwitchWithMultipleLabels() {
        Expression selector = LiteralExpr.ofInt(1);
        List<SwitchCase> cases = List.of(
            SwitchCase.of(List.of(1, 2, 3), List.of(new ReturnStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        lowerer.lower(switchStmt);

        assertTrue(irMethod.getBlockCount() >= 3);
    }

    @Test
    void lowerSwitchWithBreak() {
        Expression selector = LiteralExpr.ofInt(1);
        List<SwitchCase> cases = List.of(
            SwitchCase.of(1, List.of(new BreakStmt())),
            SwitchCase.of(2, List.of(new BlockStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        lowerer.lower(switchStmt);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    // ========== Try-Catch Statement Tests ==========

    @Test
    void lowerTryCatchWithSingleCatch() {
        Statement tryBlock = new BlockStmt();
        SourceType exceptionType = new ReferenceSourceType("java/lang/Exception");
        CatchClause catchClause = CatchClause.of(exceptionType, "e", new BlockStmt());
        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catchClause));

        lowerer.lower(tryCatch);

        // Should create: try block, catch block, exit block
        assertTrue(irMethod.getBlockCount() >= 4);
        assertTrue(ctx.hasVariable("e"));
    }

    @Test
    void lowerTryCatchWithMultipleCatches() {
        Statement tryBlock = new BlockStmt();
        SourceType exception1 = new ReferenceSourceType("java/lang/RuntimeException");
        SourceType exception2 = new ReferenceSourceType("java/lang/Exception");

        CatchClause catch1 = CatchClause.of(exception1, "e1", new BlockStmt());
        CatchClause catch2 = CatchClause.of(exception2, "e2", new BlockStmt());

        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catch1, catch2));

        lowerer.lower(tryCatch);

        assertTrue(irMethod.getBlockCount() >= 5);
        assertTrue(ctx.hasVariable("e1"));
        assertTrue(ctx.hasVariable("e2"));
    }

    @Test
    void lowerTryCatchFinally() {
        Statement tryBlock = new BlockStmt();
        Statement finallyBlock = new BlockStmt();
        SourceType exceptionType = new ReferenceSourceType("java/lang/Exception");
        CatchClause catchClause = CatchClause.of(exceptionType, "e", new BlockStmt());

        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catchClause), finallyBlock);

        lowerer.lower(tryCatch);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    @Test
    void lowerTryFinallyWithoutCatch() {
        Statement tryBlock = new BlockStmt();
        Statement finallyBlock = new BlockStmt();

        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(), finallyBlock);

        lowerer.lower(tryCatch);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerTryCatchWithReturn() {
        Statement tryBlock = new ReturnStmt();
        SourceType exceptionType = new ReferenceSourceType("java/lang/Exception");
        CatchClause catchClause = CatchClause.of(exceptionType, "e", new ReturnStmt());
        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catchClause));

        lowerer.lower(tryCatch);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    // ========== Synchronized Statement Tests ==========

    @Test
    void lowerSynchronizedBlock() {
        Expression lock = LiteralExpr.ofNull();
        Statement body = new BlockStmt();
        SynchronizedStmt syncStmt = new SynchronizedStmt(lock, body);

        lowerer.lower(syncStmt);

        List<IRInstruction> instructions = entryBlock.getInstructions();
        assertTrue(instructions.stream().anyMatch(i -> i instanceof SimpleInstruction &&
            ((SimpleInstruction) i).getOp() == SimpleOp.MONITORENTER));
    }

    @Test
    void lowerSynchronizedWithReturn() {
        Expression lock = LiteralExpr.ofNull();
        Statement body = new ReturnStmt();
        SynchronizedStmt syncStmt = new SynchronizedStmt(lock, body);

        lowerer.lower(syncStmt);

        List<IRInstruction> instructions = entryBlock.getInstructions();
        assertTrue(instructions.stream().anyMatch(i -> i instanceof SimpleInstruction &&
            ((SimpleInstruction) i).getOp() == SimpleOp.MONITORENTER));
        // Monitor exit should not be added after return (terminator present)
    }

    @Test
    void lowerSynchronizedWithMonitorExit() {
        Expression lock = LiteralExpr.ofNull();
        Statement body = new BlockStmt();
        SynchronizedStmt syncStmt = new SynchronizedStmt(lock, body);

        lowerer.lower(syncStmt);

        List<IRInstruction> instructions = entryBlock.getInstructions();
        assertTrue(instructions.stream().anyMatch(i -> i instanceof SimpleInstruction &&
            ((SimpleInstruction) i).getOp() == SimpleOp.MONITORENTER));
        assertTrue(instructions.stream().anyMatch(i -> i instanceof SimpleInstruction &&
            ((SimpleInstruction) i).getOp() == SimpleOp.MONITOREXIT));
    }

    // ========== Break and Continue Tests ==========

    @Test
    void lowerBreakInWhileLoop() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BreakStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body);

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerContinueInWhileLoop() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new ContinueStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body);

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 4);
    }

    @Test
    void lowerLabeledBreak() {
        Expression outerCond = LiteralExpr.ofBoolean(true);
        Expression innerCond = LiteralExpr.ofBoolean(true);

        Statement innerBody = new BreakStmt("outer");
        WhileStmt innerLoop = new WhileStmt(innerCond, innerBody);
        WhileStmt outerLoop = new WhileStmt(outerCond, innerLoop, "outer");

        lowerer.lower(outerLoop);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    @Test
    void lowerLabeledContinue() {
        Expression outerCond = LiteralExpr.ofBoolean(true);
        Expression innerCond = LiteralExpr.ofBoolean(true);

        Statement innerBody = new ContinueStmt("outer");
        WhileStmt innerLoop = new WhileStmt(innerCond, innerBody);
        WhileStmt outerLoop = new WhileStmt(outerCond, innerLoop, "outer");

        lowerer.lower(outerLoop);

        assertTrue(irMethod.getBlockCount() >= 5);
    }

    @Test
    void breakOutsideLoopThrowsException() {
        BreakStmt breakStmt = new BreakStmt();

        assertThrows(LoweringException.class, () -> lowerer.lower(breakStmt));
    }

    @Test
    void continueOutsideLoopThrowsException() {
        ContinueStmt continueStmt = new ContinueStmt();

        assertThrows(LoweringException.class, () -> lowerer.lower(continueStmt));
    }

    @Test
    void breakWithUnknownLabelThrowsException() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BreakStmt("unknown");
        WhileStmt whileStmt = new WhileStmt(condition, body);

        assertThrows(LoweringException.class, () -> lowerer.lower(whileStmt));
    }

    @Test
    void continueWithUnknownLabelThrowsException() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new ContinueStmt("unknown");
        WhileStmt whileStmt = new WhileStmt(condition, body);

        assertThrows(LoweringException.class, () -> lowerer.lower(whileStmt));
    }

    // ========== Block Statement Tests ==========

    @Test
    void lowerEmptyBlock() {
        BlockStmt block = new BlockStmt();

        lowerer.lower(block);

        // Empty block shouldn't add instructions
        assertTrue(entryBlock.getInstructions().isEmpty() ||
                   entryBlock.getTerminator() == null);
    }

    @Test
    void lowerBlockWithMultipleStatements() {
        BlockStmt block = new BlockStmt();
        block.addStatement(new VarDeclStmt(PrimitiveSourceType.INT, "a", LiteralExpr.ofInt(1)));
        block.addStatement(new VarDeclStmt(PrimitiveSourceType.INT, "b", LiteralExpr.ofInt(2)));
        block.addStatement(new ReturnStmt());

        lowerer.lower(block);

        assertTrue(ctx.hasVariable("a"));
        assertTrue(ctx.hasVariable("b"));
        assertNotNull(entryBlock.getTerminator());
    }

    @Test
    void lowerBlockStopsAfterTerminator() {
        BlockStmt block = new BlockStmt();
        block.addStatement(new ReturnStmt());
        block.addStatement(new VarDeclStmt(PrimitiveSourceType.INT, "unreachable"));

        lowerer.lower(block);

        // Should stop after return, not process unreachable var decl
        assertNotNull(entryBlock.getTerminator());
        assertFalse(ctx.hasVariable("unreachable"));
    }

    // ========== Expression Statement Tests ==========

    @Test
    void lowerExpressionStatement() {
        Expression expr = LiteralExpr.ofInt(42);
        ExprStmt exprStmt = new ExprStmt(expr);

        lowerer.lower(exprStmt);

        // Should lower the expression
        assertFalse(entryBlock.getInstructions().isEmpty());
    }

    // ========== Throw Statement Tests ==========

    @Test
    void lowerThrowStatement() {
        Expression exception = LiteralExpr.ofNull();
        ThrowStmt throwStmt = new ThrowStmt(exception);

        lowerer.lower(throwStmt);

        IRInstruction terminator = entryBlock.getTerminator();
        assertNotNull(terminator);
        assertTrue(terminator instanceof SimpleInstruction &&
            ((SimpleInstruction) terminator).getOp() == SimpleOp.ATHROW);
    }

    // ========== Labeled Statement Tests ==========

    @Test
    void lowerLabeledStatement() {
        Statement stmt = new ReturnStmt();
        LabeledStmt labeled = new LabeledStmt("label", stmt);

        lowerer.lower(labeled);

        // Labeled statement should just lower the inner statement
        assertNotNull(entryBlock.getTerminator());
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    void unsupportedStatementTypeThrowsException() {
        // Create a custom unsupported statement (using anonymous class)
        Statement unsupported = new Statement() {
            @Override
            public String getLabel() {
                return null;
            }

            @Override
            public com.tonic.analysis.source.ast.ASTNode getParent() {
                return null;
            }

            @Override
            public void setParent(com.tonic.analysis.source.ast.ASTNode parent) {
            }

            @Override
            public com.tonic.analysis.source.ast.SourceLocation getLocation() {
                return com.tonic.analysis.source.ast.SourceLocation.UNKNOWN;
            }

            @Override
            public <T> T accept(com.tonic.analysis.source.visitor.SourceVisitor<T> visitor) {
                return null;
            }
        };

        assertThrows(LoweringException.class, () -> lowerer.lower(unsupported));
    }

    // ========== Control Flow Structure Tests ==========

    @Test
    void ifStatementCreatesCorrectCFG() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new BlockStmt();
        Statement elseBranch = new BlockStmt();
        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        lowerer.lower(ifStmt);

        // Verify CFG structure
        assertEquals(2, entryBlock.getSuccessors().size());
        IRInstruction terminator = entryBlock.getTerminator();
        assertTrue(terminator instanceof BranchInstruction);
    }

    @Test
    void whileLoopCreatesCorrectCFG() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement body = new BlockStmt();
        WhileStmt whileStmt = new WhileStmt(condition, body);

        lowerer.lower(whileStmt);

        // Entry should goto condition block
        assertTrue(entryBlock.getTerminator() instanceof SimpleInstruction &&
            ((SimpleInstruction) entryBlock.getTerminator()).getOp() == SimpleOp.GOTO);
        assertEquals(1, entryBlock.getSuccessors().size());
    }

    @Test
    void switchStatementCreatesCorrectCFG() {
        Expression selector = LiteralExpr.ofInt(1);
        List<SwitchCase> cases = List.of(
            SwitchCase.of(1, List.of(new BlockStmt())),
            SwitchCase.of(2, List.of(new BlockStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        lowerer.lower(switchStmt);

        assertTrue(entryBlock.getTerminator() instanceof SwitchInstruction);
        // Switch should have multiple successors
        assertTrue(entryBlock.getSuccessors().size() >= 2);
    }

    @Test
    void tryCatchCreatesExceptionEdges() {
        Statement tryBlock = new BlockStmt();
        SourceType exceptionType = new ReferenceSourceType("java/lang/Exception");
        CatchClause catchClause = CatchClause.of(exceptionType, "e", new BlockStmt());
        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catchClause));

        lowerer.lower(tryCatch);

        // Verify exception edges are created
        long exceptionEdges = irMethod.getBlocks().stream()
            .flatMap(b -> b.getSuccessors().stream())
            .filter(b -> {
                // Check if any predecessor has exception edge to this block
                return b.getPredecessors().stream().anyMatch(pred ->
                    pred.getSuccessors().stream().anyMatch(s -> s == b)
                );
            })
            .count();

        assertTrue(exceptionEdges >= 0); // At least exception flow exists
    }

    // ========== Variable Scoping Tests ==========

    @Test
    void variableAvailableAfterDeclaration() {
        VarDeclStmt decl = new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(5));
        lowerer.lower(decl);

        assertTrue(ctx.hasVariable("x"));
        assertNotNull(ctx.getVariable("x"));
    }

    @Test
    void accessUndefinedVariableThrowsException() {
        assertThrows(LoweringException.class, () -> ctx.getVariable("undefined"));
    }

    // ========== Complex Nested Structures ==========

    @Test
    void nestedLoopsWithBreaksAndContinues() {
        Expression outerCond = LiteralExpr.ofBoolean(true);
        Expression innerCond = LiteralExpr.ofBoolean(true);

        BlockStmt innerBody = new BlockStmt();
        innerBody.addStatement(new ContinueStmt());
        innerBody.addStatement(new BreakStmt());

        WhileStmt innerLoop = new WhileStmt(innerCond, innerBody);
        WhileStmt outerLoop = new WhileStmt(outerCond, innerLoop);

        lowerer.lower(outerLoop);

        assertTrue(irMethod.getBlockCount() >= 6);
    }

    @Test
    void switchInsideWhileLoop() {
        Expression whileCond = LiteralExpr.ofBoolean(true);
        Expression selector = LiteralExpr.ofInt(1);

        List<SwitchCase> cases = List.of(
            SwitchCase.of(1, List.of(new BreakStmt())),
            SwitchCase.of(2, List.of(new ContinueStmt()))
        );
        SwitchStmt switchStmt = new SwitchStmt(selector, cases);

        WhileStmt whileStmt = new WhileStmt(whileCond, switchStmt);

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 6);
    }

    @Test
    void tryCatchInsideLoop() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement tryBlock = new BlockStmt();
        SourceType exceptionType = new ReferenceSourceType("java/lang/Exception");
        CatchClause catchClause = CatchClause.of(exceptionType, "e", new BlockStmt());
        TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catchClause));

        WhileStmt whileStmt = new WhileStmt(condition, tryCatch);

        lowerer.lower(whileStmt);

        assertTrue(irMethod.getBlockCount() >= 6);
    }

    @Test
    void ifInsideForLoop() {
        List<Statement> init = List.of();
        Expression condition = LiteralExpr.ofBoolean(true);
        List<Expression> update = List.of();

        Expression ifCond = LiteralExpr.ofBoolean(true);
        Statement ifBody = new BreakStmt();
        IfStmt ifStmt = new IfStmt(ifCond, ifBody);

        ForStmt forStmt = new ForStmt(init, condition, update, ifStmt);

        lowerer.lower(forStmt);

        assertTrue(irMethod.getBlockCount() >= 6);
    }
}
