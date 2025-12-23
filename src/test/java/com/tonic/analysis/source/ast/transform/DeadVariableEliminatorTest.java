package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.ArraySourceType;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeadVariableEliminatorTest {

    private DeadVariableEliminator eliminator;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        eliminator = new DeadVariableEliminator();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("DeadVariableEliminator", eliminator.getName());
    }

    @Test
    void transformEmptyBlockReturnsFalse() {
        BlockStmt block = new BlockStmt();

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
    }

    @Test
    void basicUnusedVariableRemoval() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "unused",
            LiteralExpr.ofInt(42)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(1, block.size());
        assertTrue(block.getStatements().get(0) instanceof ReturnStmt);
    }

    @Test
    void preserveUsedVariable() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(5)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(2, block.size());
    }

    @Test
    void cascadingDeadVariables() {
        VarDeclStmt declA = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "a",
            LiteralExpr.ofInt(5)
        );

        VarDeclStmt declB = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "b",
            new VarRefExpr("a", PrimitiveSourceType.INT)
        );

        VarDeclStmt declC = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "c",
            new VarRefExpr("b", PrimitiveSourceType.INT)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(declA);
        stmts.add(declB);
        stmts.add(declC);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(1, block.size());
        assertTrue(block.getStatements().get(0) instanceof ReturnStmt);
    }

    @Test
    void compoundAssignmentIsRead() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(5)
        );

        BinaryExpr compoundAssign = new BinaryExpr(
            BinaryOperator.ADD_ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(compoundAssign));
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(3, block.size());
    }

    @Test
    void writeOnlyVariableRemoved() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(10),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(1, block.size());
    }

    @Test
    void preserveSideEffectsInInitializer() {
        MethodCallExpr sideEffect = new MethodCallExpr(
            null,
            "compute",
            "TestClass",
            new ArrayList<>(),
            true,
            PrimitiveSourceType.INT
        );

        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "unused",
            sideEffect
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(2, block.size());
        assertTrue(block.getStatements().get(0) instanceof ExprStmt);
        ExprStmt exprStmt = (ExprStmt) block.getStatements().get(0);
        assertTrue(exprStmt.getExpression() instanceof MethodCallExpr);
    }

    @Test
    void preserveSideEffectsInAssignment() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        MethodCallExpr sideEffect = new MethodCallExpr(
            null,
            "getValue",
            "TestClass",
            new ArrayList<>(),
            true,
            PrimitiveSourceType.INT
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            sideEffect,
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(2, block.size());
        assertTrue(block.getStatements().get(0) instanceof ExprStmt);
    }

    @Test
    void handleNestedBlocks() {
        VarDeclStmt innerDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "y",
            LiteralExpr.ofInt(10)
        );

        List<Statement> innerStmts = new ArrayList<>();
        innerStmts.add(innerDecl);
        innerStmts.add(new ReturnStmt());
        BlockStmt innerBlock = new BlockStmt(innerStmts);

        List<Statement> outerStmts = new ArrayList<>();
        outerStmts.add(innerBlock);
        BlockStmt outerBlock = new BlockStmt(outerStmts);

        boolean changed = eliminator.transform(outerBlock);

        assertTrue(changed);
    }

    @Test
    void removeUnusedInIfBranch() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "unused",
            LiteralExpr.ofInt(5)
        );

        List<Statement> thenStmts = new ArrayList<>();
        thenStmts.add(decl);
        thenStmts.add(new ReturnStmt());
        BlockStmt thenBlock = new BlockStmt(thenStmts);

        IfStmt ifStmt = new IfStmt(
            LiteralExpr.ofBoolean(true),
            thenBlock
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
    }

    @Test
    void preserveVariableUsedInCondition() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(5)
        );

        BinaryExpr condition = new BinaryExpr(
            BinaryOperator.GT,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(0),
            PrimitiveSourceType.BOOLEAN
        );

        IfStmt ifStmt = new IfStmt(
            condition,
            new ReturnStmt(LiteralExpr.ofInt(1))
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(ifStmt);
        stmts.add(new ReturnStmt(LiteralExpr.ofInt(0)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(3, block.size());
    }

    @Test
    void multipleUnusedVariables() {
        VarDeclStmt declA = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "a",
            LiteralExpr.ofInt(1)
        );

        VarDeclStmt declB = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "b",
            LiteralExpr.ofInt(2)
        );

        VarDeclStmt declC = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "c",
            LiteralExpr.ofInt(3)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(declA);
        stmts.add(declB);
        stmts.add(declC);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(1, block.size());
    }

    @Test
    void partiallyUsedVariables() {
        VarDeclStmt declX = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(10)
        );

        VarDeclStmt declY = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "y",
            LiteralExpr.ofInt(20)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(declX);
        stmts.add(declY);
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(2, block.size());
        assertTrue(block.getStatements().get(0) instanceof VarDeclStmt);
        assertEquals("x", ((VarDeclStmt) block.getStatements().get(0)).getName());
    }

    @Test
    void idempotentTransformation() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "unused",
            LiteralExpr.ofInt(42)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean firstPass = eliminator.transform(block);
        boolean secondPass = eliminator.transform(block);

        assertTrue(firstPass);
        assertFalse(secondPass);
    }

    @Test
    void noChangeWhenAllVariablesUsed() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(5)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(2, block.size());
    }

    @Test
    void handleIncrementDecrement() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        UnaryExpr increment = new UnaryExpr(
            UnaryOperator.POST_INC,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(increment));
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(3, block.size());
    }

    @Test
    void variableUsedInArrayAccess() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "index",
            LiteralExpr.ofInt(0)
        );

        ArrayAccessExpr arrayAccess = new ArrayAccessExpr(
            new VarRefExpr("arr", new ArraySourceType(PrimitiveSourceType.INT)),
            new VarRefExpr("index", PrimitiveSourceType.INT),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ReturnStmt(arrayAccess));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(2, block.size());
    }

    @Test
    void removeDeadVariableChain() {
        VarDeclStmt declA = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "a",
            LiteralExpr.ofInt(1)
        );

        VarDeclStmt declB = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "b",
            new BinaryExpr(
                BinaryOperator.ADD,
                new VarRefExpr("a", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(1),
                PrimitiveSourceType.INT
            )
        );

        VarDeclStmt declC = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "c",
            new BinaryExpr(
                BinaryOperator.MUL,
                new VarRefExpr("b", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(2),
                PrimitiveSourceType.INT
            )
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(declA);
        stmts.add(declB);
        stmts.add(declC);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(1, block.size());
    }
}
