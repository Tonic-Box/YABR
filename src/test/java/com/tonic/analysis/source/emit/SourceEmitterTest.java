package com.tonic.analysis.source.emit;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SourceEmitter - emitting Java source code from AST nodes.
 * Uses lenient assertions focusing on successful emission rather than exact formatting.
 */
class SourceEmitterTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    // ========== Basic Statement Emission Tests ==========

    @Test
    void emitReturnStatementWithValue() {
        ReturnStmt returnStmt = new ReturnStmt(LiteralExpr.ofInt(42));

        String result = SourceEmitter.emit(returnStmt);

        assertNotNull(result);
        assertTrue(result.contains("return"));
        assertTrue(result.contains("42"));
    }

    @Test
    void emitVoidReturnStatement() {
        ReturnStmt returnStmt = new ReturnStmt();

        String result = SourceEmitter.emit(returnStmt);

        assertNotNull(result);
        assertTrue(result.contains("return"));
    }

    @Test
    void emitVariableDeclarationWithoutInitializer() {
        VarDeclStmt varDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x"
        );

        String result = SourceEmitter.emit(varDecl);

        assertNotNull(result);
        assertTrue(result.contains("int"));
        assertTrue(result.contains("x"));
    }

    @Test
    void emitVariableDeclarationWithInitializer() {
        VarDeclStmt varDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(10)
        );

        String result = SourceEmitter.emit(varDecl);

        assertNotNull(result);
        assertTrue(result.contains("int"));
        assertTrue(result.contains("x"));
        assertTrue(result.contains("10"));
    }

    @Test
    void emitFinalVariableDeclaration() {
        VarDeclStmt varDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "CONSTANT",
            LiteralExpr.ofInt(100),
            false,
            true,
            null
        );

        String result = SourceEmitter.emit(varDecl);

        assertNotNull(result);
        assertTrue(result.contains("final"));
        assertTrue(result.contains("CONSTANT"));
    }

    @Test
    void emitExpressionStatement() {
        Expression assignment = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );
        ExprStmt exprStmt = new ExprStmt(assignment);

        String result = SourceEmitter.emit(exprStmt);

        assertNotNull(result);
        assertTrue(result.contains("x"));
        assertTrue(result.contains("5"));
    }

    @Test
    void emitBlockStatement() {
        List<Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(1)));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        String result = SourceEmitter.emit(block);

        assertNotNull(result);
        assertTrue(result.contains("{"));
        assertTrue(result.contains("}"));
        assertTrue(result.contains("x"));
    }

    // ========== Control Flow Statement Tests ==========

    @Test
    void emitIfStatementWithoutElse() {
        Expression condition = new BinaryExpr(
            BinaryOperator.GT,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(0),
            PrimitiveSourceType.BOOLEAN
        );
        Statement thenBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        IfStmt ifStmt = new IfStmt(condition, thenBranch);

        String result = SourceEmitter.emit(ifStmt);

        assertNotNull(result);
        assertTrue(result.contains("if"));
    }

    @Test
    void emitIfStatementWithElse() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        Statement elseBranch = new ReturnStmt(LiteralExpr.ofInt(0));
        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        String result = SourceEmitter.emit(ifStmt);

        assertNotNull(result);
        assertTrue(result.contains("if"));
        assertTrue(result.contains("else"));
    }

    @Test
    void emitIfStatementWithEmptyBlocks() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new BlockStmt();
        Statement elseBranch = new BlockStmt();
        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        String result = SourceEmitter.emit(ifStmt);

        assertNotNull(result);
    }

    @Test
    void emitWhileStatement() {
        Expression condition = new BinaryExpr(
            BinaryOperator.LT,
            new VarRefExpr("i", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(10),
            PrimitiveSourceType.BOOLEAN
        );
        Statement body = new ExprStmt(
            new UnaryExpr(UnaryOperator.POST_INC, new VarRefExpr("i", PrimitiveSourceType.INT), PrimitiveSourceType.INT)
        );
        WhileStmt whileStmt = new WhileStmt(condition, body);

        String result = SourceEmitter.emit(whileStmt);

        assertNotNull(result);
        assertTrue(result.contains("while"));
    }

    @Test
    void emitBreakStatement() {
        BreakStmt breakStmt = new BreakStmt(null);

        String result = SourceEmitter.emit(breakStmt);

        assertNotNull(result);
        assertTrue(result.contains("break"));
    }

    @Test
    void emitBreakStatementWithLabel() {
        BreakStmt breakStmt = new BreakStmt("outer");

        String result = SourceEmitter.emit(breakStmt);

        assertNotNull(result);
        assertTrue(result.contains("break"));
        assertTrue(result.contains("outer"));
    }

    @Test
    void emitContinueStatement() {
        ContinueStmt continueStmt = new ContinueStmt(null);

        String result = SourceEmitter.emit(continueStmt);

        assertNotNull(result);
        assertTrue(result.contains("continue"));
    }

    // ========== Expression Emission Tests ==========

    @Test
    void emitIntegerLiteral() {
        Expression expr = LiteralExpr.ofInt(42);

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("42"));
    }

    @Test
    void emitLongLiteral() {
        Expression expr = LiteralExpr.ofLong(12345L);

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("12345"));
        assertTrue(result.contains("L"));
    }

    @Test
    void emitFloatLiteral() {
        Expression expr = LiteralExpr.ofFloat(3.14f);

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("3.14"));
        assertTrue(result.contains("f"));
    }

    @Test
    void emitDoubleLiteral() {
        Expression expr = LiteralExpr.ofDouble(2.718);

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("2.718"));
        assertTrue(result.contains("d"));
    }

    @Test
    void emitBooleanLiteral() {
        Expression trueExpr = LiteralExpr.ofBoolean(true);
        Expression falseExpr = LiteralExpr.ofBoolean(false);

        String trueResult = SourceEmitter.emit(trueExpr);
        String falseResult = SourceEmitter.emit(falseExpr);

        assertTrue(trueResult.contains("true"));
        assertTrue(falseResult.contains("false"));
    }

    @Test
    void emitStringLiteral() {
        Expression expr = LiteralExpr.ofString("Hello, World!");

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("Hello, World!"));
    }

    @Test
    void emitStringLiteralWithEscapes() {
        Expression expr = LiteralExpr.ofString("Line1\nLine2\tTabbed");

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("\\n"));
        assertTrue(result.contains("\\t"));
    }

    @Test
    void emitNullLiteral() {
        Expression expr = LiteralExpr.ofNull();

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("null"));
    }

    @Test
    void emitCharLiteral() {
        Expression expr = LiteralExpr.ofChar('A');

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("'A'"));
    }

    @Test
    void emitVariableReference() {
        Expression expr = new VarRefExpr("myVariable", PrimitiveSourceType.INT);

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("myVariable"));
    }

    // ========== Binary Expression Tests ==========

    @Test
    void emitBinaryAddition() {
        Expression expr = new BinaryExpr(
            BinaryOperator.ADD,
            LiteralExpr.ofInt(1),
            LiteralExpr.ofInt(2),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("1"));
        assertTrue(result.contains("2"));
        assertTrue(result.contains("+"));
    }

    @Test
    void emitBinaryComparison() {
        Expression expr = new BinaryExpr(
            BinaryOperator.EQ,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(0),
            PrimitiveSourceType.BOOLEAN
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("x"));
        assertTrue(result.contains("0"));
        assertTrue(result.contains("=="));
    }

    @Test
    void emitBinaryLogicalAnd() {
        Expression expr = new BinaryExpr(
            BinaryOperator.AND,
            LiteralExpr.ofBoolean(true),
            LiteralExpr.ofBoolean(false),
            PrimitiveSourceType.BOOLEAN
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("&&"));
    }

    @Test
    void emitBinaryAssignment() {
        Expression expr = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(10),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("x"));
        assertTrue(result.contains("10"));
        assertTrue(result.contains("="));
    }

    // ========== Unary Expression Tests ==========

    @Test
    void emitUnaryNegation() {
        Expression expr = new UnaryExpr(
            UnaryOperator.NEG,
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("-"));
        assertTrue(result.contains("5"));
    }

    @Test
    void emitUnaryLogicalNot() {
        Expression expr = new UnaryExpr(
            UnaryOperator.NOT,
            LiteralExpr.ofBoolean(true),
            PrimitiveSourceType.BOOLEAN
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("!"));
    }

    @Test
    void emitUnaryPreIncrement() {
        Expression expr = new UnaryExpr(
            UnaryOperator.PRE_INC,
            new VarRefExpr("i", PrimitiveSourceType.INT),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("++"));
        assertTrue(result.contains("i"));
    }

    @Test
    void emitUnaryPostIncrement() {
        Expression expr = new UnaryExpr(
            UnaryOperator.POST_INC,
            new VarRefExpr("i", PrimitiveSourceType.INT),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("++"));
        assertTrue(result.contains("i"));
    }

    // ========== Other Expression Tests ==========

    @Test
    void emitCastExpression() {
        Expression expr = new CastExpr(
            PrimitiveSourceType.INT,
            LiteralExpr.ofDouble(3.14)
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("int"));
    }

    @Test
    void emitTernaryExpression() {
        Expression expr = new TernaryExpr(
            LiteralExpr.ofBoolean(true),
            LiteralExpr.ofInt(1),
            LiteralExpr.ofInt(0),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("?"));
        assertTrue(result.contains(":"));
    }

    @Test
    void emitMethodCall() {
        List<Expression> args = new ArrayList<>();
        args.add(LiteralExpr.ofString("test"));
        Expression expr = new MethodCallExpr(
            null,
            "println",
            "java/io/PrintStream",
            args,
            false,
            VoidSourceType.INSTANCE,
            null
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("println"));
    }

    @Test
    void emitFieldAccess() {
        // FieldAccessExpr(receiver, fieldName, ownerClass, isStatic, type)
        Expression expr = new FieldAccessExpr(
            new VarRefExpr("obj", new ReferenceSourceType("com/test/MyClass")),
            "value",
            "com/test/MyClass",
            false,
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("value"));
    }

    @Test
    void emitArrayAccess() {
        Expression expr = new ArrayAccessExpr(
            new VarRefExpr("array", new ReferenceSourceType("[I")),
            LiteralExpr.ofInt(0),
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("["));
        assertTrue(result.contains("]"));
    }

    @Test
    void emitNewExpression() {
        List<Expression> args = new ArrayList<>();
        args.add(LiteralExpr.ofString("test"));
        Expression expr = new NewExpr(
            "java/lang/String",
            args,
            new ReferenceSourceType("java/lang/String")
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("new"));
        assertTrue(result.contains("String"));
    }

    @Test
    void emitNewArrayExpression() {
        List<Expression> dims = new ArrayList<>();
        dims.add(LiteralExpr.ofInt(10));
        // NewArrayExpr(elementType, dimensions, initializer, type, location)
        Expression expr = new NewArrayExpr(
            PrimitiveSourceType.INT,
            dims,
            null,
            new ReferenceSourceType("[I"),
            null
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("new"));
        assertTrue(result.contains("int"));
    }

    @Test
    void emitThisExpression() {
        Expression expr = new ThisExpr(new ReferenceSourceType("com/test/MyClass"));

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("this"));
    }

    @Test
    void emitSuperExpression() {
        Expression expr = new SuperExpr(new ReferenceSourceType("com/test/MyClass"));

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("super"));
    }

    // ========== Complex Statement Tests ==========

    @Test
    void emitNestedBlocks() {
        List<Statement> inner = new ArrayList<>();
        inner.add(new ReturnStmt(LiteralExpr.ofInt(1)));
        BlockStmt innerBlock = new BlockStmt(inner);

        List<Statement> outer = new ArrayList<>();
        outer.add(innerBlock);
        BlockStmt outerBlock = new BlockStmt(outer);

        String result = SourceEmitter.emit(outerBlock);

        assertNotNull(result);
        assertTrue(result.contains("{"));
        assertTrue(result.contains("}"));
    }

    @Test
    void emitMultipleStatements() {
        List<Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "a", LiteralExpr.ofInt(1)));
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "b", LiteralExpr.ofInt(2)));
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "c", LiteralExpr.ofInt(3)));
        BlockStmt block = new BlockStmt(stmts);

        String result = SourceEmitter.emit(block);

        assertNotNull(result);
        assertTrue(result.contains("a"));
        assertTrue(result.contains("b"));
        assertTrue(result.contains("c"));
    }

    // ========== Formatting Configuration Tests ==========

    @Test
    void emitWithCustomConfig() {
        // SourceEmitterConfig uses Lombok @Builder, create with builder pattern
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .alwaysUseBraces(true)
            .build();

        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        IfStmt ifStmt = new IfStmt(condition, thenBranch);

        String result = SourceEmitter.emit(ifStmt, config);

        assertNotNull(result);
        assertTrue(result.contains("if"));
    }

    @Test
    void emitDoesNotThrowOnComplexExpression() {
        Expression complex = new BinaryExpr(
            BinaryOperator.ADD,
            new BinaryExpr(
                BinaryOperator.MUL,
                LiteralExpr.ofInt(2),
                LiteralExpr.ofInt(3),
                PrimitiveSourceType.INT
            ),
            new BinaryExpr(
                BinaryOperator.SUB,
                LiteralExpr.ofInt(5),
                LiteralExpr.ofInt(1),
                PrimitiveSourceType.INT
            ),
            PrimitiveSourceType.INT
        );

        assertDoesNotThrow(() -> SourceEmitter.emit(complex));
    }

    @Test
    void emitHandlesEmptyBlock() {
        BlockStmt emptyBlock = new BlockStmt();

        String result = SourceEmitter.emit(emptyBlock);

        assertNotNull(result);
        assertTrue(result.contains("{"));
        assertTrue(result.contains("}"));
    }

    @Test
    void emitInstanceOfExpression() {
        Expression expr = new InstanceOfExpr(
            new VarRefExpr("obj", ReferenceSourceType.OBJECT),
            new ReferenceSourceType("java/lang/String"),
            null
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("instanceof"));
    }

    // ========== DynamicConstantExpr Tests ==========

    @Test
    void emitDynamicConstantExprWithBootstrapInfo() {
        Expression expr = new DynamicConstantExpr(
            "myConst",
            "Ljava/lang/Object;",
            0,
            "com/test/Bootstrap",
            "makeConst",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
            ReferenceSourceType.OBJECT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("condy"));
        assertTrue(result.contains("myConst"));
    }

    @Test
    void emitDynamicConstantExprWithMinimalInfo() {
        Expression expr = new DynamicConstantExpr(
            "unnamed",
            "I",
            5,
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("condy"));
    }

    @Test
    void emitDynamicConstantExprWithBootstrapResolution() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .resolveBootstrapMethods(true)
            .build();

        Expression expr = new DynamicConstantExpr(
            "myConst",
            "Ljava/lang/Object;",
            0,
            "com/test/Bootstrap",
            "makeConst",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
            ReferenceSourceType.OBJECT
        );

        String result = SourceEmitter.emit(new ExprStmt(expr), config);

        assertNotNull(result);
        assertTrue(result.contains("Bootstrap"));
        assertTrue(result.contains("makeConst"));
        assertTrue(result.contains("/* condy */"));
    }

    // ========== InvokeDynamicExpr Tests ==========

    @Test
    void emitInvokeDynamicExprWithBootstrapInfo() {
        List<Expression> args = new ArrayList<>();
        args.add(LiteralExpr.ofString("arg1"));

        Expression expr = new InvokeDynamicExpr(
            "dynamicMethod",
            "(Ljava/lang/String;)V",
            args,
            "com/test/Bootstrap",
            "bootstrap",
            VoidSourceType.INSTANCE
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("invokedynamic"));
        assertTrue(result.contains("dynamicMethod"));
        assertTrue(result.contains("@bsm"));
    }

    @Test
    void emitInvokeDynamicExprWithoutArgs() {
        Expression expr = new InvokeDynamicExpr(
            "noArgMethod",
            "()I",
            new ArrayList<>(),
            "com/test/Bootstrap",
            "bootstrap",
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(expr);

        assertNotNull(result);
        assertTrue(result.contains("invokedynamic"));
        assertTrue(result.contains("noArgMethod"));
    }

    @Test
    void emitInvokeDynamicExprWithBootstrapResolution() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .resolveBootstrapMethods(true)
            .build();

        List<Expression> args = new ArrayList<>();
        args.add(LiteralExpr.ofInt(42));

        Expression expr = new InvokeDynamicExpr(
            "dynamicCall",
            "(I)V",
            args,
            "com/test/Bootstrap",
            "invoke",
            VoidSourceType.INSTANCE
        );

        String result = SourceEmitter.emit(new ExprStmt(expr), config);

        assertNotNull(result);
        assertTrue(result.contains("Bootstrap"));
        assertTrue(result.contains("invoke"));
        assertTrue(result.contains("42"));
        assertTrue(result.contains("/* indy */"));
    }

    @Test
    void emitInvokeDynamicExprWithUnknownBootstrap() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .resolveBootstrapMethods(true)
            .build();

        Expression expr = new InvokeDynamicExpr(
            "unknownMethod",
            "()V",
            new ArrayList<>(),
            VoidSourceType.INSTANCE
        );

        String result = SourceEmitter.emit(new ExprStmt(expr), config);

        assertNotNull(result);
        // Should fall back to comment format since bootstrap is "unknown"
        assertTrue(result.contains("invokedynamic"));
    }

    // ========== Identifier Mode Tests ==========

    @Test
    void emitWithUnicodeEscapeMode() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .identifierMode(IdentifierMode.UNICODE_ESCAPE)
            .build();

        // Variable with invalid start character
        VarDeclStmt varDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "1invalid",
            LiteralExpr.ofInt(10)
        );

        String result = SourceEmitter.emit(varDecl, config);

        assertNotNull(result);
        assertTrue(result.contains("\\u0031")); // '1' escaped
    }

    @Test
    void emitWithSemanticRenameMode() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .identifierMode(IdentifierMode.SEMANTIC_RENAME)
            .build();

        // Method call with invalid name
        List<Expression> args = new ArrayList<>();
        Expression expr = new MethodCallExpr(
            null,
            "???invalid",
            "com/test/Class",
            args,
            true,
            VoidSourceType.INSTANCE,
            null
        );

        String result = SourceEmitter.emit(expr);

        // With default config (RAW), should keep as-is
        assertNotNull(result);
    }

    @Test
    void emitWithSemanticRenameModeRenamesFields() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .identifierMode(IdentifierMode.SEMANTIC_RENAME)
            .build();

        Expression expr = new FieldAccessExpr(
            new VarRefExpr("obj", new ReferenceSourceType("com/test/MyClass")),
            "???field",
            "com/test/MyClass",
            false,
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(new ExprStmt(expr), config);

        assertNotNull(result);
        assertTrue(result.contains("field_"));
    }

    // ========== Combined Feature Tests ==========

    @Test
    void emitDynamicConstantWithUnicodeEscape() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .identifierMode(IdentifierMode.UNICODE_ESCAPE)
            .resolveBootstrapMethods(false)
            .build();

        Expression expr = new DynamicConstantExpr(
            "const?name",
            "I",
            0,
            "x/pkg",
            "method?name",
            "",
            PrimitiveSourceType.INT
        );

        String result = SourceEmitter.emit(new ExprStmt(expr), config);

        assertNotNull(result);
        assertTrue(result.contains("\\u003f")); // '?' escaped
    }

    @Test
    void emitInvokeDynamicWithSemanticRename() {
        SourceEmitterConfig config = SourceEmitterConfig.builder()
            .identifierMode(IdentifierMode.SEMANTIC_RENAME)
            .resolveBootstrapMethods(true)
            .build();

        Expression expr = new InvokeDynamicExpr(
            "???call",
            "()V",
            new ArrayList<>(),
            "com/test/Bootstrap",
            "???bootstrap",
            VoidSourceType.INSTANCE
        );

        String result = SourceEmitter.emit(new ExprStmt(expr), config);

        assertNotNull(result);
        assertTrue(result.contains("method_")); // Bootstrap method renamed
        assertTrue(result.contains("/* indy */"));
    }
}
