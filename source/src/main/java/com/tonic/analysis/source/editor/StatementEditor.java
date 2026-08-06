package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.editor.handler.*;
import com.tonic.analysis.source.editor.matcher.StmtMatcher;

import java.util.List;

/**
 * Statement-only facade over {@link ASTEditor}.
 */
public class StatementEditor
{

    private final ASTEditor delegate;

    /**
     * Creates a statement editor for a method body.
     * @param methodBody       the method body to edit
     * @param methodName       the name of the method
     * @param methodDescriptor the method descriptor
     * @param ownerClass       the internal name of the owning class
     */
    public StatementEditor(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass)
    {
        this.delegate = new ASTEditor(methodBody, methodName, methodDescriptor, ownerClass);
    }

    /**
     * Registers a handler for return statements.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onReturn(ReturnHandler handler)
    {
        delegate.onReturn(handler);
        return this;
    }

    /**
     * Registers a handler for throw statements.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onThrow(ThrowHandler handler)
    {
        delegate.onThrow(handler);
        return this;
    }

    /**
     * Registers a handler for if statements.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onIf(IfHandler handler)
    {
        delegate.onIf(handler);
        return this;
    }

    /**
     * Registers a handler for for, while, do-while and for-each statements.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onLoop(LoopHandler handler)
    {
        delegate.onLoop(handler);
        return this;
    }

    /**
     * Registers a handler for try-catch statements.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onTryCatch(TryCatchHandler handler)
    {
        delegate.onTryCatch(handler);
        return this;
    }

    /**
     * Registers a handler for assignment statements.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onAssignment(AssignmentHandler handler)
    {
        delegate.onAssignment(handler);
        return this;
    }

    /**
     * Registers a handler for the statements a matcher accepts.
     *
     * @param matcher the predicate statements are tested against
     * @param handler the handler to invoke on a match
     * @return this editor
     */
    public StatementEditor onStmt(StmtMatcher matcher, StatementHandler handler)
    {
        delegate.onStmt(matcher, handler);
        return this;
    }

    /**
     * Registers a handler that sees every statement.
     *
     * @param handler the handler to invoke
     * @return this editor
     */
    public StatementEditor onAnyStmt(StatementHandler handler)
    {
        delegate.onStmt(StmtMatcher.any(), handler);
        return this;
    }

    /**
     * Registers a handler that inserts statements ahead of every return.
     *
     * @param stmts the statements to insert, in order
     * @return this editor
     */
    public StatementEditor insertBeforeReturns(Statement... stmts)
    {
        return onReturn((ctx, ret) -> Replacement.insertBefore(stmts));
    }

    /**
     * Registers a handler that inserts statements ahead of every value-carrying return.
     *
     * @param stmts the statements to insert, in order
     * @return this editor
     */
    public StatementEditor insertBeforeValueReturns(Statement... stmts)
    {
        return onReturn((ctx, ret) -> {
            if (!ret.isVoidReturn())
            {
                return Replacement.insertBefore(stmts);
            }
            return Replacement.keep();
        });
    }

    /**
     * Registers a handler that inserts statements ahead of every valueless return.
     *
     * @param stmts the statements to insert, in order
     * @return this editor
     */
    public StatementEditor insertBeforeVoidReturns(Statement... stmts)
    {
        return onReturn((ctx, ret) -> {
            if (ret.isVoidReturn())
            {
                return Replacement.insertBefore(stmts);
            }
            return Replacement.keep();
        });
    }

    /**
     * Registers a handler that replaces every loop with a block bracketing it.
     *
     * @param before the statement placed ahead of the loop
     * @param after the statement placed after the loop
     * @return this editor
     */
    public StatementEditor wrapLoops(Statement before, Statement after)
    {
        return onLoop((ctx, loop) -> Replacement.withBlock(before, loop, after));
    }

    /**
     * Registers a handler that inserts statements ahead of every throw.
     *
     * @param stmts the statements to insert, in order
     * @return this editor
     */
    public StatementEditor insertBeforeThrows(Statement... stmts)
    {
        return onThrow((ctx, throwStmt) -> Replacement.insertBefore(stmts));
    }

    /**
     * Registers a handler that drops every statement of a given node type.
     *
     * @param type the statement class to remove
     * @return this editor
     */
    public StatementEditor removeStmts(Class<? extends Statement> type)
    {
        return onStmt(StmtMatcher.ofType(type), (ctx, stmt) -> Replacement.remove());
    }

    /**
     * Registers a handler that drops every statement the matcher accepts.
     *
     * @param matcher the predicate statements are tested against
     * @return this editor
     */
    public StatementEditor removeStmts(StmtMatcher matcher)
    {
        return onStmt(matcher, (ctx, stmt) -> Replacement.remove());
    }

    /**
     * Finds all statements matching the given matcher.
     *
     * @param matcher the predicate statements are tested against
     * @return the matching statements in traversal order
     */
    public List<Statement> findStatements(StmtMatcher matcher)
    {
        return delegate.findStatements(matcher);
    }

    /**
     * Finds all return statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findReturns()
    {
        return delegate.findStatements(StmtMatcher.returnStmt());
    }

    /**
     * Finds all returns that carry a value.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findValueReturns()
    {
        return delegate.findStatements(StmtMatcher.returnWithValue());
    }

    /**
     * Finds all returns that carry no value.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findVoidReturns()
    {
        return delegate.findStatements(StmtMatcher.voidReturn());
    }

    /**
     * Finds all throw statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findThrows()
    {
        return delegate.findStatements(StmtMatcher.throwStmt());
    }

    /**
     * Finds all for, while, do-while and for-each statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findLoops()
    {
        return delegate.findStatements(StmtMatcher.anyLoop());
    }

    /**
     * Finds all if statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findIfs()
    {
        return delegate.findStatements(StmtMatcher.ifStmt());
    }

    /**
     * Finds all if statements that have an else branch.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findIfElses()
    {
        return delegate.findStatements(StmtMatcher.ifElseStmt());
    }

    /**
     * Finds all try-catch statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findTryCatches()
    {
        return delegate.findStatements(StmtMatcher.tryCatchStmt());
    }

    /**
     * Finds all switch statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findSwitches()
    {
        return delegate.findStatements(StmtMatcher.switchStmt());
    }

    /**
     * Finds all variable declaration statements.
     *
     * @return the matching statements in traversal order
     */
    public List<Statement> findVarDecls()
    {
        return delegate.findStatements(StmtMatcher.varDeclStmt());
    }

    /**
     * Applies all registered handlers and modifies the AST in place.
     */
    public void apply()
    {
        delegate.apply();
    }

    /**
     * @return the wrapped editor, for operations this facade does not expose
     */
    public ASTEditor getDelegate()
    {
        return delegate;
    }
}
