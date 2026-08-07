package com.tonic.analysis.source.editor.matcher;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.stmt.*;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Predicate-based matcher for filtering statements during AST editing.
 */
public class StmtMatcher
{

    private final Predicate<Statement> predicate;
    private final String description;

    private StmtMatcher(Predicate<Statement> predicate, String description)
    {
        this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
        this.description = description != null ? description : "custom matcher";
    }

    /**
     * Tests if this matcher matches the given statement.
     *
     * @param stmt the statement to test, null is never matched
     * @return true when the predicate accepts the statement
     */
    public boolean matches(Statement stmt)
    {
        return stmt != null && predicate.test(stmt);
    }

    /**
     * Matches any statement of a specific type.
     *
     * @param type the statement class an instance must belong to
     * @return a matcher accepting instances of that class
     */
    public static StmtMatcher ofType(Class<? extends Statement> type)
    {
        return new StmtMatcher(type::isInstance, "ofType(" + type.getSimpleName() + ")");
    }

    /**
     * Matches return statements.
     *
     * @return a matcher accepting any return
     */
    public static StmtMatcher returnStmt()
    {
        return ofType(ReturnStmt.class);
    }

    /**
     * Matches return statements with a value.
     *
     * @return a matcher accepting non-void returns
     */
    public static StmtMatcher returnWithValue()
    {
        return new StmtMatcher(
            stmt -> stmt instanceof ReturnStmt && !((ReturnStmt) stmt).isVoidReturn(),
            "returnWithValue()"
        );
    }

    /**
     * Matches void return statements.
     *
     * @return a matcher accepting returns without a value
     */
    public static StmtMatcher voidReturn()
    {
        return new StmtMatcher(
            stmt -> stmt instanceof ReturnStmt && ((ReturnStmt) stmt).isVoidReturn(),
            "voidReturn()"
        );
    }

    /**
     * Matches throw statements.
     *
     * @return a matcher accepting throws
     */
    public static StmtMatcher throwStmt()
    {
        return ofType(ThrowStmt.class);
    }

    /**
     * Matches if statements.
     *
     * @return a matcher accepting any if
     */
    public static StmtMatcher ifStmt()
    {
        return ofType(IfStmt.class);
    }

    /**
     * Matches if statements with an else branch.
     *
     * @return a matcher accepting ifs that carry an else
     */
    public static StmtMatcher ifElseStmt()
    {
        return new StmtMatcher(stmt -> stmt instanceof IfStmt && ((IfStmt) stmt).hasElse(), "ifElseStmt()");
    }

    /**
     * Matches if statements without an else branch.
     *
     * @return a matcher accepting ifs with no else
     */
    public static StmtMatcher ifOnlyStmt()
    {
        return new StmtMatcher(stmt -> stmt instanceof IfStmt && !((IfStmt) stmt).hasElse(), "ifOnlyStmt()");
    }

    /**
     * Matches any loop statement (for, while, do-while, for-each).
     *
     * @return a matcher accepting all four loop forms
     */
    public static StmtMatcher anyLoop()
    {
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
     *
     * @return a matcher accepting counted for loops
     */
    public static StmtMatcher forStmt()
    {
        return ofType(ForStmt.class);
    }

    /**
     * Matches while statements.
     *
     * @return a matcher accepting while loops
     */
    public static StmtMatcher whileStmt()
    {
        return ofType(WhileStmt.class);
    }

    /**
     * Matches do-while statements.
     *
     * @return a matcher accepting do-while loops
     */
    public static StmtMatcher doWhileStmt()
    {
        return ofType(DoWhileStmt.class);
    }

    /**
     * Matches for-each statements.
     *
     * @return a matcher accepting enhanced for loops
     */
    public static StmtMatcher forEachStmt()
    {
        return ofType(ForEachStmt.class);
    }

    /**
     * Matches try-catch statements.
     *
     * @return a matcher accepting try statements
     */
    public static StmtMatcher tryCatchStmt()
    {
        return ofType(TryCatchStmt.class);
    }

    /**
     * Matches switch statements.
     *
     * @return a matcher accepting switch statements
     */
    public static StmtMatcher switchStmt()
    {
        return ofType(SwitchStmt.class);
    }

    /**
     * Matches synchronized statements.
     *
     * @return a matcher accepting synchronized blocks
     */
    public static StmtMatcher synchronizedStmt()
    {
        return ofType(SynchronizedStmt.class);
    }

    /**
     * Matches block statements.
     *
     * @return a matcher accepting braced blocks
     */
    public static StmtMatcher blockStmt()
    {
        return ofType(BlockStmt.class);
    }

    /**
     * Matches expression statements.
     *
     * @return a matcher accepting expression statements
     */
    public static StmtMatcher exprStmt()
    {
        return ofType(ExprStmt.class);
    }

    /**
     * Matches variable declaration statements.
     *
     * @return a matcher accepting local declarations
     */
    public static StmtMatcher varDeclStmt()
    {
        return ofType(VarDeclStmt.class);
    }

    /**
     * Matches break statements.
     *
     * @return a matcher accepting breaks
     */
    public static StmtMatcher breakStmt()
    {
        return ofType(BreakStmt.class);
    }

    /**
     * Matches continue statements.
     *
     * @return a matcher accepting continues
     */
    public static StmtMatcher continueStmt()
    {
        return ofType(ContinueStmt.class);
    }

    /**
     * Matches labeled statements.
     *
     * @return a matcher accepting labeled statements
     */
    public static StmtMatcher labeledStmt()
    {
        return ofType(LabeledStmt.class);
    }

    /**
     * Matches statements with a specific label.
     *
     * @param label the label a statement must carry
     * @return a matcher accepting statements labeled that way
     */
    public static StmtMatcher withLabel(String label)
    {
        return new StmtMatcher(stmt -> label.equals(stmt.getLabel()), "withLabel(" + label + ")");
    }

    /**
     * Matches expression statements containing a method call.
     *
     * @return a matcher accepting expression statements whose expression is a call
     */
    public static StmtMatcher methodCallStmt()
    {
        return new StmtMatcher(
            stmt -> stmt instanceof ExprStmt &&
                    ((ExprStmt) stmt).getExpression() instanceof MethodCallExpr,
            "methodCallStmt()"
        );
    }

    /**
     * Matches expression statements containing an assignment.
     *
     * @return a matcher accepting expression statements whose expression assigns
     */
    public static StmtMatcher assignmentStmt()
    {
        return new StmtMatcher(
            stmt -> {
                if (!(stmt instanceof ExprStmt)) return false;
                Expression expr = ((ExprStmt) stmt).getExpression();
                return expr instanceof BinaryExpr &&
                       ((BinaryExpr) expr).isAssignment();
            },
            "assignmentStmt()"
        );
    }

    /**
     * Creates a matcher from a custom predicate.
     *
     * @param predicate the test to apply to each statement
     * @return a matcher backed by the predicate, described as "custom"
     */
    public static StmtMatcher custom(Predicate<Statement> predicate)
    {
        return new StmtMatcher(predicate, "custom");
    }

    /**
     * Creates a matcher from a custom predicate with description.
     *
     * @param predicate the test to apply to each statement
     * @param description the text used by {@link #toString()}
     * @return a matcher backed by the predicate
     */
    public static StmtMatcher custom(Predicate<Statement> predicate, String description)
    {
        return new StmtMatcher(predicate, description);
    }

    /**
     * Matches all statements.
     *
     * @return a matcher that always accepts
     */
    public static StmtMatcher any()
    {
        return new StmtMatcher(stmt -> true, "any()");
    }

    /**
     * Matches no statements.
     *
     * @return a matcher that never accepts
     */
    public static StmtMatcher none()
    {
        return new StmtMatcher(stmt -> false, "none()");
    }

    /**
     * Combines this matcher with another using AND logic.
     *
     * @param other the matcher that must also accept
     * @return a matcher accepting only what both accept
     */
    public StmtMatcher and(StmtMatcher other)
    {
        return new StmtMatcher(
            stmt -> this.matches(stmt) && other.matches(stmt),
            "(" + this.description + " && " + other.description + ")"
        );
    }

    /**
     * Combines this matcher with another using OR logic.
     *
     * @param other the alternative matcher
     * @return a matcher accepting what either accepts
     */
    public StmtMatcher or(StmtMatcher other)
    {
        return new StmtMatcher(
            stmt -> this.matches(stmt) || other.matches(stmt),
            "(" + this.description + " || " + other.description + ")"
        );
    }

    /**
     * Negates this matcher.
     *
     * @return a matcher accepting exactly what this one rejects
     */
    public StmtMatcher not()
    {
        return new StmtMatcher(stmt -> !this.matches(stmt), "!" + this.description);
    }

    @Override
    public String toString()
    {
        return "StmtMatcher[" + description + "]";
    }
}
