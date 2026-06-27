package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeadStoreEliminatorTest {

    private DeadStoreEliminator eliminator;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        eliminator = new DeadStoreEliminator();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("DeadStoreEliminator", eliminator.getName());
    }

    @Test
    void transformEmptyBlockReturnsFalse() {
        BlockStmt block = new BlockStmt();

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
    }

    @Test
    void basicDeadStoreElimination() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertEquals(2, block.size());
        assertTrue(block.getStatements().get(0) instanceof VarDeclStmt);
        VarDeclStmt resultDecl = (VarDeclStmt) block.getStatements().get(0);
        assertEquals("x", resultDecl.getName());
        assertTrue(resultDecl.getInitializer() instanceof LiteralExpr);
        assertEquals(5, ((LiteralExpr) resultDecl.getInitializer()).getValue());
    }

    @Test
    void multipleReassignments() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(1)
        );

        BinaryExpr assign2 = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(2),
            PrimitiveSourceType.INT
        );

        BinaryExpr assign3 = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(3),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(assign2));
        stmts.add(new ExprStmt(assign3));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
        assertTrue(block.size() <= 3);
    }

    @Test
    void initializerWithSideEffects() {
        MethodCallExpr sideEffect = new MethodCallExpr(
            null,
            "getY",
            "TestClass",
            new ArrayList<>(),
            true,
            PrimitiveSourceType.INT
        );

        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            sideEffect
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(3, block.size());
    }

    @Test
    void preserveReadBetweenDeclAndAssign() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(10)
        );

        BinaryExpr useX = new BinaryExpr(
            BinaryOperator.ADD,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(1),
            PrimitiveSourceType.INT
        );
        VarDeclStmt yDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "y",
            useX
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(20),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(yDecl);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(4, block.size());
    }

    @Test
    void assignmentReadsVariable() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(5)
        );

        BinaryExpr selfIncrement = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            new BinaryExpr(
                BinaryOperator.ADD,
                new VarRefExpr("x", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(1),
                PrimitiveSourceType.INT
            ),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(selfIncrement));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(3, block.size());
    }

    @Test
    void stopAtControlFlow() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        IfStmt ifStmt = new IfStmt(
            LiteralExpr.ofBoolean(true),
            new ReturnStmt()
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(ifStmt);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertFalse(changed);
        assertEquals(4, block.size());
    }

    @Test
    void handleNestedBlocks() {
        VarDeclStmt innerDecl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "y",
            LiteralExpr.ofInt(0)
        );

        BinaryExpr innerAssign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("y", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(10),
            PrimitiveSourceType.INT
        );

        List<Statement> innerStmts = new ArrayList<>();
        innerStmts.add(innerDecl);
        innerStmts.add(new ExprStmt(innerAssign));
        innerStmts.add(new ReturnStmt(new VarRefExpr("y", PrimitiveSourceType.INT)));
        BlockStmt innerBlock = new BlockStmt(innerStmts);

        List<Statement> outerStmts = new ArrayList<>();
        outerStmts.add(innerBlock);
        BlockStmt outerBlock = new BlockStmt(outerStmts);

        eliminator.transform(outerBlock);

        assertTrue(innerBlock.size() >= 2);
    }

    @Test
    void eliminateInIfBranch() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> thenStmts = new ArrayList<>();
        thenStmts.add(decl);
        thenStmts.add(new ExprStmt(assign));
        thenStmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt thenBlock = new BlockStmt(thenStmts);

        IfStmt ifStmt = new IfStmt(
            LiteralExpr.ofBoolean(true),
            thenBlock
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        eliminator.transform(block);

        assertTrue(thenBlock.size() >= 2);
    }

    @Test
    void multipleVariables() {
        VarDeclStmt declX = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        VarDeclStmt declY = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "y",
            LiteralExpr.ofInt(0)
        );

        BinaryExpr assignX = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        BinaryExpr assignY = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("y", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(10),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(declX);
        stmts.add(declY);
        stmts.add(new ExprStmt(assignX));
        stmts.add(new ExprStmt(assignY));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = eliminator.transform(block);

        assertTrue(changed);
    }

    @Test
    void idempotentTransformation() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.INT,
            "x",
            LiteralExpr.ofInt(0)
        );

        BinaryExpr assign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(new ExprStmt(assign));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        boolean firstPass = eliminator.transform(block);
        boolean secondPass = eliminator.transform(block);

        assertTrue(firstPass);
        assertFalse(secondPass);
    }

    @Test
    void noChangeWhenNoDeadStores() {
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
}
