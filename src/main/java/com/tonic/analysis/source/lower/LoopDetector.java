package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

/**
 * Detects the presence of loop statements in an AST.
 * Returns true if any while, do-while, for, or for-each loop is found.
 */
class LoopDetector extends AbstractSourceVisitor<Boolean> {

    private boolean foundLoop = false;

    public boolean visit(BlockStmt body) {
        foundLoop = false;
        body.accept(this);
        return foundLoop;
    }

    @Override
    protected Boolean defaultValue() {
        return false;
    }

    @Override
    public Boolean visitBlock(BlockStmt stmt) {
        if (foundLoop) return true;
        for (Statement s : stmt.getStatements()) {
            s.accept(this);
            if (foundLoop) return true;
        }
        return false;
    }

    @Override
    public Boolean visitWhile(WhileStmt stmt) {
        foundLoop = true;
        return true;
    }

    @Override
    public Boolean visitDoWhile(DoWhileStmt stmt) {
        foundLoop = true;
        return true;
    }

    @Override
    public Boolean visitFor(ForStmt stmt) {
        foundLoop = true;
        return true;
    }

    @Override
    public Boolean visitForEach(ForEachStmt stmt) {
        foundLoop = true;
        return true;
    }

    @Override
    public Boolean visitIf(IfStmt stmt) {
        if (foundLoop) return true;
        stmt.getThenBranch().accept(this);
        if (foundLoop) return true;
        if (stmt.hasElse()) {
            stmt.getElseBranch().accept(this);
        }
        return foundLoop;
    }

    @Override
    public Boolean visitTryCatch(TryCatchStmt stmt) {
        if (foundLoop) return true;
        stmt.getTryBlock().accept(this);
        if (foundLoop) return true;
        for (CatchClause c : stmt.getCatches()) {
            c.body().accept(this);
            if (foundLoop) return true;
        }
        if (stmt.hasFinally()) {
            stmt.getFinallyBlock().accept(this);
        }
        return foundLoop;
    }

    @Override
    public Boolean visitSwitch(SwitchStmt stmt) {
        if (foundLoop) return true;
        for (SwitchCase c : stmt.getCases()) {
            for (Statement s : c.statements()) {
                s.accept(this);
                if (foundLoop) return true;
            }
        }
        return foundLoop;
    }

    @Override
    public Boolean visitLabeled(LabeledStmt stmt) {
        if (foundLoop) return true;
        stmt.getStatement().accept(this);
        return foundLoop;
    }

    @Override
    public Boolean visitSynchronized(SynchronizedStmt stmt) {
        if (foundLoop) return true;
        stmt.getBody().accept(this);
        return foundLoop;
    }
}
