package com.tonic.analysis.source.editor.matcher;

import com.tonic.analysis.source.ast.expr.*;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Composable predicate over expressions, used to select edit targets in the AST.
 */
public class ExprMatcher
{

    private final Predicate<Expression> predicate;
    private final String description;

    private ExprMatcher(Predicate<Expression> predicate, String description)
    {
        this.predicate = Objects.requireNonNull(predicate, "predicate cannot be null");
        this.description = description != null ? description : "custom matcher";
    }

    /**
     * Applies this matcher's predicate.
     *
     * @param expr the expression to test, may be null
     * @return true if the predicate accepts it; false if it is null
     */
    public boolean matches(Expression expr)
    {
        return expr != null && predicate.test(expr);
    }

    /**
     * Matches method calls by method name, whatever the owner.
     *
     * @param methodName the method name to require
     * @return the matcher
     */
    public static ExprMatcher methodCall(String methodName)
    {
        return new ExprMatcher(
            expr -> expr instanceof MethodCallExpr &&
                    ((MethodCallExpr) expr).getMethodName().equals(methodName),
            "methodCall(" + methodName + ")"
        );
    }

    /**
     * Matches method calls by owner class and method name.
     *
     * @param ownerClass the declaring class, in either slashed or dotted form
     * @param methodName the method name to require
     * @return the matcher
     */
    public static ExprMatcher methodCall(String ownerClass, String methodName)
    {
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
     * Matches method calls by owner class, method name and argument count.
     *
     * @param ownerClass the declaring class, in either slashed or dotted form
     * @param methodName the method name to require
     * @param argCount the exact number of arguments to require
     * @return the matcher
     */
    public static ExprMatcher methodCall(String ownerClass, String methodName, int argCount)
    {
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
     * Matches field accesses by field name, whatever the owner.
     *
     * @param fieldName the field name to require
     * @return the matcher
     */
    public static ExprMatcher fieldAccess(String fieldName)
    {
        return new ExprMatcher(
            expr -> expr instanceof FieldAccessExpr &&
                    ((FieldAccessExpr) expr).getFieldName().equals(fieldName),
            "fieldAccess(" + fieldName + ")"
        );
    }

    /**
     * Matches field accesses by owner class and field name.
     *
     * @param ownerClass the declaring class, in either slashed or dotted form
     * @param fieldName the field name to require
     * @return the matcher
     */
    public static ExprMatcher fieldAccess(String ownerClass, String fieldName)
    {
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
     * Matches allocations of a specific class.
     *
     * @param className the class name, in either slashed or dotted form
     * @return the matcher
     */
    public static ExprMatcher newExpr(String className)
    {
        String normalizedClass = className.replace('.', '/');
        return new ExprMatcher(
            expr -> expr instanceof NewExpr &&
                    ((NewExpr) expr).getClassName().equals(normalizedClass),
            "newExpr(" + className + ")"
        );
    }

    /**
     * Matches new array expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher newArray()
    {
        return new ExprMatcher(expr -> expr instanceof NewArrayExpr, "newArray()");
    }

    /**
     * Matches cast expressions to a specific type.
     *
     * @param targetType the cast target, in either slashed or dotted form
     * @return the matcher
     */
    public static ExprMatcher cast(String targetType)
    {
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
     *
     * @return the matcher
     */
    public static ExprMatcher anyCast()
    {
        return new ExprMatcher(expr -> expr instanceof CastExpr, "anyCast()");
    }

    /**
     * Matches instanceof expressions checking a specific type.
     *
     * @param checkedType the type name, in either slashed or dotted form
     * @return the matcher
     */
    public static ExprMatcher instanceOf(String checkedType)
    {
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
     *
     * @return the matcher
     */
    public static ExprMatcher anyInstanceOf()
    {
        return new ExprMatcher(expr -> expr instanceof InstanceOfExpr, "anyInstanceOf()");
    }

    /**
     * Matches any expression of a specific node type.
     *
     * @param type the expression class to require
     * @return the matcher
     */
    public static ExprMatcher ofType(Class<? extends Expression> type)
    {
        return new ExprMatcher(type::isInstance, "ofType(" + type.getSimpleName() + ")");
    }

    /**
     * Matches all method call expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher anyMethodCall()
    {
        return ofType(MethodCallExpr.class);
    }

    /**
     * Matches all field access expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher anyFieldAccess()
    {
        return ofType(FieldAccessExpr.class);
    }

    /**
     * Matches all binary expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher anyBinary()
    {
        return ofType(BinaryExpr.class);
    }

    /**
     * Matches all unary expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher anyUnary()
    {
        return ofType(UnaryExpr.class);
    }

    /**
     * Matches all literal expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher anyLiteral()
    {
        return ofType(LiteralExpr.class);
    }

    /**
     * Matches all array access expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher anyArrayAccess()
    {
        return ofType(ArrayAccessExpr.class);
    }

    /**
     * Matches binary expressions with a specific operator.
     *
     * @param op the operator to require
     * @return the matcher
     */
    public static ExprMatcher binaryOp(BinaryOperator op)
    {
        return new ExprMatcher(
            expr -> expr instanceof BinaryExpr &&
                    ((BinaryExpr) expr).getOperator() == op,
            "binaryOp(" + op.getSymbol() + ")"
        );
    }

    /**
     * Matches binary expressions whose operator assigns.
     *
     * @return the matcher
     */
    public static ExprMatcher assignment()
    {
        return new ExprMatcher(
            expr -> expr instanceof BinaryExpr &&
                    ((BinaryExpr) expr).isAssignment(),
            "assignment()"
        );
    }

    /**
     * Matches binary expressions whose operator is a comparison.
     *
     * @return the matcher
     */
    public static ExprMatcher comparison()
    {
        return new ExprMatcher(
            expr -> expr instanceof BinaryExpr &&
                    ((BinaryExpr) expr).isComparison(),
            "comparison()"
        );
    }

    /**
     * Matches unary expressions with a specific operator.
     *
     * @param op the operator to require
     * @return the matcher
     */
    public static ExprMatcher unaryOp(UnaryOperator op)
    {
        return new ExprMatcher(
            expr -> expr instanceof UnaryExpr &&
                    ((UnaryExpr) expr).getOperator() == op,
            "unaryOp(" + op.getSymbol() + ")"
        );
    }

    /**
     * Creates a matcher from a custom predicate.
     *
     * @param predicate the test applied to each expression
     * @return the matcher
     */
    public static ExprMatcher custom(Predicate<Expression> predicate)
    {
        return new ExprMatcher(predicate, "custom");
    }

    /**
     * Creates a matcher from a custom predicate with a description.
     *
     * @param predicate the test applied to each expression
     * @param description the text used by {@link #toString()}
     * @return the matcher
     */
    public static ExprMatcher custom(Predicate<Expression> predicate, String description)
    {
        return new ExprMatcher(predicate, description);
    }

    /**
     * Matches all expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher any()
    {
        return new ExprMatcher(expr -> true, "any()");
    }

    /**
     * Matches no expressions.
     *
     * @return the matcher
     */
    public static ExprMatcher none()
    {
        return new ExprMatcher(expr -> false, "none()");
    }

    /**
     * Combines this matcher with another using AND logic.
     *
     * @param other the matcher to combine with
     * @return a matcher accepting only what both accept
     */
    public ExprMatcher and(ExprMatcher other)
    {
        return new ExprMatcher(
            expr -> this.matches(expr) && other.matches(expr),
            "(" + this.description + " && " + other.description + ")"
        );
    }

    /**
     * Combines this matcher with another using OR logic.
     *
     * @param other the matcher to combine with
     * @return a matcher accepting what either one accepts
     */
    public ExprMatcher or(ExprMatcher other)
    {
        return new ExprMatcher(
            expr -> this.matches(expr) || other.matches(expr),
            "(" + this.description + " || " + other.description + ")"
        );
    }

    /**
     * Negates this matcher.
     *
     * @return a matcher accepting everything this one rejects
     */
    public ExprMatcher not()
    {
        return new ExprMatcher(expr -> !this.matches(expr), "!" + this.description);
    }

    @Override
    public String toString()
    {
        return "ExprMatcher[" + description + "]";
    }
}
