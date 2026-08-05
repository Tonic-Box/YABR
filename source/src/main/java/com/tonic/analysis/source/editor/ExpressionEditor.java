package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.editor.handler.*;
import com.tonic.analysis.source.editor.matcher.ExprMatcher;

import java.util.List;

/**
 * Expression-only view over ASTEditor.
 *
 * <pre>
 * ExpressionEditor editor = new ExpressionEditor(methodBody, "test", "()V", "com/example/Test");
 * // Replace all deprecated method calls
 * editor.onMethodCall((ctx, call) -&gt; {
 *     if (call.getMethodName().equals("deprecatedMethod")) {
 *         return Replacement.with(ctx.factory()
 *             .methodCall("newMethod")
 *             .on(call.getReceiver())
 *             .withArgs(call.getArguments())
 *             .build());
 *     }
 *     return Replacement.keep();
 * });
 * editor.apply();
 * </pre>
 */
public class ExpressionEditor
{

    private final ASTEditor delegate;

    /**
     * Creates an expression editor for a method body.
     * @param methodBody       the method body to edit
     * @param methodName       the name of the method
     * @param methodDescriptor the method descriptor
     * @param ownerClass       the internal name of the owning class
     */
    public ExpressionEditor(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass)
    {
        this.delegate = new ASTEditor(methodBody, methodName, methodDescriptor, ownerClass);
    }

    /**
     * Registers a handler for method call expressions.
     *
     * @param handler the handler to run on each method call
     * @return this editor
     */
    public ExpressionEditor onMethodCall(MethodCallHandler handler)
    {
        delegate.onMethodCall(handler);
        return this;
    }

    /**
     * Registers a handler for field access expressions.
     *
     * @param handler the handler to run on each field access
     * @return this editor
     */
    public ExpressionEditor onFieldAccess(FieldAccessHandler handler)
    {
        delegate.onFieldAccess(handler);
        return this;
    }

    /**
     * Registers a handler for object allocation expressions.
     *
     * @param handler the handler to run on each new expression
     * @return this editor
     */
    public ExpressionEditor onNewExpr(NewExprHandler handler)
    {
        delegate.onNewExpr(handler);
        return this;
    }

    /**
     * Registers a handler for array allocation expressions.
     *
     * @param handler the handler to run on each new array expression
     * @return this editor
     */
    public ExpressionEditor onNewArray(NewArrayHandler handler)
    {
        delegate.onNewArray(handler);
        return this;
    }

    /**
     * Registers a handler for cast expressions.
     *
     * @param handler the handler to run on each cast
     * @return this editor
     */
    public ExpressionEditor onCast(CastHandler handler)
    {
        delegate.onCast(handler);
        return this;
    }

    /**
     * Registers a handler for instanceof expressions.
     *
     * @param handler the handler to run on each instanceof test
     * @return this editor
     */
    public ExpressionEditor onInstanceOf(InstanceOfHandler handler)
    {
        delegate.onInstanceOf(handler);
        return this;
    }

    /**
     * Registers a handler for binary expressions.
     *
     * @param handler the handler to run on each binary expression
     * @return this editor
     */
    public ExpressionEditor onBinaryExpr(BinaryExprHandler handler)
    {
        delegate.onBinaryExpr(handler);
        return this;
    }

    /**
     * Registers a handler for unary expressions.
     *
     * @param handler the handler to run on each unary expression
     * @return this editor
     */
    public ExpressionEditor onUnaryExpr(UnaryExprHandler handler)
    {
        delegate.onUnaryExpr(handler);
        return this;
    }

    /**
     * Registers a handler for array accesses, reads and stores alike.
     *
     * @param handler the handler to run on each access; its context says whether it is a read or a store
     * @return this editor
     */
    public ExpressionEditor onArrayAccess(ArrayAccessHandler handler)
    {
        delegate.onArrayAccess(handler);
        return this;
    }

    /**
     * Registers a handler for array reads only.
     *
     * @param handler the handler to run on each array read
     * @return this editor
     */
    public ExpressionEditor onArrayRead(ArrayAccessHandler handler)
    {
        delegate.onArrayRead(handler);
        return this;
    }

    /**
     * Registers a handler for array stores only.
     *
     * @param handler the handler to run on each array store
     * @return this editor
     */
    public ExpressionEditor onArrayStore(ArrayAccessHandler handler)
    {
        delegate.onArrayStore(handler);
        return this;
    }

    /**
     * Registers a handler for the expressions a matcher selects.
     *
     * @param matcher selects which expressions reach the handler
     * @param handler the handler to run on each match
     * @return this editor
     */
    public ExpressionEditor onExpr(ExprMatcher matcher, ExpressionHandler handler)
    {
        delegate.onExpr(matcher, handler);
        return this;
    }

    /**
     * Registers a handler for every expression in the body.
     *
     * @param handler the handler to run on each expression
     * @return this editor
     */
    public ExpressionEditor onAnyExpr(ExpressionHandler handler)
    {
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
    public ExpressionEditor replaceMethodCall(String ownerClass, String methodName, MethodCallReplacer replacer)
    {
        String normalizedOwner = ownerClass.replace('.', '/');
        return onMethodCall((ctx, call) -> {
            if (call.getMethodName().equals(methodName) && call.getOwnerClass().equals(normalizedOwner))
            {
                Expression replacement = replacer.replace(ctx, call);
                if (replacement != null)
                {
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
    public ExpressionEditor removeMethodCall(String ownerClass, String methodName)
    {
        String normalizedOwner = ownerClass.replace('.', '/');
        return onMethodCall((ctx, call) -> {
            if (call.getMethodName().equals(methodName) && call.getOwnerClass().equals(normalizedOwner))
            {
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
    public ExpressionEditor replaceFieldAccess(String ownerClass, String fieldName, FieldAccessReplacer replacer)
    {
        String normalizedOwner = ownerClass.replace('.', '/');
        return onFieldAccess((ctx, access) -> {
            if (access.getFieldName().equals(fieldName) && access.getOwnerClass().equals(normalizedOwner))
            {
                Expression replacement = replacer.replace(ctx, access);
                if (replacement != null)
                {
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
    public ExpressionEditor replaceNewExpr(String className, NewExprReplacer replacer)
    {
        String normalizedClass = className.replace('.', '/');
        return onNewExpr((ctx, newExpr) -> {
            if (newExpr.getClassName().equals(normalizedClass))
            {
                Expression replacement = replacer.replace(ctx, newExpr);
                if (replacement != null)
                {
                    return Replacement.with(replacement);
                }
            }
            return Replacement.keep();
        });
    }

    /**
     * Collects the expressions a matcher selects.
     *
     * @param matcher selects which expressions to collect
     * @return the matching expressions
     */
    public List<Expression> findExpressions(ExprMatcher matcher)
    {
        return delegate.findExpressions(matcher);
    }

    /**
     * @return every method call expression in the body
     */
    public List<Expression> findMethodCalls()
    {
        return delegate.findExpressions(ExprMatcher.anyMethodCall());
    }

    /**
     * Collects the calls to one method name, whatever the owner.
     *
     * @param methodName the method name to match
     * @return the matching call expressions
     */
    public List<Expression> findMethodCalls(String methodName)
    {
        return delegate.findExpressions(ExprMatcher.methodCall(methodName));
    }

    /**
     * Collects the calls to one method on one owner.
     *
     * @param ownerClass the owning class to match
     * @param methodName the method name to match
     * @return the matching call expressions
     */
    public List<Expression> findMethodCalls(String ownerClass, String methodName)
    {
        return delegate.findExpressions(ExprMatcher.methodCall(ownerClass, methodName));
    }

    /**
     * @return every field access expression in the body
     */
    public List<Expression> findFieldAccesses()
    {
        return delegate.findExpressions(ExprMatcher.anyFieldAccess());
    }

    /**
     * Collects the allocations of one class.
     *
     * @param className the allocated class to match
     * @return the matching new expressions
     */
    public List<Expression> findNewExpressions(String className)
    {
        return delegate.findExpressions(ExprMatcher.newExpr(className));
    }

    /**
     * @return every array access expression in the body
     */
    public List<Expression> findArrayAccesses()
    {
        return delegate.findExpressions(ExprMatcher.anyArrayAccess());
    }

    /**
     * Applies all registered handlers and modifies the AST in place.
     */
    public void apply()
    {
        delegate.apply();
    }

    /**
     * @return the underlying editor, for operations this wrapper does not expose
     */
    public ASTEditor getDelegate()
    {
        return delegate;
    }

    /**
     * Functional interface for method call replacement.
     */
    @FunctionalInterface
    public interface MethodCallReplacer
    {
        /**
         * Builds the expression that takes the call's place.
         *
         * @param ctx the surrounding edit context
         * @param call the matched call
         * @return the replacement expression, or null to leave the call alone
         */
        Expression replace(EditorContext ctx, MethodCallExpr call);
    }

    /**
     * Functional interface for field access replacement.
     */
    @FunctionalInterface
    public interface FieldAccessReplacer
    {
        /**
         * Builds the expression that takes the field access's place.
         *
         * @param ctx the surrounding edit context
         * @param access the matched field access
         * @return the replacement expression, or null to leave the access alone
         */
        Expression replace(EditorContext ctx, FieldAccessExpr access);
    }

    /**
     * Functional interface for new expression replacement.
     */
    @FunctionalInterface
    public interface NewExprReplacer
    {
        /**
         * Builds the expression that takes the allocation's place.
         *
         * @param ctx the surrounding edit context
         * @param newExpr the matched new expression
         * @return the replacement expression, or null to leave the allocation alone
         */
        Expression replace(EditorContext ctx, NewExpr newExpr);
    }
}
