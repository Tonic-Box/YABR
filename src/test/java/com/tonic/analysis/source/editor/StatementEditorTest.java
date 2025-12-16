package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.editor.matcher.StmtMatcher;
import com.tonic.analysis.source.editor.util.ASTFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for StatementEditor.
 * Covers statement insertion, removal, block manipulation, and control flow statement editing.
 */
class StatementEditorTest {

    private ASTFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ASTFactory();
    }

    // ========== Constructor Tests ==========

    @Test
    void createStatementEditor() {
        BlockStmt body = factory.block();
        StatementEditor editor = new StatementEditor(body, "testMethod", "()V", "com/example/Test");
        assertNotNull(editor);
    }

    // ========== Statement Insertion Tests ==========

    @Test
    void insertBeforeReturn() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(42));
        BlockStmt body = factory.block(returnStmt);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("logging"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> Replacement.insertBefore(logStmt));
        editor.apply();

        assertEquals(2, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof ReturnStmt);
    }

    @Test
    void insertBeforeAllReturns() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnStmt(factory.intLiteral(2));
        BlockStmt body = factory.block(ret1, ret2);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("log"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeReturns(logStmt);
        editor.apply();

        assertEquals(4, body.getStatements().size());
        // log, return1, log, return2
    }

    @Test
    void insertBeforeValueReturns() {
        ReturnStmt valueReturn = factory.returnStmt(factory.intLiteral(42));
        ReturnStmt voidReturn = factory.returnVoid();
        BlockStmt body = factory.block(valueReturn, voidReturn);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("has value"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeValueReturns(logStmt);
        editor.apply();

        // Should insert only before value return
        assertEquals(3, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof ReturnStmt);
        assertFalse(((ReturnStmt) body.getStatements().get(1)).isVoidReturn());
    }

    @Test
    void insertBeforeVoidReturns() {
        ReturnStmt valueReturn = factory.returnStmt(factory.intLiteral(42));
        ReturnStmt voidReturn = factory.returnVoid();
        BlockStmt body = factory.block(valueReturn, voidReturn);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("void return"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeVoidReturns(logStmt);
        editor.apply();

        // Should insert only before void return
        assertEquals(3, body.getStatements().size());
        assertTrue(body.getStatements().get(1) instanceof ExprStmt);
        assertTrue(body.getStatements().get(2) instanceof ReturnStmt);
        assertTrue(((ReturnStmt) body.getStatements().get(2)).isVoidReturn());
    }

    @Test
    void insertBeforeThrows() {
        ThrowStmt throwStmt = factory.throwStmt(factory.variable("ex"));
        BlockStmt body = factory.block(throwStmt);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("about to throw"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeThrows(logStmt);
        editor.apply();

        assertEquals(2, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof ThrowStmt);
    }

    // ========== Statement Removal Tests ==========

    @Test
    void removeStatement() {
        Statement stmt1 = factory.exprStmt(factory.intLiteral(1));
        Statement stmt2 = factory.exprStmt(factory.intLiteral(2));
        Statement stmt3 = factory.exprStmt(factory.intLiteral(3));
        BlockStmt body = factory.block(stmt1, stmt2, stmt3);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onStmt(StmtMatcher.any(), (ctx, stmt) -> {
            // Remove statement with literal value 2
            if (stmt instanceof ExprStmt) {
                Expression expr = ((ExprStmt) stmt).getExpression();
                if (expr instanceof LiteralExpr && Integer.valueOf(2).equals(((LiteralExpr) expr).getValue())) {
                    return Replacement.remove();
                }
            }
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(2, body.getStatements().size());
        // Verify remaining statements have values 1 and 3
        Expression expr1 = ((ExprStmt) body.getStatements().get(0)).getExpression();
        Expression expr3 = ((ExprStmt) body.getStatements().get(1)).getExpression();
        assertEquals(1, ((LiteralExpr) expr1).getValue());
        assertEquals(3, ((LiteralExpr) expr3).getValue());
    }

    @Test
    void removeStatementsByType() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ExprStmt expr = factory.exprStmt(factory.intLiteral(2));
        ReturnStmt ret2 = factory.returnStmt(factory.intLiteral(3));
        BlockStmt body = factory.block(ret1, expr, ret2);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.removeStmts(ReturnStmt.class);
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertSame(expr, body.getStatements().get(0));
    }

    @Test
    void removeStatementsByMatcher() {
        ReturnStmt valueReturn = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt voidReturn = factory.returnVoid();
        BlockStmt body = factory.block(valueReturn, voidReturn);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.removeStmts(StmtMatcher.voidReturn());
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertSame(valueReturn, body.getStatements().get(0));
    }

    // ========== Block Manipulation Tests ==========

    @Test
    void replaceStatementWithBlock() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(42));
        BlockStmt body = factory.block(returnStmt);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> {
            Statement log1 = factory.exprStmt(factory.stringLiteral("log1"));
            Statement log2 = factory.exprStmt(factory.stringLiteral("log2"));
            Statement newRet = factory.returnStmt(factory.intLiteral(100));
            return Replacement.withBlock(log1, log2, newRet);
        });
        editor.apply();

        assertEquals(3, body.getStatements().size());
    }

    @Test
    void wrapLoops() {
        WhileStmt whileLoop = new WhileStmt(factory.boolLiteral(true), factory.block());
        BlockStmt body = factory.block(whileLoop);

        Statement before = factory.exprStmt(factory.stringLiteral("before"));
        Statement after = factory.exprStmt(factory.stringLiteral("after"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.wrapLoops(before, after);
        editor.apply();

        assertEquals(3, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof WhileStmt);
        assertTrue(body.getStatements().get(2) instanceof ExprStmt);
    }

    @Test
    void addStatementsToEmptyBlock() {
        BlockStmt body = factory.block();

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        // No statements to match, so handlers won't fire
        editor.onReturn((ctx, ret) -> Replacement.keep());
        editor.apply();

        assertEquals(0, body.getStatements().size());
    }

    @Test
    void modifyNestedBlock() {
        // Create: if (true) { return 1; }
        ReturnStmt innerReturn = factory.returnStmt(factory.intLiteral(1));
        IfStmt ifStmt = factory.ifStmt(factory.boolLiteral(true), factory.block(innerReturn));
        BlockStmt body = factory.block(ifStmt);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("log"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeReturns(logStmt);
        editor.apply();

        // Check that log was inserted inside the if block
        IfStmt modifiedIf = (IfStmt) body.getStatements().get(0);
        BlockStmt thenBlock = (BlockStmt) modifiedIf.getThenBranch();
        assertEquals(2, thenBlock.getStatements().size());
    }

    // ========== Control Flow Statement Editing Tests ==========

    @Test
    void modifyIfStatement() {
        IfStmt ifStmt = factory.ifStmt(factory.boolLiteral(true), factory.block());
        BlockStmt body = factory.block(ifStmt);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onIf((ctx, i) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void modifyLoopStatement() {
        WhileStmt whileLoop = new WhileStmt(factory.boolLiteral(true), factory.block());
        ForStmt forLoop = new ForStmt(null, null, null, factory.block());
        BlockStmt body = factory.block(whileLoop, forLoop);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onLoop((ctx, loop) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(2, count[0]);
    }

    @Test
    void modifyTryCatchStatement() {
        TryCatchStmt tryCatch = new TryCatchStmt(
            factory.block(),
            Arrays.asList(CatchClause.of(new ReferenceSourceType("java/lang/Exception"), "e", factory.block())),
            null
        );
        BlockStmt body = factory.block(tryCatch);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onTryCatch((ctx, tc) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void modifyAssignment() {
        BinaryExpr assignment = factory.assign(
            factory.variable("x"),
            factory.intLiteral(5),
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(assignment);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onAssignment((ctx, assign) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void replaceIfStatement() {
        IfStmt ifStmt = factory.ifStmt(factory.boolLiteral(true), factory.block());
        BlockStmt body = factory.block(ifStmt);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onIf((ctx, i) -> {
            // Replace with different if statement
            return Replacement.with(
                factory.ifStmt(factory.boolLiteral(false), factory.block())
            );
        });
        editor.apply();

        IfStmt result = (IfStmt) body.getStatements().get(0);
        Expression condition = result.getCondition();
        assertTrue(condition instanceof LiteralExpr);
        assertEquals(false, ((LiteralExpr) condition).getValue());
    }

    @Test
    void replaceLoopStatement() {
        WhileStmt whileLoop = new WhileStmt(factory.boolLiteral(true), factory.block());
        BlockStmt body = factory.block(whileLoop);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onLoop((ctx, loop) -> {
            // Replace with a return statement
            return Replacement.with(factory.returnVoid());
        });
        editor.apply();

        assertTrue(body.getStatements().get(0) instanceof ReturnStmt);
    }

    // ========== Find Operations Tests ==========

    @Test
    void findReturns() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnStmt(factory.intLiteral(2));
        ExprStmt expr = factory.exprStmt(factory.intLiteral(3));
        BlockStmt body = factory.block(ret1, expr, ret2);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> returns = editor.findReturns();

        assertEquals(2, returns.size());
        assertTrue(returns.get(0) instanceof ReturnStmt);
        assertTrue(returns.get(1) instanceof ReturnStmt);
    }

    @Test
    void findValueReturns() {
        ReturnStmt valueReturn = factory.returnStmt(factory.intLiteral(42));
        ReturnStmt voidReturn = factory.returnVoid();
        BlockStmt body = factory.block(valueReturn, voidReturn);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> valueReturns = editor.findValueReturns();

        assertEquals(1, valueReturns.size());
        assertFalse(((ReturnStmt) valueReturns.get(0)).isVoidReturn());
    }

    @Test
    void findVoidReturns() {
        ReturnStmt valueReturn = factory.returnStmt(factory.intLiteral(42));
        ReturnStmt voidReturn = factory.returnVoid();
        BlockStmt body = factory.block(valueReturn, voidReturn);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> voidReturns = editor.findVoidReturns();

        assertEquals(1, voidReturns.size());
        assertTrue(((ReturnStmt) voidReturns.get(0)).isVoidReturn());
    }

    @Test
    void findThrows() {
        ThrowStmt throw1 = factory.throwStmt(factory.variable("ex1"));
        ThrowStmt throw2 = factory.throwStmt(factory.variable("ex2"));
        BlockStmt body = factory.block(throw1, throw2);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> throwStatements = editor.findThrows();

        assertEquals(2, throwStatements.size());
    }

    @Test
    void findLoops() {
        WhileStmt whileLoop = new WhileStmt(factory.boolLiteral(true), factory.block());
        ForStmt forLoop = new ForStmt(null, null, null, factory.block());
        DoWhileStmt doWhileLoop = new DoWhileStmt(factory.block(), factory.boolLiteral(false));
        BlockStmt body = factory.block(whileLoop, forLoop, doWhileLoop);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> loops = editor.findLoops();

        assertEquals(3, loops.size());
    }

    @Test
    void findIfs() {
        IfStmt if1 = factory.ifStmt(factory.boolLiteral(true), factory.block());
        IfStmt if2 = factory.ifElseStmt(factory.boolLiteral(false), factory.block(), factory.block());
        BlockStmt body = factory.block(if1, if2);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> ifs = editor.findIfs();

        assertEquals(2, ifs.size());
    }

    @Test
    void findIfElses() {
        IfStmt ifOnly = factory.ifStmt(factory.boolLiteral(true), factory.block());
        IfStmt ifElse = factory.ifElseStmt(factory.boolLiteral(false), factory.block(), factory.block());
        BlockStmt body = factory.block(ifOnly, ifElse);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> ifElses = editor.findIfElses();

        assertEquals(1, ifElses.size());
        assertTrue(((IfStmt) ifElses.get(0)).hasElse());
    }

    @Test
    void findTryCatches() {
        TryCatchStmt tryCatch = new TryCatchStmt(
            factory.block(),
            Arrays.asList(CatchClause.of(new ReferenceSourceType("java/lang/Exception"), "e", factory.block())),
            null
        );
        BlockStmt body = factory.block(tryCatch);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> tryCatches = editor.findTryCatches();

        assertEquals(1, tryCatches.size());
    }

    @Test
    void findSwitches() {
        SwitchStmt switchStmt = new SwitchStmt(
            factory.intLiteral(1),
            Arrays.asList(SwitchCase.of(1, Arrays.asList(factory.returnVoid())))
        );
        BlockStmt body = factory.block(switchStmt);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> switches = editor.findSwitches();

        assertEquals(1, switches.size());
    }

    @Test
    void findVarDecls() {
        VarDeclStmt varDecl1 = factory.varDecl("int", "x", factory.intLiteral(5));
        VarDeclStmt varDecl2 = factory.varDecl("String", "s", factory.stringLiteral("test"));
        BlockStmt body = factory.block(varDecl1, varDecl2);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> varDecls = editor.findVarDecls();

        assertEquals(2, varDecls.size());
    }

    @Test
    void findStatementsWithMatcher() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnVoid();
        BlockStmt body = factory.block(ret1, ret2);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        List<Statement> valueReturns = editor.findStatements(StmtMatcher.returnWithValue());

        assertEquals(1, valueReturns.size());
    }

    // ========== Handler Registration Tests ==========

    @Test
    void onReturnHandler() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onThrowHandler() {
        ThrowStmt throwStmt = factory.throwStmt(factory.variable("ex"));
        BlockStmt body = factory.block(throwStmt);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onThrow((ctx, t) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onAnyStmtHandler() {
        ReturnStmt ret = factory.returnStmt(factory.intLiteral(1));
        ThrowStmt thr = factory.throwStmt(factory.variable("ex"));
        BlockStmt body = factory.block(ret, thr);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onAnyStmt((ctx, stmt) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(2, count[0]);
    }

    @Test
    void onStmtWithMatcher() {
        ReturnStmt valueReturn = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt voidReturn = factory.returnVoid();
        BlockStmt body = factory.block(valueReturn, voidReturn);

        int[] count = {0};
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onStmt(StmtMatcher.voidReturn(), (ctx, stmt) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    // ========== Complex Scenarios Tests ==========

    @Test
    void modifyNestedControlFlow() {
        // Create: if (true) { while (false) { return 1; } }
        ReturnStmt innerReturn = factory.returnStmt(factory.intLiteral(1));
        WhileStmt whileLoop = new WhileStmt(factory.boolLiteral(false), factory.block(innerReturn));
        IfStmt ifStmt = factory.ifStmt(factory.boolLiteral(true), factory.block(whileLoop));
        BlockStmt body = factory.block(ifStmt);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("log"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeReturns(logStmt);
        editor.apply();

        // Verify log was inserted in the nested structure
        IfStmt modifiedIf = (IfStmt) body.getStatements().get(0);
        BlockStmt ifBlock = (BlockStmt) modifiedIf.getThenBranch();
        WhileStmt modifiedWhile = (WhileStmt) ifBlock.getStatements().get(0);
        BlockStmt whileBlock = (BlockStmt) modifiedWhile.getBody();

        assertEquals(2, whileBlock.getStatements().size());
        assertTrue(whileBlock.getStatements().get(0) instanceof ExprStmt);
        assertTrue(whileBlock.getStatements().get(1) instanceof ReturnStmt);
    }

    @Test
    void multipleHandlersOnSameStatement() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        int[] handler1Count = {0};
        int[] handler2Count = {0};

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> {
            handler1Count[0]++;
            return Replacement.keep();
        });
        editor.onReturn((ctx, ret) -> {
            handler2Count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, handler1Count[0]);
        assertEquals(1, handler2Count[0]);
    }

    @Test
    void insertAndRemoveInSamePass() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnStmt(factory.intLiteral(2));
        BlockStmt body = factory.block(ret1, ret2);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("log"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");

        // First handler: insert before
        editor.onReturn((ctx, ret) -> Replacement.insertBefore(logStmt));

        // Second handler: remove the return itself
        editor.onReturn((ctx, ret) -> {
            if (((Integer) ((LiteralExpr) ret.getValue()).getValue()) == 1) {
                return Replacement.remove();
            }
            return Replacement.keep();
        });

        editor.apply();

        // Complex interaction - the exact result depends on handler ordering
        // But we should have fewer than 4 statements total
        assertTrue(body.getStatements().size() < 4);
    }

    // ========== Fluent API Tests ==========

    @Test
    void fluentMethodChaining() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(1));
        ThrowStmt throwStmt = factory.throwStmt(factory.variable("ex"));
        BlockStmt body = factory.block(returnStmt, throwStmt);

        int[] returnCount = {0};
        int[] throwCount = {0};

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test")
            .onReturn((ctx, ret) -> {
                returnCount[0]++;
                return Replacement.keep();
            })
            .onThrow((ctx, thr) -> {
                throwCount[0]++;
                return Replacement.keep();
            });

        editor.apply();

        assertEquals(1, returnCount[0]);
        assertEquals(1, throwCount[0]);
    }

    @Test
    void getDelegateEditor() {
        BlockStmt body = factory.block();
        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        ASTEditor delegate = editor.getDelegate();

        assertNotNull(delegate);
    }

    // ========== Edge Cases Tests ==========

    @Test
    void emptyBlock() {
        BlockStmt body = factory.block();

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeReturns(factory.exprStmt(factory.stringLiteral("log")));
        editor.apply();

        // No returns, so nothing inserted
        assertEquals(0, body.getStatements().size());
    }

    @Test
    void noMatchingStatements() {
        ExprStmt exprStmt = factory.exprStmt(factory.intLiteral(1));
        BlockStmt body = factory.block(exprStmt);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> Replacement.keep()); // Won't match
        editor.apply();

        assertEquals(1, body.getStatements().size());
    }

    @Test
    void keepReplacement() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> Replacement.keep());
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertSame(returnStmt, body.getStatements().get(0));
    }

    @Test
    void replaceWithSameStatement() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.onReturn((ctx, ret) -> {
            // Replace with identical return
            return Replacement.with(factory.returnStmt(factory.intLiteral(5)));
        });
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ReturnStmt);
        assertEquals(5, ((Integer) ((LiteralExpr) ((ReturnStmt) body.getStatements().get(0)).getValue()).getValue()));
    }

    @Test
    void traverseIfElseBranches() {
        ReturnStmt thenReturn = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt elseReturn = factory.returnStmt(factory.intLiteral(2));
        IfStmt ifElse = factory.ifElseStmt(
            factory.boolLiteral(true),
            factory.block(thenReturn),
            factory.block(elseReturn)
        );
        BlockStmt body = factory.block(ifElse);

        Statement logStmt = factory.exprStmt(factory.stringLiteral("log"));

        StatementEditor editor = new StatementEditor(body, "test", "()V", "com/example/Test");
        editor.insertBeforeReturns(logStmt);
        editor.apply();

        // Both branches should have log inserted
        IfStmt modifiedIf = (IfStmt) body.getStatements().get(0);
        BlockStmt thenBlock = (BlockStmt) modifiedIf.getThenBranch();
        BlockStmt elseBlock = (BlockStmt) modifiedIf.getElseBranch();

        assertEquals(2, thenBlock.getStatements().size());
        assertEquals(2, elseBlock.getStatements().size());
    }
}
