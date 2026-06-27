package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.editor.handler.ArrayAccessHandler;
import com.tonic.analysis.source.editor.matcher.ExprMatcher;
import com.tonic.analysis.source.editor.matcher.StmtMatcher;
import com.tonic.analysis.source.editor.util.ASTFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ASTEditor.
 * Covers node insertion, deletion, replacement, tree traversal, and multiple edits.
 */
class ASTEditorTest {

    private ASTFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ASTFactory();
    }

    // ========== Constructor Tests ==========

    @Test
    void createEditorWithFullContext() {
        BlockStmt body = factory.block();
        ASTEditor editor = new ASTEditor(body, "testMethod", "()V", "com/example/Test");
        assertNotNull(editor);
    }

    @Test
    void createEditorWithMinimalContext() {
        BlockStmt body = factory.block();
        ASTEditor editor = new ASTEditor(body);
        assertNotNull(editor);
    }

    // ========== Node Insertion Tests ==========

    @Test
    void insertStatementBefore() {
        // Create method body: return 42;
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(42));
        BlockStmt body = factory.block(returnStmt);

        // Insert logging before return
        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            Statement logStmt = factory.methodCall("log")
                .on("java/lang/System")
                .withArgs(factory.stringLiteral("returning"))
                .asStatement();
            return Replacement.insertBefore(logStmt);
        });
        editor.apply();

        assertEquals(2, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof ReturnStmt);
    }

    @Test
    void insertStatementAfter() {
        // Create method body: int x = 5;
        VarDeclStmt varDecl = factory.varDecl("int", "x", factory.intLiteral(5));
        BlockStmt body = factory.block(varDecl);

        // Insert statement after variable declaration
        ASTEditor editor = new ASTEditor(body);
        editor.onStmt(StmtMatcher.varDeclStmt(), (ctx, stmt) -> {
            Statement logStmt = factory.methodCall("println")
                .on("java/lang/System")
                .withArgs(factory.stringLiteral("declared"))
                .asStatement();
            return Replacement.insertAfter(logStmt);
        });
        editor.apply();

        assertEquals(2, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof VarDeclStmt);
        assertTrue(body.getStatements().get(1) instanceof ExprStmt);
    }

    @Test
    void insertMultipleStatementsBefore() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(10));
        BlockStmt body = factory.block(returnStmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            Statement stmt1 = factory.exprStmt(factory.intLiteral(1));
            Statement stmt2 = factory.exprStmt(factory.intLiteral(2));
            return Replacement.insertBefore(stmt1, stmt2);
        });
        editor.apply();

        assertEquals(3, body.getStatements().size());
    }

    @Test
    void insertMultipleStatementsAfter() {
        VarDeclStmt varDecl = factory.varDecl("int", "x", factory.intLiteral(5));
        BlockStmt body = factory.block(varDecl);

        ASTEditor editor = new ASTEditor(body);
        editor.onStmt(StmtMatcher.varDeclStmt(), (ctx, stmt) -> {
            Statement stmt1 = factory.exprStmt(factory.intLiteral(1));
            Statement stmt2 = factory.exprStmt(factory.intLiteral(2));
            return Replacement.insertAfter(stmt1, stmt2);
        });
        editor.apply();

        assertEquals(3, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof VarDeclStmt);
    }

    // ========== Node Deletion Tests ==========

    @Test
    void removeStatement() {
        // Create body with 3 statements
        Statement stmt1 = factory.exprStmt(factory.intLiteral(1));
        Statement stmt2 = factory.exprStmt(factory.intLiteral(2));
        Statement stmt3 = factory.exprStmt(factory.intLiteral(3));
        BlockStmt body = factory.block(stmt1, stmt2, stmt3);

        // Remove middle statement
        ASTEditor editor = new ASTEditor(body);
        editor.onStmt(StmtMatcher.any(), (ctx, stmt) -> {
            if (stmt == stmt2) {
                return Replacement.remove();
            }
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(2, body.getStatements().size());
        assertSame(stmt1, body.getStatements().get(0));
        assertSame(stmt3, body.getStatements().get(1));
    }

    @Test
    void removeAllReturns() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnStmt(factory.intLiteral(2));
        BlockStmt body = factory.block(ret1, ret2);

        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> Replacement.remove());
        editor.apply();

        assertEquals(0, body.getStatements().size());
    }

    @Test
    void removeFirstStatement() {
        Statement stmt1 = factory.exprStmt(factory.intLiteral(1));
        Statement stmt2 = factory.exprStmt(factory.intLiteral(2));
        BlockStmt body = factory.block(stmt1, stmt2);

        ASTEditor editor = new ASTEditor(body);
        boolean[] first = {true};
        editor.onStmt(StmtMatcher.any(), (ctx, stmt) -> {
            if (first[0]) {
                first[0] = false;
                return Replacement.remove();
            }
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertSame(stmt2, body.getStatements().get(0));
    }

    @Test
    void removeLastStatement() {
        Statement stmt1 = factory.exprStmt(factory.intLiteral(1));
        Statement stmt2 = factory.exprStmt(factory.intLiteral(2));
        BlockStmt body = factory.block(stmt1, stmt2);

        ASTEditor editor = new ASTEditor(body);
        editor.onStmt(StmtMatcher.any(), (ctx, stmt) -> {
            if (ctx.getStatementIndex() == 1) {
                return Replacement.remove();
            }
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertSame(stmt1, body.getStatements().get(0));
    }

    // ========== Node Replacement Tests ==========

    @Test
    void replaceExpression() {
        // Create: System.out.println(value);
        Expression value = factory.variable("value");
        ExprStmt stmt = factory.exprStmt(value);
        BlockStmt body = factory.block(stmt);

        // Replace value with newValue at expression statement level
        ASTEditor editor = new ASTEditor(body);
        editor.onExpr(ExprMatcher.custom(expr -> expr instanceof VarRefExpr &&
            ((VarRefExpr) expr).getName().equals("value")),
            (ctx, expr) -> Replacement.with(factory.variable("newValue"))
        );
        editor.apply();

        ExprStmt modifiedStmt = (ExprStmt) body.getStatements().get(0);
        VarRefExpr newExpr = (VarRefExpr) modifiedStmt.getExpression();
        assertEquals("newValue", newExpr.getName());
    }

    @Test
    void replaceStatement() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            return Replacement.with(factory.returnStmt(factory.intLiteral(10)));
        });
        editor.apply();

        ReturnStmt newReturn = (ReturnStmt) body.getStatements().get(0);
        LiteralExpr literal = (LiteralExpr) newReturn.getValue();
        assertEquals(10, literal.getValue());
    }

    @Test
    void replaceStatementWithBlock() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(42));
        BlockStmt body = factory.block(returnStmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            Statement log = factory.exprStmt(factory.stringLiteral("log"));
            Statement newRet = factory.returnStmt(factory.intLiteral(100));
            return Replacement.withBlock(log, newRet);
        });
        editor.apply();

        assertEquals(2, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof ReturnStmt);
    }

    @Test
    void replaceMethodCall() {
        MethodCallExpr oldCall = factory.methodCall("oldMethod")
            .on("com/example/Service")
            .build();
        ExprStmt stmt = factory.exprStmt(oldCall);
        BlockStmt body = factory.block(stmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onMethodCall((ctx, call) -> {
            if (call.getMethodName().equals("oldMethod")) {
                return Replacement.with(
                    factory.methodCall("newMethod")
                        .on("com/example/Service")
                        .build()
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        MethodCallExpr newCall = (MethodCallExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("newMethod", newCall.getMethodName());
    }

    // ========== Tree Traversal Tests ==========

    @Test
    void traverseNestedBlocks() {
        // Create nested structure: if (true) { if (false) { return 1; } }
        ReturnStmt innerReturn = factory.returnStmt(factory.intLiteral(1));
        IfStmt innerIf = factory.ifStmt(factory.boolLiteral(false), factory.block(innerReturn));
        IfStmt outerIf = factory.ifStmt(factory.boolLiteral(true), factory.block(innerIf));
        BlockStmt body = factory.block(outerIf);

        // Track traversal depth
        int[] returnCount = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            returnCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, returnCount[0]);
    }

    @Test
    void traverseIfStatementBranches() {
        ReturnStmt thenReturn = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt elseReturn = factory.returnStmt(factory.intLiteral(2));
        IfStmt ifStmt = factory.ifElseStmt(
            factory.boolLiteral(true),
            factory.block(thenReturn),
            factory.block(elseReturn)
        );
        BlockStmt body = factory.block(ifStmt);

        int[] returnCount = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            returnCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(2, returnCount[0]);
    }

    @Test
    void traverseLoopBodies() {
        // Create: while (true) { return 5; }
        ReturnStmt returnInLoop = factory.returnStmt(factory.intLiteral(5));
        WhileStmt whileStmt = new WhileStmt(factory.boolLiteral(true), factory.block(returnInLoop));
        BlockStmt body = factory.block(whileStmt);

        int[] returnCount = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> {
            returnCount[0]++;
            assertTrue(ctx.isInLoop(), "Return should be inside loop");
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, returnCount[0]);
    }

    @Test
    void traverseMethodCallArguments() {
        // Create: print(compute(5))
        MethodCallExpr innerCall = factory.methodCall("compute")
            .on("com/example/Math")
            .withArgs(factory.intLiteral(5))
            .build();
        MethodCallExpr outerCall = factory.methodCall("print")
            .on("java/lang/System")
            .withArgs(innerCall)
            .build();
        ExprStmt stmt = factory.exprStmt(outerCall);
        BlockStmt body = factory.block(stmt);

        int[] callCount = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onMethodCall((ctx, call) -> {
            callCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(2, callCount[0]);
    }

    // ========== Multiple Edits Tests ==========

    @Test
    void applyMultipleHandlers() {
        // Create body with method call and return
        MethodCallExpr call = factory.methodCall("test").on("com/example/Test").build();
        ExprStmt callStmt = factory.exprStmt(call);
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(42));
        BlockStmt body = factory.block(callStmt, returnStmt);

        int[] methodCallCount = {0};
        int[] returnCount = {0};

        ASTEditor editor = new ASTEditor(body);
        editor.onMethodCall((ctx, c) -> {
            methodCallCount[0]++;
            return Replacement.keep();
        });
        editor.onReturn((ctx, ret) -> {
            returnCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, methodCallCount[0]);
        assertEquals(1, returnCount[0]);
    }

    @Test
    void chainedReplacements() {
        // Test multiple handlers on same node type
        MethodCallExpr call = factory.methodCall("method").on("com/example/Test").build();
        ExprStmt stmt = factory.exprStmt(call);
        BlockStmt body = factory.block(stmt);

        ASTEditor editor = new ASTEditor(body);

        // First handler: keep
        editor.onMethodCall((ctx, c) -> Replacement.keep());

        // Second handler: replace (should win)
        editor.onMethodCall((ctx, c) -> {
            if (c.getMethodName().equals("method")) {
                return Replacement.with(
                    factory.methodCall("replaced").on("com/example/Test").build()
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        MethodCallExpr result = (MethodCallExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("replaced", result.getMethodName());
    }

    @Test
    void multipleInsertions() {
        VarDeclStmt varDecl = factory.varDecl("int", "x", factory.intLiteral(5));
        BlockStmt body = factory.block(varDecl);

        ASTEditor editor = new ASTEditor(body);

        // Insert before and after via single handler
        editor.onStmt(StmtMatcher.varDeclStmt(), (ctx, stmt) -> {
            // Only the last replacement wins, so we can't test multiple handlers
            // This is a known limitation: multiple handlers on same node type,
            // the last non-keep replacement is used
            return Replacement.insertBefore(factory.exprStmt(factory.intLiteral(1)));
        });

        editor.apply();

        // Should have: inserted before + original
        assertEquals(2, body.getStatements().size());
        assertTrue(body.getStatements().get(0) instanceof ExprStmt);
        assertTrue(body.getStatements().get(1) instanceof VarDeclStmt);
    }

    // ========== Handler Registration Tests ==========

    @Test
    void onFieldAccessHandler() {
        FieldAccessExpr fieldAccess = factory.fieldAccess(
            factory.variable("obj"),
            "field",
            "com/example/Test",
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(fieldAccess);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onFieldAccess((ctx, access) -> {
            count[0]++;
            assertEquals("field", access.getFieldName());
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onNewExprHandler() {
        NewExpr newExpr = factory.newExpr("java/lang/String");
        ExprStmt stmt = factory.exprStmt(newExpr);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onNewExpr((ctx, expr) -> {
            count[0]++;
            assertEquals("java/lang/String", expr.getClassName());
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onCastHandler() {
        CastExpr cast = factory.cast("java/lang/String", factory.variable("obj"));
        ExprStmt stmt = factory.exprStmt(cast);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onCast((ctx, c) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onInstanceOfHandler() {
        InstanceOfExpr instanceOf = factory.instanceOf(factory.variable("obj"), "java/lang/String");
        ExprStmt stmt = factory.exprStmt(instanceOf);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onInstanceOf((ctx, iof) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onBinaryExprHandler() {
        BinaryExpr binary = factory.add(factory.intLiteral(1), factory.intLiteral(2), PrimitiveSourceType.INT);
        ExprStmt stmt = factory.exprStmt(binary);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onBinaryExpr((ctx, bin) -> {
            count[0]++;
            assertEquals(BinaryOperator.ADD, bin.getOperator());
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onUnaryExprHandler() {
        UnaryExpr unary = factory.not(factory.boolLiteral(true));
        ExprStmt stmt = factory.exprStmt(unary);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onUnaryExpr((ctx, un) -> {
            count[0]++;
            assertEquals(UnaryOperator.NOT, un.getOperator());
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onArrayAccessHandler() {
        ArrayAccessExpr arrayAccess = factory.arrayAccess(
            factory.variable("arr"),
            factory.intLiteral(0),
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(arrayAccess);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onArrayAccess((ctx, access, type) -> {
            count[0]++;
            assertEquals(ArrayAccessHandler.ArrayAccessType.READ, type);
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onArrayReadHandler() {
        // Create: x = arr[0] (read)
        ArrayAccessExpr arrayRead = factory.arrayAccess(
            factory.variable("arr"),
            factory.intLiteral(0),
            PrimitiveSourceType.INT
        );
        BinaryExpr assignment = factory.assign(factory.variable("x"), arrayRead, PrimitiveSourceType.INT);
        ExprStmt stmt = factory.exprStmt(assignment);
        BlockStmt body = factory.block(stmt);

        int[] readCount = {0};
        int[] storeCount = {0};

        ASTEditor editor = new ASTEditor(body);
        editor.onArrayRead((ctx, access, type) -> {
            readCount[0]++;
            return Replacement.keep();
        });
        editor.onArrayStore((ctx, access, type) -> {
            storeCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, readCount[0]);
        assertEquals(0, storeCount[0]);
    }

    @Test
    void onArrayStoreHandler() {
        // Create: arr[0] = 5 (store)
        ArrayAccessExpr arrayStore = factory.arrayAccess(
            factory.variable("arr"),
            factory.intLiteral(0),
            PrimitiveSourceType.INT
        );
        BinaryExpr assignment = factory.assign(arrayStore, factory.intLiteral(5), PrimitiveSourceType.INT);
        ExprStmt stmt = factory.exprStmt(assignment);
        BlockStmt body = factory.block(stmt);

        int[] readCount = {0};
        int[] storeCount = {0};

        ASTEditor editor = new ASTEditor(body);
        editor.onArrayRead((ctx, access, type) -> {
            readCount[0]++;
            return Replacement.keep();
        });
        editor.onArrayStore((ctx, access, type) -> {
            storeCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(0, readCount[0]);
        assertEquals(1, storeCount[0]);
    }

    @Test
    void onThrowHandler() {
        ThrowStmt throwStmt = factory.throwStmt(factory.variable("ex"));
        BlockStmt body = factory.block(throwStmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onThrow((ctx, t) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onIfHandler() {
        IfStmt ifStmt = factory.ifStmt(factory.boolLiteral(true), factory.block());
        BlockStmt body = factory.block(ifStmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onIf((ctx, i) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onLoopHandler() {
        WhileStmt whileStmt = new WhileStmt(factory.boolLiteral(true), factory.block());
        BlockStmt body = factory.block(whileStmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onLoop((ctx, loop) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onAssignmentHandler() {
        BinaryExpr assignment = factory.assign(
            factory.variable("x"),
            factory.intLiteral(5),
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(assignment);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onAssignment((ctx, assign) -> {
            count[0]++;
            assertTrue(assign.isAssignment());
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    // ========== Matcher-based Handler Tests ==========

    @Test
    void onExprWithMatcher() {
        MethodCallExpr call1 = factory.methodCall("method1").on("com/example/Test").build();
        MethodCallExpr call2 = factory.methodCall("method2").on("com/example/Test").build();
        BlockStmt body = factory.block(factory.exprStmt(call1), factory.exprStmt(call2));

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onExpr(ExprMatcher.methodCall("method1"), (ctx, expr) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    @Test
    void onStmtWithMatcher() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnVoid();
        BlockStmt body = factory.block(ret1, ret2);

        int[] count = {0};
        ASTEditor editor = new ASTEditor(body);
        editor.onStmt(StmtMatcher.returnWithValue(), (ctx, stmt) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    // ========== Find Operations Tests ==========

    @Test
    void findExpressions() {
        MethodCallExpr call1 = factory.methodCall("test1").on("com/example/Test").build();
        MethodCallExpr call2 = factory.methodCall("test2").on("com/example/Test").build();
        BlockStmt body = factory.block(factory.exprStmt(call1), factory.exprStmt(call2));

        ASTEditor editor = new ASTEditor(body);
        List<Expression> methodCalls = editor.findExpressions(ExprMatcher.anyMethodCall());

        assertEquals(2, methodCalls.size());
    }

    @Test
    void findStatements() {
        ReturnStmt ret1 = factory.returnStmt(factory.intLiteral(1));
        ReturnStmt ret2 = factory.returnStmt(factory.intLiteral(2));
        BlockStmt body = factory.block(ret1, ret2);

        ASTEditor editor = new ASTEditor(body);
        List<Statement> returns = editor.findStatements(StmtMatcher.returnStmt());

        assertEquals(2, returns.size());
    }

    @Test
    void findExpressionsInNestedStructure() {
        // Create: if (test()) { call1(); } else { call2(); }
        MethodCallExpr conditionCall = factory.methodCall("test").on("com/example/Test").build();
        MethodCallExpr call1 = factory.methodCall("call1").on("com/example/Test").build();
        MethodCallExpr call2 = factory.methodCall("call2").on("com/example/Test").build();

        IfStmt ifStmt = factory.ifElseStmt(
            conditionCall,
            factory.block(factory.exprStmt(call1)),
            factory.block(factory.exprStmt(call2))
        );
        BlockStmt body = factory.block(ifStmt);

        ASTEditor editor = new ASTEditor(body);
        List<Expression> methodCalls = editor.findExpressions(ExprMatcher.anyMethodCall());

        assertEquals(3, methodCalls.size());
    }

    // ========== Apply and Return Tests ==========

    @Test
    void applyAndReturn() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> Replacement.insertBefore(factory.exprStmt(factory.intLiteral(1))));

        BlockStmt result = editor.applyAndReturn();

        assertSame(body, result);
        assertEquals(2, result.getStatements().size());
    }

    // ========== Fluent API Tests ==========

    @Test
    void fluentHandlerChaining() {
        MethodCallExpr call = factory.methodCall("method").on("com/example/Test").build();
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(factory.exprStmt(call), returnStmt);

        int[] methodCount = {0};
        int[] returnCount = {0};

        ASTEditor editor = new ASTEditor(body)
            .onMethodCall((ctx, c) -> {
                methodCount[0]++;
                return Replacement.keep();
            })
            .onReturn((ctx, ret) -> {
                returnCount[0]++;
                return Replacement.keep();
            });

        editor.apply();

        assertEquals(1, methodCount[0]);
        assertEquals(1, returnCount[0]);
    }

    // ========== Edge Cases ==========

    @Test
    void emptyBlockStmt() {
        BlockStmt body = factory.block();
        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> Replacement.keep());
        editor.apply();

        assertEquals(0, body.getStatements().size());
    }

    @Test
    void noMatchingHandlers() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onMethodCall((ctx, call) -> Replacement.keep()); // Won't match
        editor.apply();

        assertEquals(1, body.getStatements().size());
    }

    @Test
    void keepReplacement() {
        ReturnStmt returnStmt = factory.returnStmt(factory.intLiteral(5));
        BlockStmt body = factory.block(returnStmt);

        ASTEditor editor = new ASTEditor(body);
        editor.onReturn((ctx, ret) -> Replacement.keep());
        editor.apply();

        assertEquals(1, body.getStatements().size());
        assertSame(returnStmt, body.getStatements().get(0));
    }
}
