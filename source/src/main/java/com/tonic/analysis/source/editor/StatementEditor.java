package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.editor.handler.*;
import com.tonic.analysis.source.editor.matcher.StmtMatcher;

import java.util.List;

/**
 * Convenience class for statement-focused editing.
 * Wraps ASTEditor with a simplified API targeting only statements.
 *
 * <p>Example usage:
 * <pre>
 * StatementEditor editor = new StatementEditor(methodBody, "test", "()V", "com/example/Test");
 *
 * // Add logging before all return statements
 * editor.onReturn((ctx, ret) -> {
 *     Statement logStmt = ctx.factory()
 *         .methodCall("info")
 *         .on("java/util/logging/Logger")
 *         .withArgs(ctx.factory().stringLiteral("Returning from " + ctx.getMethodName()))
 *         .asStatement();
 *     return Replacement.insertBefore(logStmt);
 * });
 *
 * editor.apply();
 * </pre>
 */
public class StatementEditor {

    private final ASTEditor delegate;

    /**
     * Creates a statement editor for a method body.
     *
     * @param methodBody       the method body to edit
     * @param methodName       the name of the method
     * @param methodDescriptor the method descriptor
     * @param ownerClass       the internal name of the owning class
     */
    public StatementEditor(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass) {
        this.delegate = new ASTEditor(methodBody, methodName, methodDescriptor, ownerClass);
    }

    /**
     * Registers a handler for return statements.
     */
    public StatementEditor onReturn(ReturnHandler handler) {
        delegate.onReturn(handler);
        return this;
    }

    /**
     * Registers a handler for throw statements.
     */
    public StatementEditor onThrow(ThrowHandler handler) {
        delegate.onThrow(handler);
        return this;
    }

    /**
     * Registers a handler for if statements.
     */
    public StatementEditor onIf(IfHandler handler) {
        delegate.onIf(handler);
        return this;
    }

    /**
     * Registers a handler for loop statements (for, while, do-while, for-each).
     */
    public StatementEditor onLoop(LoopHandler handler) {
        delegate.onLoop(handler);
        return this;
    }

    /**
     * Registers a handler for try-catch statements.
     */
    public StatementEditor onTryCatch(TryCatchHandler handler) {
        delegate.onTryCatch(handler);
        return this;
    }

    /**
     * Registers a handler for assignment statements.
     */
    public StatementEditor onAssignment(AssignmentHandler handler) {
        delegate.onAssignment(handler);
        return this;
    }

    /**
     * Registers a handler for all statements matching the given matcher.
     */
    public StatementEditor onStmt(StmtMatcher matcher, StatementHandler handler) {
        delegate.onStmt(matcher, handler);
        return this;
    }

    /**
     * Registers a handler for all statements.
     */
    public StatementEditor onAnyStmt(StatementHandler handler) {
        delegate.onStmt(StmtMatcher.any(), handler);
        return this;
    }

    /**
     * Inserts statements before all returns.
     */
    public StatementEditor insertBeforeReturns(Statement... stmts) {
        return onReturn((ctx, ret) -> Replacement.insertBefore(stmts));
    }

    /**
     * Inserts statements before returns with values (non-void).
     */
    public StatementEditor insertBeforeValueReturns(Statement... stmts) {
        return onReturn((ctx, ret) -> {
            if (!ret.isVoidReturn()) {
                return Replacement.insertBefore(stmts);
            }
            return Replacement.keep();
        });
    }

    /**
     * Inserts statements before void returns.
     */
    public StatementEditor insertBeforeVoidReturns(Statement... stmts) {
        return onReturn((ctx, ret) -> {
            if (ret.isVoidReturn()) {
                return Replacement.insertBefore(stmts);
            }
            return Replacement.keep();
        });
    }

    /**
     * Wraps all loops with statements before and after.
     */
    public StatementEditor wrapLoops(Statement before, Statement after) {
        return onLoop((ctx, loop) -> Replacement.withBlock(before, loop, after));
    }

    /**
     * Adds statements before all throw statements.
     */
    public StatementEditor insertBeforeThrows(Statement... stmts) {
        return onThrow((ctx, throwStmt) -> Replacement.insertBefore(stmts));
    }

    /**
     * Removes all statements of a specific type.
     */
    public StatementEditor removeStmts(Class<? extends Statement> type) {
        return onStmt(StmtMatcher.ofType(type), (ctx, stmt) -> Replacement.remove());
    }

    /**
     * Removes all statements matching the given matcher.
     */
    public StatementEditor removeStmts(StmtMatcher matcher) {
        return onStmt(matcher, (ctx, stmt) -> Replacement.remove());
    }

    /**
     * Finds all statements matching the given matcher.
     */
    public List<Statement> findStatements(StmtMatcher matcher) {
        return delegate.findStatements(matcher);
    }

    /**
     * Finds all return statements.
     */
    public List<Statement> findReturns() {
        return delegate.findStatements(StmtMatcher.returnStmt());
    }

    /**
     * Finds all return statements with values.
     */
    public List<Statement> findValueReturns() {
        return delegate.findStatements(StmtMatcher.returnWithValue());
    }

    /**
     * Finds all void return statements.
     */
    public List<Statement> findVoidReturns() {
        return delegate.findStatements(StmtMatcher.voidReturn());
    }

    /**
     * Finds all throw statements.
     */
    public List<Statement> findThrows() {
        return delegate.findStatements(StmtMatcher.throwStmt());
    }

    /**
     * Finds all loop statements.
     */
    public List<Statement> findLoops() {
        return delegate.findStatements(StmtMatcher.anyLoop());
    }

    /**
     * Finds all if statements.
     */
    public List<Statement> findIfs() {
        return delegate.findStatements(StmtMatcher.ifStmt());
    }

    /**
     * Finds all if-else statements (if with else branch).
     */
    public List<Statement> findIfElses() {
        return delegate.findStatements(StmtMatcher.ifElseStmt());
    }

    /**
     * Finds all try-catch statements.
     */
    public List<Statement> findTryCatches() {
        return delegate.findStatements(StmtMatcher.tryCatchStmt());
    }

    /**
     * Finds all switch statements.
     */
    public List<Statement> findSwitches() {
        return delegate.findStatements(StmtMatcher.switchStmt());
    }

    /**
     * Finds all variable declaration statements.
     */
    public List<Statement> findVarDecls() {
        return delegate.findStatements(StmtMatcher.varDeclStmt());
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
}
