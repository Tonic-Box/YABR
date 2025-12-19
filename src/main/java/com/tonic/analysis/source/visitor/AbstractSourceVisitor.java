package com.tonic.analysis.source.visitor;

import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.type.*;

/**
 * Abstract base implementation of SourceVisitor with default traversal behavior.
 * Subclasses can override specific methods to customize behavior.
 *
 * @param <T> the return type of visit methods
 */
public abstract class AbstractSourceVisitor<T> implements SourceVisitor<T> {

    /**
     * Returns the default value for visit methods.
     * Override this to change the default return value.
     */
    protected T defaultValue() {
        return null;
    }

    // ==================== Statements ====================

    @Override
    public T visitBlock(BlockStmt stmt) {
        for (Statement s : stmt.getStatements()) {
            s.accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitIf(IfStmt stmt) {
        stmt.getCondition().accept(this);
        stmt.getThenBranch().accept(this);
        if (stmt.hasElse()) {
            stmt.getElseBranch().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitWhile(WhileStmt stmt) {
        stmt.getCondition().accept(this);
        stmt.getBody().accept(this);
        return defaultValue();
    }

    @Override
    public T visitDoWhile(DoWhileStmt stmt) {
        stmt.getBody().accept(this);
        stmt.getCondition().accept(this);
        return defaultValue();
    }

    @Override
    public T visitFor(ForStmt stmt) {
        for (Statement s : stmt.getInit()) {
            s.accept(this);
        }
        if (stmt.getCondition() != null) {
            stmt.getCondition().accept(this);
        }
        for (Expression e : stmt.getUpdate()) {
            e.accept(this);
        }
        stmt.getBody().accept(this);
        return defaultValue();
    }

    @Override
    public T visitForEach(ForEachStmt stmt) {
        stmt.getVariable().accept(this);
        stmt.getIterable().accept(this);
        stmt.getBody().accept(this);
        return defaultValue();
    }

    @Override
    public T visitSwitch(SwitchStmt stmt) {
        stmt.getSelector().accept(this);
        for (SwitchCase c : stmt.getCases()) {
            for (Statement s : c.statements()) {
                s.accept(this);
            }
        }
        return defaultValue();
    }

    @Override
    public T visitTryCatch(TryCatchStmt stmt) {
        for (Expression resource : stmt.getResources()) {
            resource.accept(this);
        }
        stmt.getTryBlock().accept(this);
        for (CatchClause c : stmt.getCatches()) {
            c.body().accept(this);
        }
        if (stmt.hasFinally()) {
            stmt.getFinallyBlock().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitReturn(ReturnStmt stmt) {
        if (!stmt.isVoidReturn()) {
            stmt.getValue().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitThrow(ThrowStmt stmt) {
        stmt.getException().accept(this);
        return defaultValue();
    }

    @Override
    public T visitVarDecl(VarDeclStmt stmt) {
        if (stmt.hasInitializer()) {
            stmt.getInitializer().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitExprStmt(ExprStmt stmt) {
        stmt.getExpression().accept(this);
        return defaultValue();
    }

    @Override
    public T visitSynchronized(SynchronizedStmt stmt) {
        stmt.getLock().accept(this);
        stmt.getBody().accept(this);
        return defaultValue();
    }

    @Override
    public T visitLabeled(LabeledStmt stmt) {
        stmt.getStatement().accept(this);
        return defaultValue();
    }

    @Override
    public T visitBreak(BreakStmt stmt) {
        return defaultValue();
    }

    @Override
    public T visitContinue(ContinueStmt stmt) {
        return defaultValue();
    }

    @Override
    public T visitIRRegion(IRRegionStmt stmt) {
        // IRRegion contains IR blocks, not AST nodes
        // Subclasses can override if they need to process IR
        return defaultValue();
    }

    // ==================== Expressions ====================

    @Override
    public T visitLiteral(LiteralExpr expr) {
        return defaultValue();
    }

    @Override
    public T visitVarRef(VarRefExpr expr) {
        return defaultValue();
    }

    @Override
    public T visitFieldAccess(FieldAccessExpr expr) {
        if (expr.getReceiver() != null) {
            expr.getReceiver().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitArrayAccess(ArrayAccessExpr expr) {
        expr.getArray().accept(this);
        expr.getIndex().accept(this);
        return defaultValue();
    }

    @Override
    public T visitMethodCall(MethodCallExpr expr) {
        if (expr.getReceiver() != null) {
            expr.getReceiver().accept(this);
        }
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitNew(NewExpr expr) {
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitNewArray(NewArrayExpr expr) {
        for (Expression dim : expr.getDimensions()) {
            dim.accept(this);
        }
        if (expr.hasInitializer()) {
            expr.getInitializer().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitArrayInit(ArrayInitExpr expr) {
        for (Expression elem : expr.getElements()) {
            elem.accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitBinary(BinaryExpr expr) {
        expr.getLeft().accept(this);
        expr.getRight().accept(this);
        return defaultValue();
    }

    @Override
    public T visitUnary(UnaryExpr expr) {
        expr.getOperand().accept(this);
        return defaultValue();
    }

    @Override
    public T visitCast(CastExpr expr) {
        expr.getExpression().accept(this);
        return defaultValue();
    }

    @Override
    public T visitInstanceOf(InstanceOfExpr expr) {
        expr.getExpression().accept(this);
        return defaultValue();
    }

    @Override
    public T visitTernary(TernaryExpr expr) {
        expr.getCondition().accept(this);
        expr.getThenExpr().accept(this);
        expr.getElseExpr().accept(this);
        return defaultValue();
    }

    @Override
    public T visitLambda(LambdaExpr expr) {
        expr.getBody().accept(this);
        return defaultValue();
    }

    @Override
    public T visitMethodRef(MethodRefExpr expr) {
        if (expr.getReceiver() != null) {
            expr.getReceiver().accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitThis(ThisExpr expr) {
        return defaultValue();
    }

    @Override
    public T visitSuper(SuperExpr expr) {
        return defaultValue();
    }

    @Override
    public T visitClass(ClassExpr expr) {
        return defaultValue();
    }

    @Override
    public T visitDynamicConstant(DynamicConstantExpr expr) {
        return defaultValue();
    }

    @Override
    public T visitInvokeDynamic(InvokeDynamicExpr expr) {
        for (Expression arg : expr.getArguments()) {
            arg.accept(this);
        }
        return defaultValue();
    }

    // ==================== Types ====================

    @Override
    public T visitPrimitiveType(PrimitiveSourceType type) {
        return defaultValue();
    }

    @Override
    public T visitReferenceType(ReferenceSourceType type) {
        for (SourceType typeArg : type.getTypeArguments()) {
            typeArg.accept(this);
        }
        return defaultValue();
    }

    @Override
    public T visitArrayType(ArraySourceType type) {
        type.getComponentType().accept(this);
        return defaultValue();
    }

    @Override
    public T visitVoidType(VoidSourceType type) {
        return defaultValue();
    }
}
