package com.tonic.analysis.source.editor.matcher;

import com.tonic.analysis.source.ast.stmt.*;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Predicate-based matcher for filtering statements during AST editing.
 * Provides factory methods for common matching patterns and combinators.
 */
public class StmtMatcher {

    private final Predicate<Statement> predicate;
    private final String description;

    private StmtMatcher(Predicate<Statement> predicate, String description) {
        this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
        this.description = description != null ? description : "custom matcher";
    }

    /**
     * Tests if this matcher matches the given statement.
     */
    public boolean matches(Statement stmt) {
        return stmt != null && predicate.test(stmt);
    }

    /**
     * Matches any statement of a specific type.
     */
    public static StmtMatcher ofType(Class<? extends Statement> type) {
        return new StmtMatcher(
            stmt -> type.isInstance(stmt),
            "ofType(" + type.getSimpleName() + ")"
        );
    }

    /**
     * Matches return statements.
     */
    public static StmtMatcher returnStmt() {
        return ofType(ReturnStmt.class);
    }

    /**
     * Matches return statements with a value.
     */
    public static StmtMatcher returnWithValue() {
        return new StmtMatcher(
            stmt -> stmt instanceof ReturnStmt && !((ReturnStmt) stmt).isVoidReturn(),
            "returnWithValue()"
        );
    }

    /**
     * Matches void return statements.
     */
    public static StmtMatcher voidReturn() {
        return new StmtMatcher(
            stmt -> stmt instanceof ReturnStmt && ((ReturnStmt) stmt).isVoidReturn(),
            "voidReturn()"
        );
    }

    /**
     * Matches throw statements.
     */
    public static StmtMatcher throwStmt() {
        return ofType(ThrowStmt.class);
    }

    /**
     * Matches if statements.
     */
    public static StmtMatcher ifStmt() {
        return ofType(IfStmt.class);
    }

    /**
     * Matches if statements with an else branch.
     */
    public static StmtMatcher ifElseStmt() {
        return new StmtMatcher(
            stmt -> stmt instanceof IfStmt && ((IfStmt) stmt).hasElse(),
            "ifElseStmt()"
        );
    }

    /**
     * Matches if statements without an else branch.
     */
    public static StmtMatcher ifOnlyStmt() {
        return new StmtMatcher(
            stmt -> stmt instanceof IfStmt && !((IfStmt) stmt).hasElse(),
            "ifOnlyStmt()"
        );
    }

    /**
     * Matches any loop statement (for, while, do-while, for-each).
     */
    public static StmtMatcher anyLoop() {
        return new StmtMatcher(
            stmt -> stmt instanceof ForStmt ||
                    stmt instanceof WhileStmt ||
                    stmt instanceof DoWhileStmt ||
                    stmt instanceof ForEachStmt,
            "anyLoop()"
        );
    }

    /**
     * Matches for statements.
     */
    public static StmtMatcher forStmt() {
        return ofType(ForStmt.class);
    }

    /**
     * Matches while statements.
     */
    public static StmtMatcher whileStmt() {
        return ofType(WhileStmt.class);
    }

    /**
     * Matches do-while statements.
     */
    public static StmtMatcher doWhileStmt() {
        return ofType(DoWhileStmt.class);
    }

    /**
     * Matches for-each statements.
     */
    public static StmtMatcher forEachStmt() {
        return ofType(ForEachStmt.class);
    }

    /**
     * Matches try-catch statements.
     */
    public static StmtMatcher tryCatchStmt() {
        return ofType(TryCatchStmt.class);
    }

    /**
     * Matches switch statements.
     */
    public static StmtMatcher switchStmt() {
        return ofType(SwitchStmt.class);
    }

    /**
     * Matches synchronized statements.
     */
    public static StmtMatcher synchronizedStmt() {
        return ofType(SynchronizedStmt.class);
    }

    /**
     * Matches block statements.
     */
    public static StmtMatcher blockStmt() {
        return ofType(BlockStmt.class);
    }

    /**
     * Matches expression statements.
     */
    public static StmtMatcher exprStmt() {
        return ofType(ExprStmt.class);
    }

    /**
     * Matches variable declaration statements.
     */
    public static StmtMatcher varDeclStmt() {
        return ofType(VarDeclStmt.class);
    }

    /**
     * Matches break statements.
     */
    public static StmtMatcher breakStmt() {
        return ofType(BreakStmt.class);
    }

    /**
     * Matches continue statements.
     */
    public static StmtMatcher continueStmt() {
        return ofType(ContinueStmt.class);
    }

    /**
     * Matches labeled statements.
     */
    public static StmtMatcher labeledStmt() {
        return ofType(LabeledStmt.class);
    }

    /**
     * Matches statements with a specific label.
     */
    public static StmtMatcher withLabel(String label) {
        return new StmtMatcher(
            stmt -> label.equals(stmt.getLabel()),
            "withLabel(" + label + ")"
        );
    }

    /**
     * Matches expression statements containing a method call.
     */
    public static StmtMatcher methodCallStmt() {
        return new StmtMatcher(
            stmt -> stmt instanceof ExprStmt &&
                    ((ExprStmt) stmt).getExpression() instanceof com.tonic.analysis.source.ast.expr.MethodCallExpr,
            "methodCallStmt()"
        );
    }

    /**
     * Matches expression statements containing an assignment.
     */
    public static StmtMatcher assignmentStmt() {
        return new StmtMatcher(
            stmt -> {
                if (!(stmt instanceof ExprStmt)) return false;
                com.tonic.analysis.source.ast.expr.Expression expr = ((ExprStmt) stmt).getExpression();
                return expr instanceof com.tonic.analysis.source.ast.expr.BinaryExpr &&
                       ((com.tonic.analysis.source.ast.expr.BinaryExpr) expr).isAssignment();
            },
            "assignmentStmt()"
        );
    }

    /**
     * Creates a matcher from a custom predicate.
     */
    public static StmtMatcher custom(Predicate<Statement> predicate) {
        return new StmtMatcher(predicate, "custom");
    }

    /**
     * Creates a matcher from a custom predicate with description.
     */
    public static StmtMatcher custom(Predicate<Statement> predicate, String description) {
        return new StmtMatcher(predicate, description);
    }

    /**
     * Matches all statements.
     */
    public static StmtMatcher any() {
        return new StmtMatcher(stmt -> true, "any()");
    }

    /**
     * Matches no statements.
     */
    public static StmtMatcher none() {
        return new StmtMatcher(stmt -> false, "none()");
    }

    /**
     * Combines this matcher with another using AND logic.
     */
    public StmtMatcher and(StmtMatcher other) {
        return new StmtMatcher(
            stmt -> this.matches(stmt) && other.matches(stmt),
            "(" + this.description + " && " + other.description + ")"
        );
    }

    /**
     * Combines this matcher with another using OR logic.
     */
    public StmtMatcher or(StmtMatcher other) {
        return new StmtMatcher(
            stmt -> this.matches(stmt) || other.matches(stmt),
            "(" + this.description + " || " + other.description + ")"
        );
    }

    /**
     * Negates this matcher.
     */
    public StmtMatcher not() {
        return new StmtMatcher(
            stmt -> !this.matches(stmt),
            "!" + this.description
        );
    }

    @Override
    public String toString() {
        return "StmtMatcher[" + description + "]";
    }
}
