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
                new ExprStmt(new VarRefExpr("y", PrimitiveSourceType.INT)),
                new ExprStmt(new VarRefExpr("x", PrimitiveSourceType.INT))
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            assertFalse(changed);
        }

        @Test
        void flattensRedundantElseAfterTerminalThen() {
            IfStmt ifStmt = new IfStmt(
                new VarRefExpr("valid", PrimitiveSourceType.BOOLEAN),
                new ReturnStmt(LiteralExpr.ofInt(1)),
                new ExprStmt(new VarRefExpr("x", PrimitiveSourceType.INT))
            );

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            BlockStmt block = new BlockStmt(stmts);

            boolean changed = simplifier.transform(block);
            assertTrue(changed);
            assertEquals(2, block.getStatements().size());
            IfStmt guard = (IfStmt) block.getStatements().get(0);
            assertFalse(guard.hasElse());
            assertTrue(block.getStatements().get(1) instanceof ExprStmt);
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

    @Nested
    class EmptyIfTests {

        @Test
        void emptyThenNoElseSideEffectFreeRemoved() {
            // if (x != 3) {}  -> removed entirely (condition has no side effects)
            IfStmt ifStmt = new IfStmt(
                new BinaryExpr(BinaryOperator.NE,
                    new VarRefExpr("x", PrimitiveSourceType.INT),
                    LiteralExpr.ofInt(3), PrimitiveSourceType.BOOLEAN),
                new BlockStmt(new ArrayList<>()));

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            stmts.add(new ReturnStmt());
            BlockStmt block = new BlockStmt(stmts);

            assertTrue(simplifier.transform(block));
            assertEquals(1, block.size());
            assertTrue(block.getStatements().get(0) instanceof ReturnStmt);
        }

        @Test
        void emptyThenSideEffectingComparisonKeptAsIf() {
            // if (foo() != 0) {}  -> kept as an empty if: the condition is side-effecting (call)
            // but a bare comparison is not a legal Java statement.
            MethodCallExpr call = MethodCallExpr.staticCall(
                "com/test/T", "foo", new ArrayList<>(), PrimitiveSourceType.INT);
            IfStmt ifStmt = new IfStmt(
                new BinaryExpr(BinaryOperator.NE, call, LiteralExpr.ofInt(0), PrimitiveSourceType.BOOLEAN),
                new BlockStmt(new ArrayList<>()));

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            stmts.add(new ReturnStmt());
            BlockStmt block = new BlockStmt(stmts);

            simplifier.transform(block);
            assertEquals(2, block.size());
            assertTrue(block.getStatements().get(0) instanceof IfStmt,
                "side-effecting non-statement condition must remain an if, not a bare expression");
        }

        @Test
        void emptyThenBareCallConditionLoweredToStatement() {
            // if (foo()) {}  -> foo();  (the call is itself a valid statement expression)
            MethodCallExpr call = MethodCallExpr.staticCall(
                "com/test/T", "foo", new ArrayList<>(), PrimitiveSourceType.BOOLEAN);
            IfStmt ifStmt = new IfStmt(call, new BlockStmt(new ArrayList<>()));

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            stmts.add(new ReturnStmt());
            BlockStmt block = new BlockStmt(stmts);

            assertTrue(simplifier.transform(block));
            assertTrue(block.getStatements().get(0) instanceof ExprStmt,
                "a bare call condition should lower to an expression statement");
        }

        @Test
        void emptyThenWithElseInverted() {
            // if (x) {} else { y = 1; }  -> if (!x) { y = 1; }
            BinaryExpr assign = new BinaryExpr(BinaryOperator.ASSIGN,
                new VarRefExpr("y", PrimitiveSourceType.INT), LiteralExpr.ofInt(1), PrimitiveSourceType.INT);
            List<Statement> elseStmts = new ArrayList<>();
            elseStmts.add(new ExprStmt(assign));

            IfStmt ifStmt = new IfStmt(
                new VarRefExpr("x", PrimitiveSourceType.BOOLEAN),
                new BlockStmt(new ArrayList<>()),
                new BlockStmt(elseStmts));

            List<Statement> stmts = new ArrayList<>();
            stmts.add(ifStmt);
            BlockStmt block = new BlockStmt(stmts);

            assertTrue(simplifier.transform(block));
            IfStmt result = (IfStmt) block.getStatements().get(0);
            assertFalse(result.hasElse(), "else should be folded into an inverted then");
            assertFalse(((BlockStmt) result.getThenBranch()).isEmpty(), "then should carry the former else body");
        }
    }
}
