package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.editor.handler.*;
import com.tonic.analysis.source.editor.matcher.ExprMatcher;

import java.util.List;

/**
 * Convenience class for expression-focused editing.
 * Wraps ASTEditor with a simplified API targeting only expressions.
 *
 * <p>Example usage:
 * <pre>
 * ExpressionEditor editor = new ExpressionEditor(methodBody, "test", "()V", "com/example/Test");
 *
 * // Replace all deprecated method calls
 * editor.onMethodCall((ctx, call) -> {
 *     if (call.getMethodName().equals("deprecatedMethod")) {
 *         return Replacement.with(ctx.factory()
 *             .methodCall("newMethod")
 *             .on(call.getReceiver())
 *             .withArgs(call.getArguments())
 *             .build());
 *     }
 *     return Replacement.keep();
 * });
 *
 * editor.apply();
 * </pre>
 */
public class ExpressionEditor {

    private final ASTEditor delegate;

    /**
     * Creates an expression editor for a method body.
     *
     * @param methodBody       the method body to edit
     * @param methodName       the name of the method
     * @param methodDescriptor the method descriptor
     * @param ownerClass       the internal name of the owning class
     */
    public ExpressionEditor(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass) {
        this.delegate = new ASTEditor(methodBody, methodName, methodDescriptor, ownerClass);
    }

    /**
     * Registers a handler for method call expressions.
     */
    public ExpressionEditor onMethodCall(MethodCallHandler handler) {
        delegate.onMethodCall(handler);
        return this;
    }

    /**
     * Registers a handler for field access expressions.
     */
    public ExpressionEditor onFieldAccess(FieldAccessHandler handler) {
        delegate.onFieldAccess(handler);
        return this;
    }

    /**
     * Registers a handler for new object expressions.
     */
    public ExpressionEditor onNewExpr(NewExprHandler handler) {
        delegate.onNewExpr(handler);
        return this;
    }

    /**
     * Registers a handler for new array expressions.
     */
    public ExpressionEditor onNewArray(NewArrayHandler handler) {
        delegate.onNewArray(handler);
        return this;
    }

    /**
     * Registers a handler for cast expressions.
     */
    public ExpressionEditor onCast(CastHandler handler) {
        delegate.onCast(handler);
        return this;
    }

    /**
     * Registers a handler for instanceof expressions.
     */
    public ExpressionEditor onInstanceOf(InstanceOfHandler handler) {
        delegate.onInstanceOf(handler);
        return this;
    }

    /**
     * Registers a handler for binary expressions.
     */
    public ExpressionEditor onBinaryExpr(BinaryExprHandler handler) {
        delegate.onBinaryExpr(handler);
        return this;
    }

    /**
     * Registers a handler for unary expressions.
     */
    public ExpressionEditor onUnaryExpr(UnaryExprHandler handler) {
        delegate.onUnaryExpr(handler);
        return this;
    }

    /**
     * Registers a handler for array access expressions.
     * The handler receives context about whether the access is a read or store operation.
     */
    public ExpressionEditor onArrayAccess(ArrayAccessHandler handler) {
        delegate.onArrayAccess(handler);
        return this;
    }

    /**
     * Registers a handler for array read operations only.
     */
    public ExpressionEditor onArrayRead(ArrayAccessHandler handler) {
        delegate.onArrayRead(handler);
        return this;
    }

    /**
     * Registers a handler for array store operations only.
     */
    public ExpressionEditor onArrayStore(ArrayAccessHandler handler) {
        delegate.onArrayStore(handler);
        return this;
    }

    /**
     * Registers a handler for all expressions matching the given matcher.
     */
    public ExpressionEditor onExpr(ExprMatcher matcher, ExpressionHandler handler) {
        delegate.onExpr(matcher, handler);
        return this;
    }

    /**
     * Registers a handler for all expressions.
     */
    public ExpressionEditor onAnyExpr(ExpressionHandler handler) {
        delegate.onExpr(ExprMatcher.any(), handler);
        return this;
    }

    /**
     * Replaces all method calls matching the criteria.
     * @param ownerClass the owner class to match
     * @param methodName the method name to match
     * @param replacer function to create replacement expression
     * @return this editor for chaining
     */
    public ExpressionEditor replaceMethodCall(String ownerClass, String methodName,
                                               MethodCallReplacer replacer) {
        String normalizedOwner = ownerClass.replace('.', '/');
        return onMethodCall((ctx, call) -> {
            if (call.getMethodName().equals(methodName) &&
                call.getOwnerClass().equals(normalizedOwner)) {
                Expression replacement = replacer.replace(ctx, call);
                if (replacement != null) {
                    return Replacement.with(replacement);
                }
            }
            return Replacement.keep();
        });
    }

    /**
     * Removes all method calls matching the criteria.
     * @param ownerClass the owner class to match
     * @param methodName the method name to match
     * @return this editor for chaining
     */
    public ExpressionEditor removeMethodCall(String ownerClass, String methodName) {
        String normalizedOwner = ownerClass.replace('.', '/');
        return onMethodCall((ctx, call) -> {
            if (call.getMethodName().equals(methodName) &&
                call.getOwnerClass().equals(normalizedOwner)) {
                return Replacement.remove();
            }
            return Replacement.keep();
        });
    }

    /**
     * Replaces all field accesses matching the criteria.
     * @param ownerClass the owner class to match
     * @param fieldName the field name to match
     * @param replacer function to create replacement expression
     * @return this editor for chaining
     */
    public ExpressionEditor replaceFieldAccess(String ownerClass, String fieldName,
                                                FieldAccessReplacer replacer) {
        String normalizedOwner = ownerClass.replace('.', '/');
        return onFieldAccess((ctx, access) -> {
            if (access.getFieldName().equals(fieldName) &&
                access.getOwnerClass().equals(normalizedOwner)) {
                Expression replacement = replacer.replace(ctx, access);
                if (replacement != null) {
                    return Replacement.with(replacement);
                }
            }
            return Replacement.keep();
        });
    }

    /**
     * Replaces all new expressions of a specific class.
     * @param className the class name to match
     * @param replacer function to create replacement expression
     * @return this editor for chaining
     */
    public ExpressionEditor replaceNewExpr(String className, NewExprReplacer replacer) {
        String normalizedClass = className.replace('.', '/');
        return onNewExpr((ctx, newExpr) -> {
            if (newExpr.getClassName().equals(normalizedClass)) {
                Expression replacement = replacer.replace(ctx, newExpr);
                if (replacement != null) {
                    return Replacement.with(replacement);
                }
            }
            return Replacement.keep();
        });
    }

    /**
     * Finds all expressions matching the given matcher.
     */
    public List<Expression> findExpressions(ExprMatcher matcher) {
        return delegate.findExpressions(matcher);
    }

    /**
     * Finds all method call expressions.
     */
    public List<Expression> findMethodCalls() {
        return delegate.findExpressions(ExprMatcher.anyMethodCall());
    }

    /**
     * Finds all method calls to a specific method.
     */
    public List<Expression> findMethodCalls(String methodName) {
        return delegate.findExpressions(ExprMatcher.methodCall(methodName));
    }

    /**
     * Finds all method calls to a specific owner and method.
     */
    public List<Expression> findMethodCalls(String ownerClass, String methodName) {
        return delegate.findExpressions(ExprMatcher.methodCall(ownerClass, methodName));
    }

    /**
     * Finds all field access expressions.
     */
    public List<Expression> findFieldAccesses() {
        return delegate.findExpressions(ExprMatcher.anyFieldAccess());
    }

    /**
     * Finds all new expressions for a specific class.
     */
    public List<Expression> findNewExpressions(String className) {
        return delegate.findExpressions(ExprMatcher.newExpr(className));
    }

    /**
     * Finds all array access expressions.
     */
    public List<Expression> findArrayAccesses() {
        return delegate.findExpressions(ExprMatcher.anyArrayAccess());
    }

    /**
     * Applies all registered handlers and modifies the AST in place.
     */
    public void apply() {
        delegate.apply();
    }

    /**
     * Gets the underlying ASTEditor for advanced operations.
     */
    public ASTEditor getDelegate() {
        return delegate;
    }

    /**
     * Functional interface for method call replacement.
     */
    @FunctionalInterface
    public interface MethodCallReplacer {
        Expression replace(EditorContext ctx, MethodCallExpr call);
    }

    /**
     * Functional interface for field access replacement.
     */
    @FunctionalInterface
    public interface FieldAccessReplacer {
        Expression replace(EditorContext ctx, FieldAccessExpr access);
    }

    /**
     * Functional interface for new expression replacement.
     */
    @FunctionalInterface
    public interface NewExprReplacer {
        Expression replace(EditorContext ctx, NewExpr newExpr);
    }
}
