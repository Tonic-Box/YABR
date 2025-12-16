package com.tonic.analysis.source.editor;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.editor.handler.*;
import com.tonic.analysis.source.editor.matcher.ExprMatcher;
import com.tonic.analysis.source.editor.matcher.StmtMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Main API for editing AST nodes.
 * Provides a fluent interface for registering handlers and applying transformations.
 *
 * <p>Example usage:</p>
 * <pre>
 * ASTEditor editor = new ASTEditor(methodBody, "methodName", "()V", "com/example/Class");
 * editor.onMethodCall((ctx, call) -> {
 *     if (call.getMethodName().equals("oldMethod")) {
 *         return Replacement.with(ctx.factory().methodCall("newMethod").build());
 *     }
 *     return Replacement.keep();
 * });
 * editor.apply();
 * </pre>
 */
public class ASTEditor {

    private final BlockStmt methodBody;
    private final EditorContext context;

    private final List<HandlerEntry<MethodCallHandler, MethodCallExpr>> methodCallHandlers = new ArrayList<>();
    private final List<HandlerEntry<FieldAccessHandler, FieldAccessExpr>> fieldAccessHandlers = new ArrayList<>();
    private final List<HandlerEntry<NewExprHandler, NewExpr>> newExprHandlers = new ArrayList<>();
    private final List<HandlerEntry<NewArrayHandler, NewArrayExpr>> newArrayHandlers = new ArrayList<>();
    private final List<HandlerEntry<CastHandler, CastExpr>> castHandlers = new ArrayList<>();
    private final List<HandlerEntry<InstanceOfHandler, InstanceOfExpr>> instanceOfHandlers = new ArrayList<>();
    private final List<HandlerEntry<BinaryExprHandler, BinaryExpr>> binaryExprHandlers = new ArrayList<>();
    private final List<HandlerEntry<UnaryExprHandler, UnaryExpr>> unaryExprHandlers = new ArrayList<>();
    private final List<ArrayAccessHandlerEntry> arrayAccessHandlers = new ArrayList<>();

    private final List<HandlerEntry<ReturnHandler, ReturnStmt>> returnHandlers = new ArrayList<>();
    private final List<HandlerEntry<ThrowHandler, ThrowStmt>> throwHandlers = new ArrayList<>();
    private final List<HandlerEntry<IfHandler, IfStmt>> ifHandlers = new ArrayList<>();
    private final List<HandlerEntry<LoopHandler, Statement>> loopHandlers = new ArrayList<>();
    private final List<HandlerEntry<TryCatchHandler, TryCatchStmt>> tryCatchHandlers = new ArrayList<>();
    private final List<HandlerEntry<AssignmentHandler, BinaryExpr>> assignmentHandlers = new ArrayList<>();

    private final List<MatcherExprHandler> exprMatcherHandlers = new ArrayList<>();
    private final List<MatcherStmtHandler> stmtMatcherHandlers = new ArrayList<>();

    /**
     * Creates a new AST editor for the given method body.
     */
    public ASTEditor(BlockStmt methodBody, String methodName, String methodDescriptor, String ownerClass) {
        this.methodBody = methodBody;
        this.context = new EditorContext(methodBody, methodName, methodDescriptor, ownerClass);
    }

    /**
     * Creates a new AST editor with minimal context.
     */
    public ASTEditor(BlockStmt methodBody) {
        this(methodBody, null, null, null);
    }

    /**
     * Registers a handler for method call expressions.
     */
    public ASTEditor onMethodCall(MethodCallHandler handler) {
        methodCallHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for field access expressions.
     */
    public ASTEditor onFieldAccess(FieldAccessHandler handler) {
        fieldAccessHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for new object expressions.
     */
    public ASTEditor onNewExpr(NewExprHandler handler) {
        newExprHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for new array expressions.
     */
    public ASTEditor onNewArray(NewArrayHandler handler) {
        newArrayHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for cast expressions.
     */
    public ASTEditor onCast(CastHandler handler) {
        castHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for instanceof expressions.
     */
    public ASTEditor onInstanceOf(InstanceOfHandler handler) {
        instanceOfHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for binary expressions.
     */
    public ASTEditor onBinaryExpr(BinaryExprHandler handler) {
        binaryExprHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for unary expressions.
     */
    public ASTEditor onUnaryExpr(UnaryExprHandler handler) {
        unaryExprHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for array access expressions.
     * The handler receives context about whether the access is a read or store operation.
     */
    public ASTEditor onArrayAccess(ArrayAccessHandler handler) {
        arrayAccessHandlers.add(new ArrayAccessHandlerEntry(handler));
        return this;
    }

    /**
     * Registers a handler for array read operations only.
     * This is a convenience method that filters to only READ access types.
     */
    public ASTEditor onArrayRead(ArrayAccessHandler handler) {
        arrayAccessHandlers.add(new ArrayAccessHandlerEntry(handler, ArrayAccessHandler.ArrayAccessType.READ));
        return this;
    }

    /**
     * Registers a handler for array store operations only.
     * This is a convenience method that filters to only STORE access types.
     */
    public ASTEditor onArrayStore(ArrayAccessHandler handler) {
        arrayAccessHandlers.add(new ArrayAccessHandlerEntry(handler, ArrayAccessHandler.ArrayAccessType.STORE));
        return this;
    }

    /**
     * Registers a handler for return statements.
     */
    public ASTEditor onReturn(ReturnHandler handler) {
        returnHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for throw statements.
     */
    public ASTEditor onThrow(ThrowHandler handler) {
        throwHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for if statements.
     */
    public ASTEditor onIf(IfHandler handler) {
        ifHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for loop statements (for, while, do-while, for-each).
     */
    public ASTEditor onLoop(LoopHandler handler) {
        loopHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for try-catch statements.
     */
    public ASTEditor onTryCatch(TryCatchHandler handler) {
        tryCatchHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for assignment expressions.
     */
    public ASTEditor onAssignment(AssignmentHandler handler) {
        assignmentHandlers.add(new HandlerEntry<>(handler, null));
        return this;
    }

    /**
     * Registers a handler for expressions matching the given matcher.
     */
    public ASTEditor onExpr(ExprMatcher matcher, ExpressionHandler handler) {
        exprMatcherHandlers.add(new MatcherExprHandler(matcher, handler));
        return this;
    }

    /**
     * Registers a handler for statements matching the given matcher.
     */
    public ASTEditor onStmt(StmtMatcher matcher, StatementHandler handler) {
        stmtMatcherHandlers.add(new MatcherStmtHandler(matcher, handler));
        return this;
    }

    /**
     * Applies all registered handlers and modifies the AST in place.
     */
    public void apply() {
        processBlock(methodBody);
    }

    /**
     * Applies all registered handlers and returns the modified method body.
     */
    public BlockStmt applyAndReturn() {
        apply();
        return methodBody;
    }

    /**
     * Finds all expressions matching the given matcher.
     */
    public List<Expression> findExpressions(ExprMatcher matcher) {
        List<Expression> results = new ArrayList<>();
        collectExpressions(methodBody, matcher, results);
        return results;
    }

    /**
     * Finds all statements matching the given matcher.
     */
    public List<Statement> findStatements(StmtMatcher matcher) {
        List<Statement> results = new ArrayList<>();
        collectStatements(methodBody, matcher, results);
        return results;
    }

    private void processBlock(BlockStmt block) {
        context.setEnclosingBlock(block);
        List<Statement> statements = block.getStatements();

        for (int i = 0; i < statements.size(); i++) {
            context.setStatementIndex(i);
            Statement stmt = statements.get(i);
            context.setCurrentStatement(stmt);

            StatementResult result = processStatement(stmt);

            if (result.replacement != null && !result.replacement.isKeep()) {
                i = applyStatementReplacement(statements, i, result.replacement);
            }
        }
    }

    private StatementResult processStatement(Statement stmt) {
        Replacement replacement = Replacement.keep();

        if (stmt instanceof BlockStmt) {
            processBlock((BlockStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            context.enterConditional();
            replacement = processIfStmt((IfStmt) stmt);
            context.exitConditional();
        } else if (stmt instanceof WhileStmt) {
            context.enterLoop();
            replacement = processWhileStmt((WhileStmt) stmt);
            context.exitLoop();
        } else if (stmt instanceof DoWhileStmt) {
            context.enterLoop();
            replacement = processDoWhileStmt((DoWhileStmt) stmt);
            context.exitLoop();
        } else if (stmt instanceof ForStmt) {
            context.enterLoop();
            replacement = processForStmt((ForStmt) stmt);
            context.exitLoop();
        } else if (stmt instanceof ForEachStmt) {
            context.enterLoop();
            replacement = processForEachStmt((ForEachStmt) stmt);
            context.exitLoop();
        } else if (stmt instanceof TryCatchStmt) {
            context.enterTry();
            replacement = processTryCatchStmt((TryCatchStmt) stmt);
            context.exitTry();
        } else if (stmt instanceof ReturnStmt) {
            replacement = processReturnStmt((ReturnStmt) stmt);
        } else if (stmt instanceof ThrowStmt) {
            replacement = processThrowStmt((ThrowStmt) stmt);
        } else if (stmt instanceof ExprStmt) {
            replacement = processExprStmt((ExprStmt) stmt);
        } else if (stmt instanceof VarDeclStmt) {
            processVarDeclStmt((VarDeclStmt) stmt);
        } else if (stmt instanceof SwitchStmt) {
            context.enterConditional();
            processSwitchStmt((SwitchStmt) stmt);
            context.exitConditional();
        } else if (stmt instanceof SynchronizedStmt) {
            processSynchronizedStmt((SynchronizedStmt) stmt);
        } else if (stmt instanceof LabeledStmt) {
            processLabeledStmt((LabeledStmt) stmt);
        }

        for (MatcherStmtHandler msh : stmtMatcherHandlers) {
            if (msh.matcher.matches(stmt)) {
                Replacement r = msh.handler.handle(context, stmt);
                if (!r.isKeep()) {
                    replacement = r;
                }
            }
        }

        return new StatementResult(replacement);
    }

    private Replacement processIfStmt(IfStmt stmt) {
        processExpression(stmt.getCondition());

        if (stmt.getThenBranch() instanceof BlockStmt) {
            processBlock((BlockStmt) stmt.getThenBranch());
        } else {
            processStatement(stmt.getThenBranch());
        }

        if (stmt.hasElse()) {
            if (stmt.getElseBranch() instanceof BlockStmt) {
                processBlock((BlockStmt) stmt.getElseBranch());
            } else {
                processStatement(stmt.getElseBranch());
            }
        }

        // Call handlers
        Replacement replacement = Replacement.keep();
        for (HandlerEntry<IfHandler, IfStmt> entry : ifHandlers) {
            Replacement r = entry.handler.handle(context, stmt);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processWhileStmt(WhileStmt stmt) {
        processExpression(stmt.getCondition());
        if (stmt.getBody() instanceof BlockStmt) {
            processBlock((BlockStmt) stmt.getBody());
        } else {
            processStatement(stmt.getBody());
        }

        return callLoopHandlers(stmt);
    }

    private Replacement processDoWhileStmt(DoWhileStmt stmt) {
        if (stmt.getBody() instanceof BlockStmt) {
            processBlock((BlockStmt) stmt.getBody());
        } else {
            processStatement(stmt.getBody());
        }
        processExpression(stmt.getCondition());

        return callLoopHandlers(stmt);
    }

    private Replacement processForStmt(ForStmt stmt) {
        if (stmt.getInit() != null) {
            for (Statement init : stmt.getInit()) {
                processStatement(init);
            }
        }
        if (stmt.getCondition() != null) {
            processExpression(stmt.getCondition());
        }
        if (stmt.getUpdate() != null) {
            for (Expression update : stmt.getUpdate()) {
                processExpression(update);
            }
        }
        if (stmt.getBody() instanceof BlockStmt) {
            processBlock((BlockStmt) stmt.getBody());
        } else {
            processStatement(stmt.getBody());
        }

        return callLoopHandlers(stmt);
    }

    private Replacement processForEachStmt(ForEachStmt stmt) {
        processExpression(stmt.getIterable());
        if (stmt.getBody() instanceof BlockStmt) {
            processBlock((BlockStmt) stmt.getBody());
        } else {
            processStatement(stmt.getBody());
        }

        return callLoopHandlers(stmt);
    }

    private Replacement callLoopHandlers(Statement loopStmt) {
        Replacement replacement = Replacement.keep();
        for (HandlerEntry<LoopHandler, Statement> entry : loopHandlers) {
            Replacement r = entry.handler.handle(context, loopStmt);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processTryCatchStmt(TryCatchStmt stmt) {
        Statement tryBlock = stmt.getTryBlock();
        if (tryBlock instanceof BlockStmt) {
            processBlock((BlockStmt) tryBlock);
        } else {
            processStatement(tryBlock);
        }

        for (CatchClause clause : stmt.getCatches()) {
            Statement catchBody = clause.body();
            if (catchBody instanceof BlockStmt) {
                processBlock((BlockStmt) catchBody);
            } else {
                processStatement(catchBody);
            }
        }

        Statement finallyBlock = stmt.getFinallyBlock();
        if (finallyBlock != null) {
            if (finallyBlock instanceof BlockStmt) {
                processBlock((BlockStmt) finallyBlock);
            } else {
                processStatement(finallyBlock);
            }
        }

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<TryCatchHandler, TryCatchStmt> entry : tryCatchHandlers) {
            Replacement r = entry.handler.handle(context, stmt);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processReturnStmt(ReturnStmt stmt) {
        if (stmt.getValue() != null) {
            processExpression(stmt.getValue());
        }

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<ReturnHandler, ReturnStmt> entry : returnHandlers) {
            Replacement r = entry.handler.handle(context, stmt);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processThrowStmt(ThrowStmt stmt) {
        processExpression(stmt.getException());

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<ThrowHandler, ThrowStmt> entry : throwHandlers) {
            Replacement r = entry.handler.handle(context, stmt);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processExprStmt(ExprStmt stmt) {
        Expression expr = stmt.getExpression();
        ExpressionResult result = processExpression(expr);

        if (result.replacement != null && !result.replacement.isKeep()) {
            if (result.replacement.getType() == Replacement.Type.REPLACE_EXPR) {
                stmt.setExpression(result.replacement.getExpression());
            }
        }

        return Replacement.keep();
    }

    private void processVarDeclStmt(VarDeclStmt stmt) {
        if (stmt.hasInitializer()) {
            processExpression(stmt.getInitializer());
        }
    }

    private void processSwitchStmt(SwitchStmt stmt) {
        processExpression(stmt.getSelector());
        for (SwitchCase switchCase : stmt.getCases()) {
            for (Statement s : switchCase.statements()) {
                processStatement(s);
            }
        }
    }

    private void processSynchronizedStmt(SynchronizedStmt stmt) {
        processExpression(stmt.getLock());
        Statement body = stmt.getBody();
        if (body instanceof BlockStmt) {
            processBlock((BlockStmt) body);
        } else {
            processStatement(body);
        }
    }

    private void processLabeledStmt(LabeledStmt stmt) {
        processStatement(stmt.getStatement());
    }

    private ExpressionResult processExpression(Expression expr) {
        if (expr == null) {
            return new ExpressionResult(Replacement.keep());
        }

        Replacement replacement = Replacement.keep();

        if (expr instanceof MethodCallExpr) {
            replacement = processMethodCall((MethodCallExpr) expr);
        } else if (expr instanceof FieldAccessExpr) {
            replacement = processFieldAccess((FieldAccessExpr) expr);
        } else if (expr instanceof NewExpr) {
            replacement = processNewExpr((NewExpr) expr);
        } else if (expr instanceof NewArrayExpr) {
            replacement = processNewArrayExpr((NewArrayExpr) expr);
        } else if (expr instanceof CastExpr) {
            replacement = processCastExpr((CastExpr) expr);
        } else if (expr instanceof InstanceOfExpr) {
            replacement = processInstanceOfExpr((InstanceOfExpr) expr);
        } else if (expr instanceof BinaryExpr) {
            replacement = processBinaryExpr((BinaryExpr) expr);
        } else if (expr instanceof UnaryExpr) {
            replacement = processUnaryExpr((UnaryExpr) expr);
        } else if (expr instanceof TernaryExpr) {
            processTernaryExpr((TernaryExpr) expr);
        } else if (expr instanceof ArrayAccessExpr) {
            replacement = processArrayAccessExpr((ArrayAccessExpr) expr);
        } else if (expr instanceof LambdaExpr) {
            processLambdaExpr((LambdaExpr) expr);
        }

        for (MatcherExprHandler meh : exprMatcherHandlers) {
            if (meh.matcher.matches(expr)) {
                Replacement r = meh.handler.handle(context, expr);
                if (!r.isKeep()) {
                    replacement = r;
                }
            }
        }

        return new ExpressionResult(replacement);
    }

    private Replacement processMethodCall(MethodCallExpr expr) {
        if (expr.getReceiver() != null) {
            processExpression(expr.getReceiver());
        }
        for (Expression arg : expr.getArguments()) {
            processExpression(arg);
        }

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<MethodCallHandler, MethodCallExpr> entry : methodCallHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processFieldAccess(FieldAccessExpr expr) {
        if (expr.getReceiver() != null) {
            processExpression(expr.getReceiver());
        }

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<FieldAccessHandler, FieldAccessExpr> entry : fieldAccessHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processNewExpr(NewExpr expr) {
        for (Expression arg : expr.getArguments()) {
            processExpression(arg);
        }

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<NewExprHandler, NewExpr> entry : newExprHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processNewArrayExpr(NewArrayExpr expr) {
        for (Expression dim : expr.getDimensions()) {
            processExpression(dim);
        }

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<NewArrayHandler, NewArrayExpr> entry : newArrayHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processCastExpr(CastExpr expr) {
        processExpression(expr.getExpression());

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<CastHandler, CastExpr> entry : castHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processInstanceOfExpr(InstanceOfExpr expr) {
        processExpression(expr.getExpression());

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<InstanceOfHandler, InstanceOfExpr> entry : instanceOfHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processBinaryExpr(BinaryExpr expr) {
        processExpression(expr.getLeft());
        processExpression(expr.getRight());

        Replacement replacement = Replacement.keep();

        if (expr.isAssignment()) {
            for (HandlerEntry<AssignmentHandler, BinaryExpr> entry : assignmentHandlers) {
                Replacement r = entry.handler.handle(context, expr);
                if (!r.isKeep()) {
                    replacement = r;
                }
            }
        }

        for (HandlerEntry<BinaryExprHandler, BinaryExpr> entry : binaryExprHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private Replacement processUnaryExpr(UnaryExpr expr) {
        processExpression(expr.getOperand());

        Replacement replacement = Replacement.keep();
        for (HandlerEntry<UnaryExprHandler, UnaryExpr> entry : unaryExprHandlers) {
            Replacement r = entry.handler.handle(context, expr);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    private void processTernaryExpr(TernaryExpr expr) {
        processExpression(expr.getCondition());
        processExpression(expr.getThenExpr());
        processExpression(expr.getElseExpr());
    }

    private Replacement processArrayAccessExpr(ArrayAccessExpr expr) {
        processExpression(expr.getArray());
        processExpression(expr.getIndex());

        ArrayAccessHandler.ArrayAccessType accessType = determineArrayAccessType(expr);

        Replacement replacement = Replacement.keep();
        for (ArrayAccessHandlerEntry entry : arrayAccessHandlers) {
            if (entry.filterType != null && entry.filterType != accessType) {
                continue;
            }
            Replacement r = entry.handler.handle(context, expr, accessType);
            if (!r.isKeep()) {
                replacement = r;
            }
        }
        return replacement;
    }

    /**
     * Determines whether an array access is a read, store, or compound assignment.
     * @param expr the array access expression
     * @return the type of array access
     */
    private ArrayAccessHandler.ArrayAccessType determineArrayAccessType(ArrayAccessExpr expr) {
        if (expr.getParent() instanceof BinaryExpr) {
            BinaryExpr parent = (BinaryExpr) expr.getParent();
            if (parent.isAssignment() && parent.getLeft() == expr) {
                if (parent.getOperator() != BinaryOperator.ASSIGN) {
                    return ArrayAccessHandler.ArrayAccessType.COMPOUND_ASSIGN;
                }
                return ArrayAccessHandler.ArrayAccessType.STORE;
            }
        }
        return ArrayAccessHandler.ArrayAccessType.READ;
    }

    private void processLambdaExpr(LambdaExpr expr) {
        if (expr.getBody() instanceof BlockStmt) {
            processBlock((BlockStmt) expr.getBody());
        } else if (expr.getBody() instanceof Expression) {
            processExpression((Expression) expr.getBody());
        }
    }

    private int applyStatementReplacement(List<Statement> statements, int index, Replacement replacement) {
        switch (replacement.getType()) {
            case REPLACE_STMT:
                statements.set(index, replacement.getStatement());
                return index;

            case REPLACE_BLOCK:
                statements.remove(index);
                List<Statement> newStmts = replacement.getStatements();
                for (int j = 0; j < newStmts.size(); j++) {
                    statements.add(index + j, newStmts.get(j));
                }
                return index + newStmts.size() - 1;

            case REMOVE:
                statements.remove(index);
                return index - 1;

            case INSERT_BEFORE:
                List<Statement> beforeStmts = replacement.getStatements();
                for (int j = 0; j < beforeStmts.size(); j++) {
                    statements.add(index + j, beforeStmts.get(j));
                }
                return index + beforeStmts.size();

            case INSERT_AFTER:
                List<Statement> afterStmts = replacement.getStatements();
                for (int j = 0; j < afterStmts.size(); j++) {
                    statements.add(index + 1 + j, afterStmts.get(j));
                }
                return index + afterStmts.size();

            default:
                return index;
        }
    }

    private void collectExpressions(Statement stmt, ExprMatcher matcher, List<Expression> results) {
        if (stmt instanceof BlockStmt) {
            for (Statement s : ((BlockStmt) stmt).getStatements()) {
                collectExpressions(s, matcher, results);
            }
        } else if (stmt instanceof ExprStmt) {
            collectExpressionsFromExpr(((ExprStmt) stmt).getExpression(), matcher, results);
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            collectExpressionsFromExpr(ifStmt.getCondition(), matcher, results);
            collectExpressions(ifStmt.getThenBranch(), matcher, results);
            if (ifStmt.hasElse()) {
                collectExpressions(ifStmt.getElseBranch(), matcher, results);
            }
        }
    }

    private void collectExpressionsFromExpr(Expression expr, ExprMatcher matcher, List<Expression> results) {
        if (expr == null) return;

        if (matcher.matches(expr)) {
            results.add(expr);
        }

        if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            if (call.getReceiver() != null) {
                collectExpressionsFromExpr(call.getReceiver(), matcher, results);
            }
            for (Expression arg : call.getArguments()) {
                collectExpressionsFromExpr(arg, matcher, results);
            }
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr bin = (BinaryExpr) expr;
            collectExpressionsFromExpr(bin.getLeft(), matcher, results);
            collectExpressionsFromExpr(bin.getRight(), matcher, results);
        } else if (expr instanceof UnaryExpr) {
            collectExpressionsFromExpr(((UnaryExpr) expr).getOperand(), matcher, results);
        }
    }

    private void collectStatements(Statement stmt, StmtMatcher matcher, List<Statement> results) {
        if (matcher.matches(stmt)) {
            results.add(stmt);
        }

        if (stmt instanceof BlockStmt) {
            for (Statement s : ((BlockStmt) stmt).getStatements()) {
                collectStatements(s, matcher, results);
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            collectStatements(ifStmt.getThenBranch(), matcher, results);
            if (ifStmt.hasElse()) {
                collectStatements(ifStmt.getElseBranch(), matcher, results);
            }
        }
    }

    private static class HandlerEntry<H, N> {
        final H handler;
        final ExprMatcher matcher;

        HandlerEntry(H handler, ExprMatcher matcher) {
            this.handler = handler;
            this.matcher = matcher;
        }
    }

    private static class MatcherExprHandler {
        final ExprMatcher matcher;
        final ExpressionHandler handler;

        MatcherExprHandler(ExprMatcher matcher, ExpressionHandler handler) {
            this.matcher = matcher;
            this.handler = handler;
        }
    }

    private static class MatcherStmtHandler {
        final StmtMatcher matcher;
        final StatementHandler handler;

        MatcherStmtHandler(StmtMatcher matcher, StatementHandler handler) {
            this.matcher = matcher;
            this.handler = handler;
        }
    }

    private static class StatementResult {
        final Replacement replacement;

        StatementResult(Replacement replacement) {
            this.replacement = replacement;
        }
    }

    private static class ExpressionResult {
        final Replacement replacement;

        ExpressionResult(Replacement replacement) {
            this.replacement = replacement;
        }
    }

    private static class ArrayAccessHandlerEntry {
        final ArrayAccessHandler handler;
        final ArrayAccessHandler.ArrayAccessType filterType;

        ArrayAccessHandlerEntry(ArrayAccessHandler handler) {
            this.handler = handler;
            this.filterType = null;
        }

        ArrayAccessHandlerEntry(ArrayAccessHandler handler, ArrayAccessHandler.ArrayAccessType filterType) {
            this.handler = handler;
            this.filterType = filterType;
        }
    }
}
