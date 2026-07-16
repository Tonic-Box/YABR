package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

/**
 * Determines whether an expression (or any sub-expression) has side effects. Conservative: unknown
 * expression kinds are assumed to have side effects. Shared by the AST cleanup transforms so they
 * agree on what is safe to remove or reorder.
 */
public final class SideEffectDetector extends AbstractSourceVisitor<Boolean> {

    public static final SideEffectDetector INSTANCE = new SideEffectDetector();

    @Override
    protected Boolean defaultValue() { return true; }

    @Override
    public Boolean visitLiteral(LiteralExpr expr) { return false; }

    @Override
    public Boolean visitVarRef(VarRefExpr expr) { return false; }

    @Override
    public Boolean visitThis(ThisExpr expr) { return false; }

    @Override
    public Boolean visitSuper(SuperExpr expr) { return false; }

    @Override
    public Boolean visitClass(ClassExpr expr) { return false; }

    @Override
    public Boolean visitLambda(LambdaExpr expr) { return false; }

    @Override
    public Boolean visitMethodRef(MethodRefExpr expr) { return false; }

    @Override
    public Boolean visitBinary(BinaryExpr expr) {
        if (expr.getOperator().isAssignment()) return true;
        return expr.getLeft().accept(this) || expr.getRight().accept(this);
    }

    @Override
    public Boolean visitUnary(UnaryExpr expr) {
        UnaryOperator op = expr.getOperator();
        if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC ||
            op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
            return true;
        }
        return expr.getOperand().accept(this);
    }

    @Override
    public Boolean visitTernary(TernaryExpr expr) {
        return expr.getCondition().accept(this) ||
               expr.getThenExpr().accept(this) ||
               expr.getElseExpr().accept(this);
    }

    @Override
    public Boolean visitCast(CastExpr expr) { return expr.getExpression().accept(this); }

    @Override
    public Boolean visitInstanceOf(InstanceOfExpr expr) { return expr.getExpression().accept(this); }

    @Override
    public Boolean visitMethodCall(MethodCallExpr expr) { return true; }

    @Override
    public Boolean visitNew(NewExpr expr) { return true; }

    @Override
    public Boolean visitNewArray(NewArrayExpr expr) { return true; }

    @Override
    public Boolean visitFieldAccess(FieldAccessExpr expr) {
        return expr.getReceiver() != null && expr.getReceiver().accept(this);
    }

    @Override
    public Boolean visitArrayAccess(ArrayAccessExpr expr) {
        return expr.getArray().accept(this) || expr.getIndex().accept(this);
    }

    @Override
    public Boolean visitArrayInit(ArrayInitExpr expr) {
        for (Expression elem : expr.getElements()) {
            if (elem.accept(this)) return true;
        }
        return false;
    }
}
