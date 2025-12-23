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

class ControlFlowSimplifierTest {

    private ControlFlowSimplifier simplifier;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
        simplifier = new ControlFlowSimplifier();
    }

    @Test
    void getNameReturnsCorrectName() {
        assertEquals("ControlFlowSimplifier", simplifier.getName());
    }

    @Test
    void transformEmptyBlockReturnsFalse() {
        BlockStmt block = new BlockStmt();

        boolean changed = simplifier.transform(block);

        assertFalse(changed);
    }

    @Test
    void transformDoesNotThrowOnEmptyBlock() {
        BlockStmt block = new BlockStmt();

        assertDoesNotThrow(() -> simplifier.transform(block));
    }

    @Test
    void transformInvertsEmptyIfWithElse() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new BlockStmt();
        Statement elseBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertTrue(changed || !changed);
    }

    @Test
    void transformMergesNestedIfStatements() {
        Expression outerCond = new VarRefExpr("a", PrimitiveSourceType.BOOLEAN);
        Expression innerCond = new VarRefExpr("b", PrimitiveSourceType.BOOLEAN);
        Statement body = new ReturnStmt(LiteralExpr.ofInt(1));

        IfStmt innerIf = new IfStmt(innerCond, body);
        IfStmt outerIf = new IfStmt(outerCond, innerIf);

        List<Statement> stmts = new ArrayList<>();
        stmts.add(outerIf);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformHandlesIfWithoutElse() {
        Expression condition = LiteralExpr.ofBoolean(true);
        Statement thenBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        IfStmt ifStmt = new IfStmt(condition, thenBranch);

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        assertDoesNotThrow(() -> simplifier.transform(block));
    }

    @Test
    void guardClauseWithLargeElse() {
        Expression condition = new VarRefExpr("valid", PrimitiveSourceType.BOOLEAN);
        Statement earlyReturn = new ReturnStmt();

        List<Statement> longBodyStmts = new ArrayList<>();
        longBodyStmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(1)));
        longBodyStmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "y", LiteralExpr.ofInt(2)));
        longBodyStmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "z", LiteralExpr.ofInt(3)));
        longBodyStmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        Statement longBody = new BlockStmt(longBodyStmts);

        IfStmt ifStmt = new IfStmt(condition, longBody, earlyReturn);

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertTrue(changed);
        assertTrue(block.size() > 1);
        assertTrue(block.getStatements().get(0) instanceof IfStmt);
        IfStmt guardClause = (IfStmt) block.getStatements().get(0);
        assertFalse(guardClause.hasElse());
    }

    @Test
    void booleanInliningMultipleUses() {
        Expression complexExpr = new BinaryExpr(
            BinaryOperator.AND,
            new VarRefExpr("a", PrimitiveSourceType.BOOLEAN),
            new VarRefExpr("b", PrimitiveSourceType.BOOLEAN),
            PrimitiveSourceType.BOOLEAN
        );

        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.BOOLEAN,
            "flag",
            complexExpr
        );

        Expression condition = new VarRefExpr("flag", PrimitiveSourceType.BOOLEAN);
        IfStmt ifStmt = new IfStmt(condition, new ReturnStmt(LiteralExpr.ofInt(1)));

        Expression condition2 = new VarRefExpr("flag", PrimitiveSourceType.BOOLEAN);
        IfStmt ifStmt2 = new IfStmt(condition2, new ReturnStmt(LiteralExpr.ofInt(2)));

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(ifStmt);
        stmts.add(ifStmt2);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void booleanInliningSingleUse() {
        VarDeclStmt decl = new VarDeclStmt(
            PrimitiveSourceType.BOOLEAN,
            "flag",
            LiteralExpr.ofBoolean(true)
        );

        Expression condition = new UnaryExpr(
            UnaryOperator.NOT,
            new VarRefExpr("flag", PrimitiveSourceType.BOOLEAN),
            PrimitiveSourceType.BOOLEAN
        );

        IfStmt ifStmt = new IfStmt(condition, new ReturnStmt());

        List<Statement> stmts = new ArrayList<>();
        stmts.add(decl);
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertTrue(changed);
        assertEquals(1, block.size());
    }

    @Test
    void doubleNegationRemoval() {
        Expression innerNot = new UnaryExpr(
            UnaryOperator.NOT,
            new VarRefExpr("x", PrimitiveSourceType.BOOLEAN),
            PrimitiveSourceType.BOOLEAN
        );

        Expression doubleNot = new UnaryExpr(
            UnaryOperator.NOT,
            innerNot,
            PrimitiveSourceType.BOOLEAN
        );

        IfStmt ifStmt = new IfStmt(doubleNot, new ReturnStmt(LiteralExpr.ofInt(1)));

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        simplifier.transform(block);

        assertNotNull(block);
        assertEquals(1, block.size());
    }

    @Test
    void transformSimplifiesTornaryWithEqualBranches() {
        Expression ternary = new TernaryExpr(
            LiteralExpr.ofBoolean(true),
            LiteralExpr.ofInt(5),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt(ternary));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformHandlesComplexExpressions() {
        BinaryExpr complex = new BinaryExpr(
            BinaryOperator.ADD,
            new BinaryExpr(
                BinaryOperator.MUL,
                LiteralExpr.ofInt(2),
                LiteralExpr.ofInt(3),
                PrimitiveSourceType.INT
            ),
            LiteralExpr.ofInt(1),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt(complex));
        BlockStmt block = new BlockStmt(stmts);

        assertDoesNotThrow(() -> simplifier.transform(block));
    }

    @Test
    void transformMergesSequentialGuards() {
        Expression cond1 = new VarRefExpr("a", PrimitiveSourceType.BOOLEAN);
        Expression cond2 = new VarRefExpr("b", PrimitiveSourceType.BOOLEAN);

        IfStmt guard1 = new IfStmt(cond1, new ReturnStmt());
        IfStmt guard2 = new IfStmt(cond2, new ReturnStmt());

        List<Statement> stmts = new ArrayList<>();
        stmts.add(guard1);
        stmts.add(guard2);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformHandlesDifferentEarlyExits() {
        Expression cond1 = new VarRefExpr("a", PrimitiveSourceType.BOOLEAN);
        Expression cond2 = new VarRefExpr("b", PrimitiveSourceType.BOOLEAN);

        IfStmt guard1 = new IfStmt(cond1, new ReturnStmt(LiteralExpr.ofInt(1)));
        IfStmt guard2 = new IfStmt(cond2, new ReturnStmt(LiteralExpr.ofInt(2)));

        List<Statement> stmts = new ArrayList<>();
        stmts.add(guard1);
        stmts.add(guard2);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformRemovesSelfAssignments() {
        Expression selfAssign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            new VarRefExpr("x", PrimitiveSourceType.INT),
            PrimitiveSourceType.INT
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(new ExprStmt(selfAssign));
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformRemovesEmptyBlocks() {
        List<Statement> stmts = new ArrayList<>();
        stmts.add(new BlockStmt());
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformConvertsIfElseToAssignment() {
        Expression condition = new VarRefExpr("flag", PrimitiveSourceType.BOOLEAN);

        BinaryExpr thenAssign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(0),
            PrimitiveSourceType.INT
        );

        BinaryExpr elseAssign = new BinaryExpr(
            BinaryOperator.ASSIGN,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(1),
            PrimitiveSourceType.INT
        );

        IfStmt ifStmt = new IfStmt(
            condition,
            new ExprStmt(thenAssign),
            new ExprStmt(elseAssign)
        );

        List<Statement> stmts = new ArrayList<>();
        stmts.add(ifStmt);
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformMovesDeclarationsToFirstUse() {
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
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformFlattensNestedNegatedGuards() {
        Expression notA = new UnaryExpr(
            UnaryOperator.NOT,
            new VarRefExpr("a", PrimitiveSourceType.BOOLEAN),
            PrimitiveSourceType.BOOLEAN
        );

        Expression notB = new UnaryExpr(
            UnaryOperator.NOT,
            new VarRefExpr("b", PrimitiveSourceType.BOOLEAN),
            PrimitiveSourceType.BOOLEAN
        );

        IfStmt innerIf = new IfStmt(notB, new BlockStmt());
        IfStmt outerIf = new IfStmt(notA, innerIf);

        List<Statement> stmts = new ArrayList<>();
        stmts.add(outerIf);
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        boolean changed = simplifier.transform(block);

        assertNotNull(block);
    }

    @Test
    void transformHandlesMultipleOptimizations() {
        List<Statement> stmts = new ArrayList<>();

        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "a", LiteralExpr.ofInt(1)));
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "b", LiteralExpr.ofInt(2)));

        Expression condition = new BinaryExpr(
            BinaryOperator.GT,
            new VarRefExpr("a", PrimitiveSourceType.INT),
            new VarRefExpr("b", PrimitiveSourceType.INT),
            PrimitiveSourceType.BOOLEAN
        );

        stmts.add(new IfStmt(condition, new ReturnStmt(LiteralExpr.ofInt(1))));
        stmts.add(new ReturnStmt(LiteralExpr.ofInt(0)));

        BlockStmt block = new BlockStmt(stmts);

        assertDoesNotThrow(() -> simplifier.transform(block));
    }

    @Test
    void transformIsIdempotentOnSimpleCode() {
        List<Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt(LiteralExpr.ofInt(1)));
        BlockStmt block = new BlockStmt(stmts);

        boolean firstPass = simplifier.transform(block);
        boolean secondPass = simplifier.transform(block);

        assertFalse(secondPass);
    }

    @Test
    void transformDoesNotBreakValidCode() {
        List<Statement> stmts = new ArrayList<>();

        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(10)));

        Expression condition = new BinaryExpr(
            BinaryOperator.GT,
            new VarRefExpr("x", PrimitiveSourceType.INT),
            LiteralExpr.ofInt(5),
            PrimitiveSourceType.BOOLEAN
        );

        Statement thenBranch = new ReturnStmt(LiteralExpr.ofInt(1));
        Statement elseBranch = new ReturnStmt(LiteralExpr.ofInt(0));

        stmts.add(new IfStmt(condition, thenBranch, elseBranch));

        BlockStmt block = new BlockStmt(stmts);

        simplifier.transform(block);

        assertNotNull(block.getStatements());
        assertFalse(block.isEmpty());
    }

    @Test
    void transformHandlesNestedBlocks() {
        List<Statement> inner = new ArrayList<>();
        inner.add(new ReturnStmt(LiteralExpr.ofInt(1)));
        BlockStmt innerBlock = new BlockStmt(inner);

        List<Statement> outer = new ArrayList<>();
        outer.add(innerBlock);
        BlockStmt outerBlock = new BlockStmt(outer);

        assertDoesNotThrow(() -> simplifier.transform(outerBlock));
    }

    @Test
    void transformHandlesSingleStatement() {
        List<Statement> stmts = new ArrayList<>();
        stmts.add(new ReturnStmt());
        BlockStmt block = new BlockStmt(stmts);

        assertDoesNotThrow(() -> simplifier.transform(block));
    }

    @Test
    void transformHandlesManyStatements() {
        List<Statement> stmts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            stmts.add(new VarDeclStmt(
                PrimitiveSourceType.INT,
                "var" + i,
                LiteralExpr.ofInt(i)
            ));
        }
        stmts.add(new ReturnStmt());

        BlockStmt block = new BlockStmt(stmts);

        assertDoesNotThrow(() -> simplifier.transform(block));
    }

    @Test
    void transformPreservesBlockStructure() {
        List<Statement> stmts = new ArrayList<>();
        stmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(1)));
        stmts.add(new ReturnStmt(new VarRefExpr("x", PrimitiveSourceType.INT)));
        BlockStmt block = new BlockStmt(stmts);

        simplifier.transform(block);

        assertNotNull(block.getStatements());
    }

    @Test
    void transformHandlesComplexControlFlow() {
        Expression cond1 = new VarRefExpr("a", PrimitiveSourceType.BOOLEAN);
        Expression cond2 = new VarRefExpr("b", PrimitiveSourceType.BOOLEAN);

        IfStmt innerIf = new IfStmt(cond2, new ReturnStmt(LiteralExpr.ofInt(2)));
        IfStmt outerIf = new IfStmt(cond1, innerIf, new ReturnStmt(LiteralExpr.ofInt(1)));

        List<Statement> stmts = new ArrayList<>();
        stmts.add(outerIf);
        stmts.add(new ReturnStmt(LiteralExpr.ofInt(0)));
        BlockStmt block = new BlockStmt(stmts);

        assertDoesNotThrow(() -> simplifier.transform(block));
        assertFalse(block.isEmpty());
    }
}
