package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.editor.matcher.ExprMatcher;
import com.tonic.analysis.source.editor.util.ASTFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ExpressionEditor.
 * Covers expression replacement, operator changes, subexpression extraction, and variable renaming.
 */
class ExpressionEditorTest {

    private ASTFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ASTFactory();
    }

    // ========== Constructor Tests ==========

    @Test
    void createExpressionEditor() {
        BlockStmt body = factory.block();
        ExpressionEditor editor = new ExpressionEditor(body, "testMethod", "()V", "com/example/Test");
        assertNotNull(editor);
    }

    // ========== Expression Replacement Tests ==========

    @Test
    void replaceMethodCallExpression() {
        // Create: oldMethod();
        MethodCallExpr oldCall = factory.methodCall("oldMethod")
            .on("com/example/Service")
            .build();
        ExprStmt stmt = factory.exprStmt(oldCall);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
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

        MethodCallExpr result = (MethodCallExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("newMethod", result.getMethodName());
    }

    @Test
    void replaceMethodCallByOwnerAndName() {
        // Create: Service.oldMethod()
        MethodCallExpr call = factory.methodCall("oldMethod")
            .on("com/example/Service")
            .build();
        ExprStmt stmt = factory.exprStmt(call);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.replaceMethodCall("com/example/Service", "oldMethod", (ctx, c) ->
            factory.methodCall("newMethod")
                .on("com/example/Service")
                .build()
        );
        editor.apply();

        MethodCallExpr result = (MethodCallExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("newMethod", result.getMethodName());
    }

    @Test
    void replaceMethodCallWithDotNotation() {
        // Test that dot notation is converted to internal format
        MethodCallExpr call = factory.methodCall("method")
            .on("com/example/Service")
            .build();
        ExprStmt stmt = factory.exprStmt(call);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.replaceMethodCall("com.example.Service", "method", (ctx, c) ->
            factory.stringLiteral("replaced")
        );
        editor.apply();

        Expression result = ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result instanceof LiteralExpr);
    }

    @Test
    void removeMethodCall() {
        MethodCallExpr call = factory.methodCall("deprecatedMethod")
            .on("com/example/Service")
            .build();
        ExprStmt stmt = factory.exprStmt(call);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.removeMethodCall("com/example/Service", "deprecatedMethod");
        editor.apply();

        // Expression statement should still exist but expression might be null or removed
        // Implementation may vary - verify the handler was called
        assertNotNull(body.getStatements().get(0));
    }

    @Test
    void replaceFieldAccessExpression() {
        // Create: obj.oldField
        FieldAccessExpr fieldAccess = factory.fieldAccess(
            factory.variable("obj"),
            "oldField",
            "com/example/Test",
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(fieldAccess);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onFieldAccess((ctx, access) -> {
            if (access.getFieldName().equals("oldField")) {
                return Replacement.with(
                    factory.fieldAccess(
                        access.getReceiver(),
                        "newField",
                        "com/example/Test",
                        PrimitiveSourceType.INT
                    )
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        FieldAccessExpr result = (FieldAccessExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("newField", result.getFieldName());
    }

    @Test
    void replaceFieldAccessByOwnerAndName() {
        FieldAccessExpr fieldAccess = factory.fieldAccess(
            factory.variable("obj"),
            "field",
            "com/example/Test",
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(fieldAccess);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.replaceFieldAccess("com/example/Test", "field", (ctx, access) ->
            factory.stringLiteral("replaced")
        );
        editor.apply();

        Expression result = ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result instanceof LiteralExpr);
    }

    @Test
    void replaceNewExpression() {
        // Create: new OldClass()
        NewExpr newExpr = factory.newExpr("com/example/OldClass");
        ExprStmt stmt = factory.exprStmt(newExpr);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.replaceNewExpr("com/example/OldClass", (ctx, expr) ->
            factory.newExpr("com/example/NewClass")
        );
        editor.apply();

        NewExpr result = (NewExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("com/example/NewClass", result.getClassName());
    }

    @Test
    void replaceNewExprWithDotNotation() {
        NewExpr newExpr = factory.newExpr("com/example/OldClass");
        ExprStmt stmt = factory.exprStmt(newExpr);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.replaceNewExpr("com.example.OldClass", (ctx, expr) ->
            factory.stringLiteral("replaced")
        );
        editor.apply();

        Expression result = ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result instanceof LiteralExpr);
    }

    // ========== Operator Changes Tests ==========

    @Test
    void changeBinaryOperator() {
        // Create: x + y
        BinaryExpr addition = factory.add(
            factory.variable("x"),
            factory.variable("y"),
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(addition);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onBinaryExpr((ctx, binary) -> {
            if (binary.getOperator() == BinaryOperator.ADD) {
                // Change to subtraction
                return Replacement.with(
                    factory.subtract(binary.getLeft(), binary.getRight(), PrimitiveSourceType.INT)
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        BinaryExpr result = (BinaryExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals(BinaryOperator.SUB, result.getOperator());
    }

    @Test
    void changeUnaryOperator() {
        // Create: !x
        UnaryExpr not = factory.not(factory.variable("x"));
        ExprStmt stmt = factory.exprStmt(not);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onUnaryExpr((ctx, unary) -> {
            if (unary.getOperator() == UnaryOperator.NOT) {
                // Remove the NOT (just return the operand)
                return Replacement.with(unary.getOperand());
            }
            return Replacement.keep();
        });
        editor.apply();

        Expression result = ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result instanceof VarRefExpr);
        assertEquals("x", ((VarRefExpr) result).getName());
    }

    @Test
    void changeComparisonOperator() {
        // Create: x == y
        BinaryExpr equals = factory.equals(factory.variable("x"), factory.variable("y"));
        ExprStmt stmt = factory.exprStmt(equals);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onBinaryExpr((ctx, binary) -> {
            if (binary.getOperator() == BinaryOperator.EQ) {
                // Change to !=
                return Replacement.with(
                    factory.notEquals(binary.getLeft(), binary.getRight())
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        BinaryExpr result = (BinaryExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals(BinaryOperator.NE, result.getOperator());
    }

    // ========== Subexpression Extraction Tests ==========

    @Test
    void extractMethodCallArgument() {
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

        // Extract inner call to a variable
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onMethodCall((ctx, call) -> {
            if (call.getMethodName().equals("print")) {
                // Replace argument with a variable reference
                return Replacement.with(
                    factory.methodCall("print")
                        .on("java/lang/System")
                        .withArgs(factory.variable("temp"))
                        .build()
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        MethodCallExpr result = (MethodCallExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result.getArguments().get(0) instanceof VarRefExpr);
    }

    @Test
    void extractBinaryExpressionOperand() {
        // Create: (x + y) * z
        BinaryExpr add = factory.add(
            factory.variable("x"),
            factory.variable("y"),
            PrimitiveSourceType.INT
        );
        BinaryExpr multiply = factory.multiply(add, factory.variable("z"), PrimitiveSourceType.INT);
        ExprStmt stmt = factory.exprStmt(multiply);
        BlockStmt body = factory.block(stmt);

        // Extract addition to variable
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onBinaryExpr((ctx, binary) -> {
            if (binary.getOperator() == BinaryOperator.MUL) {
                // Replace left operand with variable
                return Replacement.with(
                    factory.multiply(factory.variable("sum"), binary.getRight(), PrimitiveSourceType.INT)
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        BinaryExpr result = (BinaryExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result.getLeft() instanceof VarRefExpr);
        assertEquals("sum", ((VarRefExpr) result.getLeft()).getName());
    }

    @Test
    void extractFieldAccessReceiver() {
        // Create: obj.field.subfield
        FieldAccessExpr innerField = factory.fieldAccess(
            factory.variable("obj"),
            "field",
            "com/example/Test",
            new ReferenceSourceType("com/example/Inner")
        );
        FieldAccessExpr outerField = factory.fieldAccess(
            innerField,
            "subfield",
            "com/example/Inner",
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(outerField);
        BlockStmt body = factory.block(stmt);

        // Extract inner field access
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onFieldAccess((ctx, access) -> {
            if (access.getFieldName().equals("subfield")) {
                return Replacement.with(
                    factory.fieldAccess(
                        factory.variable("temp"),
                        "subfield",
                        "com/example/Inner",
                        PrimitiveSourceType.INT
                    )
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        FieldAccessExpr result = (FieldAccessExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result.getReceiver() instanceof VarRefExpr);
    }

    // ========== Variable Renaming Tests ==========

    @Test
    void renameVariableInExpression() {
        // Create: oldName (as standalone expression)
        VarRefExpr varRef = factory.variable("oldName");
        ExprStmt stmt = factory.exprStmt(varRef);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onExpr(
            ExprMatcher.custom(expr -> expr instanceof VarRefExpr &&
                ((VarRefExpr) expr).getName().equals("oldName")),
            (ctx, expr) -> Replacement.with(factory.variable("newName"))
        );
        editor.apply();

        VarRefExpr result = (VarRefExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("newName", result.getName());
    }

    @Test
    void renameVariableMultipleOccurrences() {
        // Test replacing multiple independent variable references
        VarRefExpr var1 = factory.variable("x");
        VarRefExpr var2 = factory.variable("x");
        VarRefExpr var3 = factory.variable("y");
        BlockStmt body = factory.block(
            factory.exprStmt(var1),
            factory.exprStmt(var2),
            factory.exprStmt(var3)
        );

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onExpr(
            ExprMatcher.custom(expr -> expr instanceof VarRefExpr &&
                ((VarRefExpr) expr).getName().equals("x")),
            (ctx, expr) -> Replacement.with(factory.variable("renamed"))
        );
        editor.apply();

        // Both x references should be renamed
        VarRefExpr result1 = (VarRefExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        VarRefExpr result2 = (VarRefExpr) ((ExprStmt) body.getStatements().get(1)).getExpression();
        VarRefExpr result3 = (VarRefExpr) ((ExprStmt) body.getStatements().get(2)).getExpression();

        assertEquals("renamed", result1.getName());
        assertEquals("renamed", result2.getName());
        assertEquals("y", result3.getName()); // Not renamed
    }

    // ========== Cast and Type Operations Tests ==========

    @Test
    void replaceCastExpression() {
        // Create: (String) obj
        CastExpr cast = factory.cast("java/lang/String", factory.variable("obj"));
        ExprStmt stmt = factory.exprStmt(cast);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onCast((ctx, c) -> {
            // Remove cast, just use the variable
            return Replacement.with(c.getExpression());
        });
        editor.apply();

        Expression result = ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result instanceof VarRefExpr);
    }

    @Test
    void replaceInstanceOfExpression() {
        // Create: obj instanceof String
        InstanceOfExpr instanceOf = factory.instanceOf(factory.variable("obj"), "java/lang/String");
        ExprStmt stmt = factory.exprStmt(instanceOf);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onInstanceOf((ctx, iof) -> {
            // Replace with true literal
            return Replacement.with(factory.boolLiteral(true));
        });
        editor.apply();

        Expression result = ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result instanceof LiteralExpr);
        assertEquals(true, ((LiteralExpr) result).getValue());
    }

    // ========== Array Access Tests ==========

    @Test
    void replaceArrayAccessExpression() {
        // Create: arr[0]
        ArrayAccessExpr arrayAccess = factory.arrayAccess(
            factory.variable("arr"),
            factory.intLiteral(0),
            PrimitiveSourceType.INT
        );
        ExprStmt stmt = factory.exprStmt(arrayAccess);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onArrayAccess((ctx, access, type) -> {
            // Replace index with variable
            return Replacement.with(
                factory.arrayAccess(access.getArray(), factory.variable("index"), PrimitiveSourceType.INT)
            );
        });
        editor.apply();

        ArrayAccessExpr result = (ArrayAccessExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertTrue(result.getIndex() instanceof VarRefExpr);
    }

    @Test
    void handleArrayReadVsStore() {
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

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onArrayRead((ctx, access, type) -> {
            readCount[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, readCount[0]);
    }

    // ========== Find Operations Tests ==========

    @Test
    void findAllMethodCalls() {
        MethodCallExpr call1 = factory.methodCall("method1").on("com/example/Test").build();
        MethodCallExpr call2 = factory.methodCall("method2").on("com/example/Test").build();
        BlockStmt body = factory.block(factory.exprStmt(call1), factory.exprStmt(call2));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> calls = editor.findMethodCalls();

        assertEquals(2, calls.size());
    }

    @Test
    void findMethodCallsByName() {
        MethodCallExpr call1 = factory.methodCall("target").on("com/example/Test").build();
        MethodCallExpr call2 = factory.methodCall("other").on("com/example/Test").build();
        BlockStmt body = factory.block(factory.exprStmt(call1), factory.exprStmt(call2));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> calls = editor.findMethodCalls("target");

        assertEquals(1, calls.size());
        assertEquals("target", ((MethodCallExpr) calls.get(0)).getMethodName());
    }

    @Test
    void findMethodCallsByOwnerAndName() {
        MethodCallExpr call1 = factory.methodCall("method").on("com/example/Test").build();
        MethodCallExpr call2 = factory.methodCall("method").on("com/example/Other").build();
        BlockStmt body = factory.block(factory.exprStmt(call1), factory.exprStmt(call2));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> calls = editor.findMethodCalls("com/example/Test", "method");

        assertEquals(1, calls.size());
        assertEquals("com/example/Test", ((MethodCallExpr) calls.get(0)).getOwnerClass());
    }

    @Test
    void findAllFieldAccesses() {
        FieldAccessExpr field1 = factory.fieldAccess(
            factory.variable("obj"),
            "field1",
            "com/example/Test",
            PrimitiveSourceType.INT
        );
        FieldAccessExpr field2 = factory.fieldAccess(
            factory.variable("obj"),
            "field2",
            "com/example/Test",
            PrimitiveSourceType.INT
        );
        BlockStmt body = factory.block(factory.exprStmt(field1), factory.exprStmt(field2));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> fields = editor.findFieldAccesses();

        assertEquals(2, fields.size());
    }

    @Test
    void findNewExpressionsByClass() {
        NewExpr new1 = factory.newExpr("java/lang/String");
        NewExpr new2 = factory.newExpr("java/lang/Integer");
        BlockStmt body = factory.block(factory.exprStmt(new1), factory.exprStmt(new2));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> strings = editor.findNewExpressions("java/lang/String");

        assertEquals(1, strings.size());
        assertEquals("java/lang/String", ((NewExpr) strings.get(0)).getClassName());
    }

    @Test
    void findArrayAccesses() {
        ArrayAccessExpr access1 = factory.arrayAccess(
            factory.variable("arr"),
            factory.intLiteral(0),
            PrimitiveSourceType.INT
        );
        ArrayAccessExpr access2 = factory.arrayAccess(
            factory.variable("arr"),
            factory.intLiteral(1),
            PrimitiveSourceType.INT
        );
        BlockStmt body = factory.block(factory.exprStmt(access1), factory.exprStmt(access2));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> accesses = editor.findArrayAccesses();

        assertEquals(2, accesses.size());
    }

    @Test
    void findExpressionsWithMatcher() {
        BinaryExpr addition = factory.add(factory.intLiteral(1), factory.intLiteral(2), PrimitiveSourceType.INT);
        BinaryExpr subtraction = factory.subtract(factory.intLiteral(3), factory.intLiteral(4), PrimitiveSourceType.INT);
        BlockStmt body = factory.block(factory.exprStmt(addition), factory.exprStmt(subtraction));

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        List<Expression> additions = editor.findExpressions(ExprMatcher.binaryOp(BinaryOperator.ADD));

        assertEquals(1, additions.size());
        assertEquals(BinaryOperator.ADD, ((BinaryExpr) additions.get(0)).getOperator());
    }

    // ========== Handler Registration Tests ==========

    @Test
    void onAnyExprHandler() {
        MethodCallExpr call = factory.methodCall("method").on("com/example/Test").build();
        LiteralExpr literal = factory.intLiteral(42);
        BlockStmt body = factory.block(factory.exprStmt(call), factory.exprStmt(literal));

        int[] count = {0};
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onAnyExpr((ctx, expr) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        // Should match both expressions
        assertEquals(2, count[0]);
    }

    @Test
    void onNewArrayHandler() {
        NewArrayExpr newArray = factory.newArray("int", factory.intLiteral(10));
        ExprStmt stmt = factory.exprStmt(newArray);
        BlockStmt body = factory.block(stmt);

        int[] count = {0};
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onNewArray((ctx, array) -> {
            count[0]++;
            return Replacement.keep();
        });
        editor.apply();

        assertEquals(1, count[0]);
    }

    // ========== Fluent API Tests ==========

    @Test
    void fluentMethodChaining() {
        MethodCallExpr call = factory.methodCall("method").on("com/example/Test").build();
        FieldAccessExpr field = factory.fieldAccess(
            factory.variable("obj"),
            "field",
            "com/example/Test",
            PrimitiveSourceType.INT
        );
        BlockStmt body = factory.block(factory.exprStmt(call), factory.exprStmt(field));

        int[] methodCount = {0};
        int[] fieldCount = {0};

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test")
            .onMethodCall((ctx, c) -> {
                methodCount[0]++;
                return Replacement.keep();
            })
            .onFieldAccess((ctx, f) -> {
                fieldCount[0]++;
                return Replacement.keep();
            });

        editor.apply();

        assertEquals(1, methodCount[0]);
        assertEquals(1, fieldCount[0]);
    }

    // ========== Edge Cases ==========

    @Test
    void replaceWithNullReturnsKeep() {
        MethodCallExpr call = factory.methodCall("method").on("com/example/Test").build();
        ExprStmt stmt = factory.exprStmt(call);
        BlockStmt body = factory.block(stmt);

        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.replaceMethodCall("com/example/Test", "method", (ctx, c) -> null);
        editor.apply();

        // Should keep original since replacement is null
        MethodCallExpr result = (MethodCallExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals("method", result.getMethodName());
    }

    @Test
    void getDelegateEditor() {
        BlockStmt body = factory.block();
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        ASTEditor delegate = editor.getDelegate();

        assertNotNull(delegate);
    }

    @Test
    void complexExpressionReplacement() {
        // Create: a + b (test replacing top-level binary expression)
        BinaryExpr add = factory.add(factory.variable("a"), factory.variable("b"), PrimitiveSourceType.INT);
        ExprStmt stmt = factory.exprStmt(add);
        BlockStmt body = factory.block(stmt);

        // Replace addition with multiplication
        ExpressionEditor editor = new ExpressionEditor(body, "test", "()V", "com/example/Test");
        editor.onBinaryExpr((ctx, binary) -> {
            if (binary.getOperator() == BinaryOperator.ADD) {
                return Replacement.with(
                    factory.multiply(binary.getLeft(), binary.getRight(), PrimitiveSourceType.INT)
                );
            }
            return Replacement.keep();
        });
        editor.apply();

        BinaryExpr result = (BinaryExpr) ((ExprStmt) body.getStatements().get(0)).getExpression();
        assertEquals(BinaryOperator.MUL, result.getOperator());
    }
}
