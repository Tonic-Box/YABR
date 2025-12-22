package com.tonic.analysis.source.editor.matcher;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for StmtMatcher predicate-based statement matching.
 */
@DisplayName("StmtMatcher Tests")
class StmtMatcherTest {

    // Helper methods to create test statements
    private static Expression createBooleanLiteral(boolean value) {
        return LiteralExpr.ofBoolean(value);
    }

    private static Expression createIntLiteral(int value) {
        return LiteralExpr.ofInt(value);
    }

    private static Expression createVarRef(String name) {
        return new VarRefExpr(name, PrimitiveSourceType.INT);
    }

    private static Statement createEmptyBlock() {
        return new BlockStmt(List.of());
    }

    @Nested
    @DisplayName("Basic Matching")
    class BasicMatching {

        @Test
        @DisplayName("matches() returns true when predicate matches")
        void matchesReturnsTrueWhenPredicateMatches() {
            StmtMatcher matcher = StmtMatcher.returnStmt();
            ReturnStmt returnStmt = new ReturnStmt(createIntLiteral(42));

            assertTrue(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("matches() returns false when predicate does not match")
        void matchesReturnsFalseWhenPredicateDoesNotMatch() {
            StmtMatcher matcher = StmtMatcher.returnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertFalse(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("matches() returns false for null statements")
        void matchesReturnsFalseForNull() {
            StmtMatcher matcher = StmtMatcher.returnStmt();

            assertFalse(matcher.matches(null));
        }
    }

    @Nested
    @DisplayName("Return Statement Matchers")
    class ReturnMatchers {

        @Test
        @DisplayName("returnStmt() matches any return statement")
        void returnStmtMatchesAnyReturn() {
            StmtMatcher matcher = StmtMatcher.returnStmt();

            ReturnStmt returnWithValue = new ReturnStmt(createIntLiteral(42));
            ReturnStmt voidReturn = new ReturnStmt();

            assertTrue(matcher.matches(returnWithValue));
            assertTrue(matcher.matches(voidReturn));
        }

        @Test
        @DisplayName("returnStmt() does not match non-return statements")
        void returnStmtDoesNotMatchNonReturn() {
            StmtMatcher matcher = StmtMatcher.returnStmt();

            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertFalse(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("returnWithValue() matches non-void returns")
        void returnWithValueMatchesNonVoidReturns() {
            StmtMatcher matcher = StmtMatcher.returnWithValue();

            ReturnStmt returnWithValue = new ReturnStmt(createIntLiteral(42));

            assertTrue(matcher.matches(returnWithValue));
        }

        @Test
        @DisplayName("returnWithValue() does not match void returns")
        void returnWithValueDoesNotMatchVoidReturns() {
            StmtMatcher matcher = StmtMatcher.returnWithValue();

            ReturnStmt voidReturn = new ReturnStmt();

            assertFalse(matcher.matches(voidReturn));
        }

        @Test
        @DisplayName("voidReturn() matches void returns")
        void voidReturnMatchesVoidReturns() {
            StmtMatcher matcher = StmtMatcher.voidReturn();

            ReturnStmt voidReturn = new ReturnStmt();

            assertTrue(matcher.matches(voidReturn));
        }

        @Test
        @DisplayName("voidReturn() does not match non-void returns")
        void voidReturnDoesNotMatchNonVoidReturns() {
            StmtMatcher matcher = StmtMatcher.voidReturn();

            ReturnStmt returnWithValue = new ReturnStmt(createIntLiteral(42));

            assertFalse(matcher.matches(returnWithValue));
        }
    }

    @Nested
    @DisplayName("Exception Statement Matchers")
    class ExceptionMatchers {

        @Test
        @DisplayName("throwStmt() matches throw statements")
        void throwStmtMatchesThrowStatements() {
            StmtMatcher matcher = StmtMatcher.throwStmt();

            Expression exception = new VarRefExpr("ex", ReferenceSourceType.OBJECT);
            ThrowStmt throwStmt = new ThrowStmt(exception);

            assertTrue(matcher.matches(throwStmt));
        }

        @Test
        @DisplayName("throwStmt() does not match non-throw statements")
        void throwStmtDoesNotMatchNonThrow() {
            StmtMatcher matcher = StmtMatcher.throwStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("tryCatchStmt() matches try-catch statements")
        void tryCatchStmtMatchesTryCatch() {
            StmtMatcher matcher = StmtMatcher.tryCatchStmt();

            BlockStmt tryBlock = new BlockStmt(List.of());
            ReferenceSourceType exceptionType = new ReferenceSourceType("java/lang/Exception");
            CatchClause catchClause = CatchClause.of(exceptionType, "ex", new BlockStmt(List.of()));
            TryCatchStmt tryCatch = new TryCatchStmt(tryBlock, List.of(catchClause), null);

            assertTrue(matcher.matches(tryCatch));
        }

        @Test
        @DisplayName("tryCatchStmt() does not match non-try-catch statements")
        void tryCatchStmtDoesNotMatchNonTryCatch() {
            StmtMatcher matcher = StmtMatcher.tryCatchStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }
    }

    @Nested
    @DisplayName("Conditional Statement Matchers")
    class ConditionalMatchers {

        @Test
        @DisplayName("ifStmt() matches any if statement")
        void ifStmtMatchesAnyIf() {
            StmtMatcher matcher = StmtMatcher.ifStmt();

            IfStmt ifOnly = new IfStmt(createBooleanLiteral(true), createEmptyBlock());
            IfStmt ifElse = new IfStmt(createBooleanLiteral(true), createEmptyBlock(), createEmptyBlock());

            assertTrue(matcher.matches(ifOnly));
            assertTrue(matcher.matches(ifElse));
        }

        @Test
        @DisplayName("ifStmt() does not match non-if statements")
        void ifStmtDoesNotMatchNonIf() {
            StmtMatcher matcher = StmtMatcher.ifStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("ifElseStmt() matches if with else")
        void ifElseStmtMatchesIfWithElse() {
            StmtMatcher matcher = StmtMatcher.ifElseStmt();

            IfStmt ifElse = new IfStmt(createBooleanLiteral(true), createEmptyBlock(), createEmptyBlock());

            assertTrue(matcher.matches(ifElse));
        }

        @Test
        @DisplayName("ifElseStmt() does not match if without else")
        void ifElseStmtDoesNotMatchIfWithoutElse() {
            StmtMatcher matcher = StmtMatcher.ifElseStmt();

            IfStmt ifOnly = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertFalse(matcher.matches(ifOnly));
        }

        @Test
        @DisplayName("ifOnlyStmt() matches if without else")
        void ifOnlyStmtMatchesIfWithoutElse() {
            StmtMatcher matcher = StmtMatcher.ifOnlyStmt();

            IfStmt ifOnly = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(ifOnly));
        }

        @Test
        @DisplayName("ifOnlyStmt() does not match if with else")
        void ifOnlyStmtDoesNotMatchIfWithElse() {
            StmtMatcher matcher = StmtMatcher.ifOnlyStmt();

            IfStmt ifElse = new IfStmt(createBooleanLiteral(true), createEmptyBlock(), createEmptyBlock());

            assertFalse(matcher.matches(ifElse));
        }
    }

    @Nested
    @DisplayName("Loop Statement Matchers")
    class LoopMatchers {

        @Test
        @DisplayName("anyLoop() matches for loops")
        void anyLoopMatchesForLoops() {
            StmtMatcher matcher = StmtMatcher.anyLoop();

            ForStmt forStmt = new ForStmt(List.of(), createBooleanLiteral(true), List.of(), createEmptyBlock());

            assertTrue(matcher.matches(forStmt));
        }

        @Test
        @DisplayName("anyLoop() matches while loops")
        void anyLoopMatchesWhileLoops() {
            StmtMatcher matcher = StmtMatcher.anyLoop();

            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(whileStmt));
        }

        @Test
        @DisplayName("anyLoop() matches do-while loops")
        void anyLoopMatchesDoWhileLoops() {
            StmtMatcher matcher = StmtMatcher.anyLoop();

            DoWhileStmt doWhileStmt = new DoWhileStmt(createEmptyBlock(), createBooleanLiteral(true));

            assertTrue(matcher.matches(doWhileStmt));
        }

        @Test
        @DisplayName("anyLoop() matches for-each loops")
        void anyLoopMatchesForEachLoops() {
            StmtMatcher matcher = StmtMatcher.anyLoop();

            Expression iterable = new VarRefExpr("items", ReferenceSourceType.OBJECT);
            VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "item");
            ForEachStmt forEachStmt = new ForEachStmt(varDecl, iterable, createEmptyBlock());

            assertTrue(matcher.matches(forEachStmt));
        }

        @Test
        @DisplayName("anyLoop() does not match non-loop statements")
        void anyLoopDoesNotMatchNonLoop() {
            StmtMatcher matcher = StmtMatcher.anyLoop();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("forStmt() matches for loops only")
        void forStmtMatchesForLoopsOnly() {
            StmtMatcher matcher = StmtMatcher.forStmt();

            ForStmt forStmt = new ForStmt(List.of(), createBooleanLiteral(true), List.of(), createEmptyBlock());
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(forStmt));
            assertFalse(matcher.matches(whileStmt));
        }

        @Test
        @DisplayName("whileStmt() matches while loops only")
        void whileStmtMatchesWhileLoopsOnly() {
            StmtMatcher matcher = StmtMatcher.whileStmt();

            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());
            ForStmt forStmt = new ForStmt(List.of(), createBooleanLiteral(true), List.of(), createEmptyBlock());

            assertTrue(matcher.matches(whileStmt));
            assertFalse(matcher.matches(forStmt));
        }

        @Test
        @DisplayName("doWhileStmt() matches do-while loops only")
        void doWhileStmtMatchesDoWhileLoopsOnly() {
            StmtMatcher matcher = StmtMatcher.doWhileStmt();

            DoWhileStmt doWhileStmt = new DoWhileStmt(createEmptyBlock(), createBooleanLiteral(true));
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(doWhileStmt));
            assertFalse(matcher.matches(whileStmt));
        }

        @Test
        @DisplayName("forEachStmt() matches for-each loops only")
        void forEachStmtMatchesForEachLoopsOnly() {
            StmtMatcher matcher = StmtMatcher.forEachStmt();

            Expression iterable = new VarRefExpr("items", ReferenceSourceType.OBJECT);
            VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "item");
            ForEachStmt forEachStmt = new ForEachStmt(varDecl, iterable, createEmptyBlock());
            ForStmt forStmt = new ForStmt(List.of(), createBooleanLiteral(true), List.of(), createEmptyBlock());

            assertTrue(matcher.matches(forEachStmt));
            assertFalse(matcher.matches(forStmt));
        }
    }

    @Nested
    @DisplayName("Other Statement Matchers")
    class OtherStatementMatchers {

        @Test
        @DisplayName("switchStmt() matches switch statements")
        void switchStmtMatchesSwitchStatements() {
            StmtMatcher matcher = StmtMatcher.switchStmt();

            SwitchStmt switchStmt = new SwitchStmt(createIntLiteral(1), List.of());

            assertTrue(matcher.matches(switchStmt));
        }

        @Test
        @DisplayName("switchStmt() does not match non-switch statements")
        void switchStmtDoesNotMatchNonSwitch() {
            StmtMatcher matcher = StmtMatcher.switchStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("synchronizedStmt() matches synchronized blocks")
        void synchronizedStmtMatchesSynchronizedBlocks() {
            StmtMatcher matcher = StmtMatcher.synchronizedStmt();

            Expression lock = new VarRefExpr("lock", ReferenceSourceType.OBJECT);
            SynchronizedStmt syncStmt = new SynchronizedStmt(lock, createEmptyBlock());

            assertTrue(matcher.matches(syncStmt));
        }

        @Test
        @DisplayName("synchronizedStmt() does not match non-synchronized statements")
        void synchronizedStmtDoesNotMatchNonSynchronized() {
            StmtMatcher matcher = StmtMatcher.synchronizedStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("blockStmt() matches block statements")
        void blockStmtMatchesBlockStatements() {
            StmtMatcher matcher = StmtMatcher.blockStmt();

            BlockStmt blockStmt = new BlockStmt(List.of());

            assertTrue(matcher.matches(blockStmt));
        }

        @Test
        @DisplayName("blockStmt() does not match non-block statements")
        void blockStmtDoesNotMatchNonBlock() {
            StmtMatcher matcher = StmtMatcher.blockStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("exprStmt() matches expression statements")
        void exprStmtMatchesExpressionStatements() {
            StmtMatcher matcher = StmtMatcher.exprStmt();

            ExprStmt exprStmt = new ExprStmt(createIntLiteral(42));

            assertTrue(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("exprStmt() does not match non-expression statements")
        void exprStmtDoesNotMatchNonExpression() {
            StmtMatcher matcher = StmtMatcher.exprStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("varDeclStmt() matches variable declarations")
        void varDeclStmtMatchesVariableDeclarations() {
            StmtMatcher matcher = StmtMatcher.varDeclStmt();

            VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "x", createIntLiteral(42));

            assertTrue(matcher.matches(varDecl));
        }

        @Test
        @DisplayName("varDeclStmt() does not match non-variable-declaration statements")
        void varDeclStmtDoesNotMatchNonVarDecl() {
            StmtMatcher matcher = StmtMatcher.varDeclStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }
    }

    @Nested
    @DisplayName("Jump Statement Matchers")
    class JumpStatementMatchers {

        @Test
        @DisplayName("breakStmt() matches break statements")
        void breakStmtMatchesBreakStatements() {
            StmtMatcher matcher = StmtMatcher.breakStmt();

            BreakStmt breakStmt = new BreakStmt();

            assertTrue(matcher.matches(breakStmt));
        }

        @Test
        @DisplayName("breakStmt() does not match non-break statements")
        void breakStmtDoesNotMatchNonBreak() {
            StmtMatcher matcher = StmtMatcher.breakStmt();

            ContinueStmt continueStmt = new ContinueStmt();

            assertFalse(matcher.matches(continueStmt));
        }

        @Test
        @DisplayName("continueStmt() matches continue statements")
        void continueStmtMatchesContinueStatements() {
            StmtMatcher matcher = StmtMatcher.continueStmt();

            ContinueStmt continueStmt = new ContinueStmt();

            assertTrue(matcher.matches(continueStmt));
        }

        @Test
        @DisplayName("continueStmt() does not match non-continue statements")
        void continueStmtDoesNotMatchNonContinue() {
            StmtMatcher matcher = StmtMatcher.continueStmt();

            BreakStmt breakStmt = new BreakStmt();

            assertFalse(matcher.matches(breakStmt));
        }

        @Test
        @DisplayName("labeledStmt() matches labeled statements")
        void labeledStmtMatchesLabeledStatements() {
            StmtMatcher matcher = StmtMatcher.labeledStmt();

            LabeledStmt labeledStmt = new LabeledStmt("loop", createEmptyBlock());

            assertTrue(matcher.matches(labeledStmt));
        }

        @Test
        @DisplayName("labeledStmt() does not match non-labeled statements")
        void labeledStmtDoesNotMatchNonLabeled() {
            StmtMatcher matcher = StmtMatcher.labeledStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("withLabel() matches statements with specific label")
        void withLabelMatchesSpecificLabel() {
            StmtMatcher matcher = StmtMatcher.withLabel("loop");

            LabeledStmt labeledStmt = new LabeledStmt("loop", createEmptyBlock());

            assertTrue(matcher.matches(labeledStmt));
        }

        @Test
        @DisplayName("withLabel() does not match statements with different label")
        void withLabelDoesNotMatchDifferentLabel() {
            StmtMatcher matcher = StmtMatcher.withLabel("loop");

            LabeledStmt labeledStmt = new LabeledStmt("other", createEmptyBlock());

            assertFalse(matcher.matches(labeledStmt));
        }

        @Test
        @DisplayName("withLabel() does not match statements without label")
        void withLabelDoesNotMatchNoLabel() {
            StmtMatcher matcher = StmtMatcher.withLabel("loop");

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }
    }

    @Nested
    @DisplayName("Expression Statement Matchers")
    class ExpressionStatementMatchers {

        @Test
        @DisplayName("methodCallStmt() matches ExprStmt containing MethodCallExpr")
        void methodCallStmtMatchesMethodCalls() {
            StmtMatcher matcher = StmtMatcher.methodCallStmt();

            MethodCallExpr methodCall = MethodCallExpr.staticCall("java/lang/System", "println",
                List.of(createIntLiteral(42)), VoidSourceType.INSTANCE);
            ExprStmt exprStmt = new ExprStmt(methodCall);

            assertTrue(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("methodCallStmt() does not match ExprStmt without MethodCallExpr")
        void methodCallStmtDoesNotMatchNonMethodCall() {
            StmtMatcher matcher = StmtMatcher.methodCallStmt();

            ExprStmt exprStmt = new ExprStmt(createIntLiteral(42));

            assertFalse(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("methodCallStmt() does not match non-ExprStmt")
        void methodCallStmtDoesNotMatchNonExprStmt() {
            StmtMatcher matcher = StmtMatcher.methodCallStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("assignmentStmt() matches ExprStmt containing assignment BinaryExpr")
        void assignmentStmtMatchesAssignments() {
            StmtMatcher matcher = StmtMatcher.assignmentStmt();

            BinaryExpr assignment = new BinaryExpr(BinaryOperator.ASSIGN,
                createVarRef("x"), createIntLiteral(42), PrimitiveSourceType.INT);
            ExprStmt exprStmt = new ExprStmt(assignment);

            assertTrue(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("assignmentStmt() matches compound assignment operators")
        void assignmentStmtMatchesCompoundAssignments() {
            StmtMatcher matcher = StmtMatcher.assignmentStmt();

            BinaryExpr addAssign = new BinaryExpr(BinaryOperator.ADD_ASSIGN,
                createVarRef("x"), createIntLiteral(5), PrimitiveSourceType.INT);
            ExprStmt exprStmt = new ExprStmt(addAssign);

            assertTrue(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("assignmentStmt() does not match non-assignment BinaryExpr")
        void assignmentStmtDoesNotMatchNonAssignment() {
            StmtMatcher matcher = StmtMatcher.assignmentStmt();

            BinaryExpr addition = new BinaryExpr(BinaryOperator.ADD,
                createIntLiteral(1), createIntLiteral(2), PrimitiveSourceType.INT);
            ExprStmt exprStmt = new ExprStmt(addition);

            assertFalse(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("assignmentStmt() does not match ExprStmt without BinaryExpr")
        void assignmentStmtDoesNotMatchNonBinaryExpr() {
            StmtMatcher matcher = StmtMatcher.assignmentStmt();

            ExprStmt exprStmt = new ExprStmt(createIntLiteral(42));

            assertFalse(matcher.matches(exprStmt));
        }

        @Test
        @DisplayName("assignmentStmt() does not match non-ExprStmt")
        void assignmentStmtDoesNotMatchNonExprStmt() {
            StmtMatcher matcher = StmtMatcher.assignmentStmt();

            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }
    }

    @Nested
    @DisplayName("Combinator Operations")
    class Combinators {

        @Test
        @DisplayName("and() combines matchers with AND logic")
        void andCombinesWithAndLogic() {
            StmtMatcher matcher = StmtMatcher.returnStmt().and(StmtMatcher.returnWithValue());

            ReturnStmt returnWithValue = new ReturnStmt(createIntLiteral(42));
            ReturnStmt voidReturn = new ReturnStmt();

            assertTrue(matcher.matches(returnWithValue));
            assertFalse(matcher.matches(voidReturn));
        }

        @Test
        @DisplayName("or() combines matchers with OR logic")
        void orCombinesWithOrLogic() {
            StmtMatcher matcher = StmtMatcher.returnStmt().or(StmtMatcher.throwStmt());

            ReturnStmt returnStmt = new ReturnStmt();
            Expression exception = new VarRefExpr("ex", ReferenceSourceType.OBJECT);
            ThrowStmt throwStmt = new ThrowStmt(exception);
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(returnStmt));
            assertTrue(matcher.matches(throwStmt));
            assertFalse(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("not() negates matcher")
        void notNegatesMatcher() {
            StmtMatcher matcher = StmtMatcher.returnStmt().not();

            ReturnStmt returnStmt = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertFalse(matcher.matches(returnStmt));
            assertTrue(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("combinators can be chained")
        void combinatorsCanBeChained() {
            // Matches return statements that are NOT void returns
            StmtMatcher matcher = StmtMatcher.returnStmt()
                .and(StmtMatcher.voidReturn().not());

            ReturnStmt returnWithValue = new ReturnStmt(createIntLiteral(42));
            ReturnStmt voidReturn = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(returnWithValue));
            assertFalse(matcher.matches(voidReturn));
            assertFalse(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("complex combinator expressions work correctly")
        void complexCombinatorExpressionsWork() {
            // Matches (if statements OR while loops) AND NOT labeled statements
            StmtMatcher matcher = StmtMatcher.ifStmt()
                .or(StmtMatcher.whileStmt())
                .and(StmtMatcher.labeledStmt().not());

            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());
            LabeledStmt labeledIf = new LabeledStmt("label",
                new IfStmt(createBooleanLiteral(true), createEmptyBlock()));
            ReturnStmt returnStmt = new ReturnStmt();

            assertTrue(matcher.matches(ifStmt));
            assertTrue(matcher.matches(whileStmt));
            assertFalse(matcher.matches(labeledIf));
            assertFalse(matcher.matches(returnStmt));
        }

        @Test
        @DisplayName("any() matches all statements")
        void anyMatchesAllStatements() {
            StmtMatcher matcher = StmtMatcher.any();

            ReturnStmt returnStmt = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(returnStmt));
            assertTrue(matcher.matches(ifStmt));
            assertTrue(matcher.matches(whileStmt));
        }

        @Test
        @DisplayName("none() matches no statements")
        void noneMatchesNoStatements() {
            StmtMatcher matcher = StmtMatcher.none();

            ReturnStmt returnStmt = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());

            assertFalse(matcher.matches(returnStmt));
            assertFalse(matcher.matches(ifStmt));
            assertFalse(matcher.matches(whileStmt));
        }
    }

    @Nested
    @DisplayName("Custom Matchers")
    class CustomMatchers {

        @Test
        @DisplayName("custom() creates matcher from predicate")
        void customCreatesMatcherFromPredicate() {
            StmtMatcher matcher = StmtMatcher.custom(stmt -> stmt instanceof ReturnStmt);

            ReturnStmt returnStmt = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(returnStmt));
            assertFalse(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("custom() with description creates matcher with description")
        void customWithDescriptionCreatesMatcherWithDescription() {
            StmtMatcher matcher = StmtMatcher.custom(
                stmt -> stmt instanceof ReturnStmt,
                "returns only"
            );

            ReturnStmt returnStmt = new ReturnStmt();

            assertTrue(matcher.matches(returnStmt));
            assertTrue(matcher.toString().contains("returns only"));
        }

        @Test
        @DisplayName("custom() matcher can use complex predicates")
        void customMatcherCanUseComplexPredicates() {
            // Match if statements with else branches that are also if statements (else-if)
            StmtMatcher matcher = StmtMatcher.custom(stmt -> {
                if (!(stmt instanceof IfStmt)) return false;
                IfStmt ifStmt = (IfStmt) stmt;
                return ifStmt.hasElse() && ifStmt.getElseBranch() instanceof IfStmt;
            }, "else-if chains");

            IfStmt simpleIf = new IfStmt(createBooleanLiteral(true), createEmptyBlock());
            IfStmt ifElseBlock = new IfStmt(createBooleanLiteral(true),
                createEmptyBlock(), createEmptyBlock());
            IfStmt elseIf = new IfStmt(createBooleanLiteral(true), createEmptyBlock(),
                new IfStmt(createBooleanLiteral(false), createEmptyBlock()));

            assertFalse(matcher.matches(simpleIf));
            assertFalse(matcher.matches(ifElseBlock));
            assertTrue(matcher.matches(elseIf));
        }

        @Test
        @DisplayName("custom() throws NullPointerException for null predicate")
        void customThrowsForNullPredicate() {
            assertThrows(NullPointerException.class, () -> {
                StmtMatcher.custom(null);
            });
        }
    }

    @Nested
    @DisplayName("Type-Based Matching")
    class TypeBasedMatching {

        @Test
        @DisplayName("ofType() matches statements of specific type")
        void ofTypeMatchesSpecificType() {
            StmtMatcher matcher = StmtMatcher.ofType(ReturnStmt.class);

            ReturnStmt returnStmt = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(returnStmt));
            assertFalse(matcher.matches(ifStmt));
        }

        @Test
        @DisplayName("ofType() can match interface types")
        void ofTypeCanMatchInterfaceTypes() {
            StmtMatcher matcher = StmtMatcher.ofType(Statement.class);

            ReturnStmt returnStmt = new ReturnStmt();
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), createEmptyBlock());
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());

            assertTrue(matcher.matches(returnStmt));
            assertTrue(matcher.matches(ifStmt));
            assertTrue(matcher.matches(whileStmt));
        }
    }

    @Nested
    @DisplayName("toString() Behavior")
    class ToStringBehavior {

        @Test
        @DisplayName("toString() returns descriptive representation")
        void toStringReturnsDescriptiveRepresentation() {
            StmtMatcher matcher = StmtMatcher.returnStmt();

            String result = matcher.toString();

            assertTrue(result.contains("StmtMatcher"));
            assertTrue(result.contains("ReturnStmt"));
        }

        @Test
        @DisplayName("toString() for combinators shows combined logic")
        void toStringForCombinatorsShowsCombinedLogic() {
            StmtMatcher matcher = StmtMatcher.returnStmt().and(StmtMatcher.returnWithValue());

            String result = matcher.toString();

            assertTrue(result.contains("&&"));
        }

        @Test
        @DisplayName("toString() for negation shows negation operator")
        void toStringForNegationShowsNegationOperator() {
            StmtMatcher matcher = StmtMatcher.returnStmt().not();

            String result = matcher.toString();

            assertTrue(result.contains("!"));
        }

        @Test
        @DisplayName("toString() for custom matcher shows description")
        void toStringForCustomMatcherShowsDescription() {
            StmtMatcher matcher = StmtMatcher.custom(stmt -> true, "my custom matcher");

            String result = matcher.toString();

            assertTrue(result.contains("my custom matcher"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("matcher works with nested statements")
        void matcherWorksWithNestedStatements() {
            StmtMatcher returnMatcher = StmtMatcher.returnStmt();

            // Create an if statement containing a return
            ReturnStmt returnStmt = new ReturnStmt(createIntLiteral(42));
            IfStmt ifStmt = new IfStmt(createBooleanLiteral(true), returnStmt);

            // Matcher should match the return but not the if
            assertTrue(returnMatcher.matches(returnStmt));
            assertFalse(returnMatcher.matches(ifStmt));
        }

        @Test
        @DisplayName("matcher handles empty block statements")
        void matcherHandlesEmptyBlocks() {
            StmtMatcher blockMatcher = StmtMatcher.blockStmt();

            BlockStmt emptyBlock = new BlockStmt(List.of());

            assertTrue(blockMatcher.matches(emptyBlock));
        }

        @Test
        @DisplayName("matcher can distinguish loop types")
        void matcherCanDistinguishLoopTypes() {
            ForStmt forStmt = new ForStmt(List.of(), createBooleanLiteral(true),
                List.of(), createEmptyBlock());
            WhileStmt whileStmt = new WhileStmt(createBooleanLiteral(true), createEmptyBlock());
            DoWhileStmt doWhileStmt = new DoWhileStmt(createEmptyBlock(), createBooleanLiteral(true));
            Expression iterable = new VarRefExpr("items", ReferenceSourceType.OBJECT);
            VarDeclStmt varDecl = new VarDeclStmt(PrimitiveSourceType.INT, "item");
            ForEachStmt forEachStmt = new ForEachStmt(varDecl, iterable, createEmptyBlock());

            // anyLoop matches all
            assertTrue(StmtMatcher.anyLoop().matches(forStmt));
            assertTrue(StmtMatcher.anyLoop().matches(whileStmt));
            assertTrue(StmtMatcher.anyLoop().matches(doWhileStmt));
            assertTrue(StmtMatcher.anyLoop().matches(forEachStmt));

            // Specific matchers are exclusive
            assertTrue(StmtMatcher.forStmt().matches(forStmt));
            assertFalse(StmtMatcher.forStmt().matches(whileStmt));

            assertTrue(StmtMatcher.whileStmt().matches(whileStmt));
            assertFalse(StmtMatcher.whileStmt().matches(doWhileStmt));

            assertTrue(StmtMatcher.doWhileStmt().matches(doWhileStmt));
            assertFalse(StmtMatcher.doWhileStmt().matches(forEachStmt));

            assertTrue(StmtMatcher.forEachStmt().matches(forEachStmt));
            assertFalse(StmtMatcher.forEachStmt().matches(forStmt));
        }

        @Test
        @DisplayName("withLabel() handles null label in statement")
        void withLabelHandlesNullLabelInStatement() {
            StmtMatcher matcher = StmtMatcher.withLabel("test");

            // Most statements return null for getLabel()
            ReturnStmt returnStmt = new ReturnStmt();

            assertFalse(matcher.matches(returnStmt));
        }
    }
}
