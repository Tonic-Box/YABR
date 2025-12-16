package com.tonic.analysis.source.editor.matcher;

import com.tonic.analysis.source.ast.expr.*;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Predicate-based matcher for filtering expressions during AST editing.
 * Provides factory methods for common matching patterns and combinators.
 */
public class ExprMatcher {

    private final Predicate<Expression> predicate;
    private final String description;

    private ExprMatcher(Predicate<Expression> predicate, String description) {
        this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
        this.description = description != null ? description : "custom matcher";
    }

    /**
     * Tests if this matcher matches the given expression.
     */
    public boolean matches(Expression expr) {
        return expr != null && predicate.test(expr);
    }

    /**
     * Matches method calls by method name only.
     */
    public static ExprMatcher methodCall(String methodName) {
        return new ExprMatcher(
            expr -> expr instanceof MethodCallExpr &&
                    ((MethodCallExpr) expr).getMethodName().equals(methodName),
            "methodCall(" + methodName + ")"
        );
    }

    /**
     * Matches method calls by owner class and method name.
     */
    public static ExprMatcher methodCall(String ownerClass, String methodName) {
        String normalizedOwner = ownerClass.replace('.', '/');
        return new ExprMatcher(
            expr -> {
                if (!(expr instanceof MethodCallExpr)) return false;
                MethodCallExpr call = (MethodCallExpr) expr;
                return call.getMethodName().equals(methodName) &&
                       call.getOwnerClass().equals(normalizedOwner);
            },
            "methodCall(" + ownerClass + "." + methodName + ")"
        );
    }

    /**
     * Matches method calls by owner class, method name, and argument count.
     */
    public static ExprMatcher methodCall(String ownerClass, String methodName, int argCount) {
        String normalizedOwner = ownerClass.replace('.', '/');
        return new ExprMatcher(
            expr -> {
                if (!(expr instanceof MethodCallExpr)) return false;
                MethodCallExpr call = (MethodCallExpr) expr;
                return call.getMethodName().equals(methodName) &&
                       call.getOwnerClass().equals(normalizedOwner) &&
                       call.getArgumentCount() == argCount;
            },
            "methodCall(" + ownerClass + "." + methodName + "/" + argCount + ")"
        );
    }

    /**
     * Matches field accesses by field name only.
     */
    public static ExprMatcher fieldAccess(String fieldName) {
        return new ExprMatcher(
            expr -> expr instanceof FieldAccessExpr &&
                    ((FieldAccessExpr) expr).getFieldName().equals(fieldName),
            "fieldAccess(" + fieldName + ")"
        );
    }

    /**
     * Matches field accesses by owner class and field name.
     */
    public static ExprMatcher fieldAccess(String ownerClass, String fieldName) {
        String normalizedOwner = ownerClass.replace('.', '/');
        return new ExprMatcher(
            expr -> {
                if (!(expr instanceof FieldAccessExpr)) return false;
                FieldAccessExpr access = (FieldAccessExpr) expr;
                return access.getFieldName().equals(fieldName) &&
                       access.getOwnerClass().equals(normalizedOwner);
            },
            "fieldAccess(" + ownerClass + "." + fieldName + ")"
        );
    }

    /**
     * Matches new expressions by class name.
     */
    public static ExprMatcher newExpr(String className) {
        String normalizedClass = className.replace('.', '/');
        return new ExprMatcher(
            expr -> expr instanceof NewExpr &&
                    ((NewExpr) expr).getClassName().equals(normalizedClass),
            "newExpr(" + className + ")"
        );
    }

    /**
     * Matches new array expressions.
     */
    public static ExprMatcher newArray() {
        return new ExprMatcher(
            expr -> expr instanceof NewArrayExpr,
            "newArray()"
        );
    }

    /**
     * Matches cast expressions to a specific type.
     */
    public static ExprMatcher cast(String targetType) {
        return new ExprMatcher(
            expr -> {
                if (!(expr instanceof CastExpr)) return false;
                CastExpr cast = (CastExpr) expr;
                return cast.getTargetType().toJavaSource().equals(targetType) ||
                       cast.getTargetType().toJavaSource().equals(targetType.replace('/', '.'));
            },
            "cast(" + targetType + ")"
        );
    }

    /**
     * Matches any cast expression.
     */
    public static ExprMatcher anyCast() {
        return new ExprMatcher(
            expr -> expr instanceof CastExpr,
            "anyCast()"
        );
    }

    /**
     * Matches instanceof expressions checking a specific type.
     */
    public static ExprMatcher instanceOf(String checkedType) {
        return new ExprMatcher(
            expr -> {
                if (!(expr instanceof InstanceOfExpr)) return false;
                InstanceOfExpr iof = (InstanceOfExpr) expr;
                return iof.getCheckType().toJavaSource().equals(checkedType) ||
                       iof.getCheckType().toJavaSource().equals(checkedType.replace('/', '.'));
            },
            "instanceOf(" + checkedType + ")"
        );
    }

    /**
     * Matches any instanceof expression.
     */
    public static ExprMatcher anyInstanceOf() {
        return new ExprMatcher(
            expr -> expr instanceof InstanceOfExpr,
            "anyInstanceOf()"
        );
    }

    /**
     * Matches any expression of a specific type.
     */
    public static ExprMatcher ofType(Class<? extends Expression> type) {
        return new ExprMatcher(
            expr -> type.isInstance(expr),
            "ofType(" + type.getSimpleName() + ")"
        );
    }

    /**
     * Matches all method call expressions.
     */
    public static ExprMatcher anyMethodCall() {
        return ofType(MethodCallExpr.class);
    }

    /**
     * Matches all field access expressions.
     */
    public static ExprMatcher anyFieldAccess() {
        return ofType(FieldAccessExpr.class);
    }

    /**
     * Matches all binary expressions.
     */
    public static ExprMatcher anyBinary() {
        return ofType(BinaryExpr.class);
    }

    /**
     * Matches all unary expressions.
     */
    public static ExprMatcher anyUnary() {
        return ofType(UnaryExpr.class);
    }

    /**
     * Matches all literal expressions.
     */
    public static ExprMatcher anyLiteral() {
        return ofType(LiteralExpr.class);
    }

    /**
     * Matches all array access expressions.
     */
    public static ExprMatcher anyArrayAccess() {
        return ofType(ArrayAccessExpr.class);
    }

    /**
     * Matches binary expressions with a specific operator.
     */
    public static ExprMatcher binaryOp(BinaryOperator op) {
        return new ExprMatcher(
            expr -> expr instanceof BinaryExpr &&
                    ((BinaryExpr) expr).getOperator() == op,
            "binaryOp(" + op.getSymbol() + ")"
        );
    }

    /**
     * Matches assignment expressions.
     */
    public static ExprMatcher assignment() {
        return new ExprMatcher(
            expr -> expr instanceof BinaryExpr &&
                    ((BinaryExpr) expr).isAssignment(),
            "assignment()"
        );
    }

    /**
     * Matches comparison expressions.
     */
    public static ExprMatcher comparison() {
        return new ExprMatcher(
            expr -> expr instanceof BinaryExpr &&
                    ((BinaryExpr) expr).isComparison(),
            "comparison()"
        );
    }

    /**
     * Matches unary expressions with a specific operator.
     */
    public static ExprMatcher unaryOp(UnaryOperator op) {
        return new ExprMatcher(
            expr -> expr instanceof UnaryExpr &&
                    ((UnaryExpr) expr).getOperator() == op,
            "unaryOp(" + op.getSymbol() + ")"
        );
    }

    /**
     * Creates a matcher from a custom predicate.
     */
    public static ExprMatcher custom(Predicate<Expression> predicate) {
        return new ExprMatcher(predicate, "custom");
    }

    /**
     * Creates a matcher from a custom predicate with description.
     */
    public static ExprMatcher custom(Predicate<Expression> predicate, String description) {
        return new ExprMatcher(predicate, description);
    }

    /**
     * Matches all expressions.
     */
    public static ExprMatcher any() {
        return new ExprMatcher(expr -> true, "any()");
    }

    /**
     * Matches no expressions.
     */
    public static ExprMatcher none() {
        return new ExprMatcher(expr -> false, "none()");
    }

    /**
     * Combines this matcher with another using AND logic.
     */
    public ExprMatcher and(ExprMatcher other) {
        return new ExprMatcher(
            expr -> this.matches(expr) && other.matches(expr),
            "(" + this.description + " && " + other.description + ")"
        );
    }

    /**
     * Combines this matcher with another using OR logic.
     */
    public ExprMatcher or(ExprMatcher other) {
        return new ExprMatcher(
            expr -> this.matches(expr) || other.matches(expr),
            "(" + this.description + " || " + other.description + ")"
        );
    }

    /**
     * Negates this matcher.
     */
    public ExprMatcher not() {
        return new ExprMatcher(
            expr -> !this.matches(expr),
            "!" + this.description
        );
    }

    @Override
    public String toString() {
        return "ExprMatcher[" + description + "]";
    }
}
