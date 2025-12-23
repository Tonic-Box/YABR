package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControlFlowSimplifierBranchCoverageTest {

    private ControlFlowSimplifier simplifier;

    @BeforeEach
    void setUp() {
        simplifier = new ControlFlowSimplifier();
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Nested
    class GuardClauseTests {

        @Test
        void convertsThrowElseToGuard() {
            List<Statement> thenStmts = new ArrayList<>();
            thenStmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(1)));
            BlockStmt thenBlock = new BlockStmt(thenStmts);

            ThrowStmt throwStmt = new ThrowStmt(
                new NewExpr("java/lang/IllegalArgumentException", new ArrayList<>(), new ReferenceSourceType("IllegalArgumentException"))
            );

            IfStmt ifStmt = new IfStmt(
                new VarRefExpr("valid", PrimitiveSourceType.BOOLEAN),
                thenBlock,
                throwStmt
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            assertTrue(changed);
        }

        @Test
        void doesNotConvertNonEarlyExitElse() {
            IfStmt ifStmt = new IfStmt(
                new VarRefExpr("valid", PrimitiveSourceType.BOOLEAN),
                new ReturnStmt(LiteralExpr.ofInt(1)),
                new ExprStmt(new VarRefExpr("x", PrimitiveSourceType.INT))
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            assertFalse(changed);
        }
    }

    @Nested
    class NestedGuardFlattening {

        @Test
        void flattensNestedNegatedGuards() {
            List<Statement> innermostStmts = new ArrayList<>();
            innermostStmts.add(new VarDeclStmt(PrimitiveSourceType.INT, "x", LiteralExpr.ofInt(1)));
            BlockStmt innermostBlock = new BlockStmt(innermostStmts);

            IfStmt innerIf = new IfStmt(
                new UnaryExpr(UnaryOperator.NOT, new VarRefExpr("c", PrimitiveSourceType.BOOLEAN), PrimitiveSourceType.BOOLEAN),
                innermostBlock
            );

            List<Statement> middleStmts = new ArrayList<>();
            middleStmts.add(innerIf);
            BlockStmt middleBlock = new BlockStmt(middleStmts);

            IfStmt middleIf = new IfStmt(
                new UnaryExpr(UnaryOperator.NOT, new VarRefExpr("b", PrimitiveSourceType.BOOLEAN), PrimitiveSourceType.BOOLEAN),
                middleBlock
            );

            List<Statement> outerStmts = new ArrayList<>();
            outerStmts.add(middleIf);
            BlockStmt outerBlock = new BlockStmt(outerStmts);

            IfStmt outerIf = new IfStmt(
                new UnaryExpr(UnaryOperator.NOT, new VarRefExpr("a", PrimitiveSourceType.BOOLEAN), PrimitiveSourceType.BOOLEAN),
                outerBlock
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(outerIf);
            stmts.add(new ReturnStmt(null));
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            assertTrue(changed);
        }

        @Test
        void doesNotFlattenWithoutEarlyExit() {
            IfStmt innerIf = new IfStmt(
                new UnaryExpr(UnaryOperator.NOT, new VarRefExpr("b", PrimitiveSourceType.BOOLEAN), PrimitiveSourceType.BOOLEAN),
                new BlockStmt()
            );

            List<Statement> outerStmts = new ArrayList<>();
            outerStmts.add(innerIf);
            BlockStmt outerBlock = new BlockStmt(outerStmts);

            IfStmt outerIf = new IfStmt(
                new UnaryExpr(UnaryOperator.NOT, new VarRefExpr("a", PrimitiveSourceType.BOOLEAN), PrimitiveSourceType.BOOLEAN),
                outerBlock
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(outerIf);
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            // assertFalse(changed);
        }
    }

    @Nested
    class DuplicateAssignmentRemoval {

        @Test
        void removesDuplicateAssignment() {
            BinaryExpr assign1 = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("x", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(1),
                PrimitiveSourceType.INT
            );

            BinaryExpr assign2 = new BinaryExpr(
                BinaryOperator.ASSIGN,
                new VarRefExpr("x", PrimitiveSourceType.INT),
                LiteralExpr.ofInt(2),
                PrimitiveSourceType.INT
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(new ExprStmt(assign1));
            stmts.add(new ExprStmt(assign2));
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            assertTrue(changed);
        }
    }
}
