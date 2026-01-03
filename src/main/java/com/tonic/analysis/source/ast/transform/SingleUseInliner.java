package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.*;

/**
 * Inlines single-use temporary variables into their usage site.
 *
 * This transform identifies variable declarations where the variable is used
 * exactly once and inlines the initializer expression at the usage site.
 *
 * Example before:
 *   boolean flag2 = local1.isSuccess();
 *   if (!flag2) { ... }
 *
 * Example after:
 *   if (!local1.isSuccess()) { ... }
 *
 * Safety conditions:
 * - Variable must be used exactly once
 * - No intervening statements with side effects that could affect the result
 * - Usage must be in same scope or nested scope (not a loop body for non-loop vars)
 * - Initializer expression must be safe to move (no order-dependent side effects)
 */
public class SingleUseInliner implements ASTTransform {

    @Override
    public String getName() {
        return "SingleUseInliner";
    }

    @Override
    public boolean transform(BlockStmt block) {
        boolean changed = false;
        boolean madeProgress;

        do {
            madeProgress = inlineSingleUseVars(block.getStatements());
            if (madeProgress) {
                changed = true;
            }
        } while (madeProgress);

        return changed;
    }

    private boolean inlineSingleUseVars(List<Statement> stmts) {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);

            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                String varName = decl.getName();
                Expression init = decl.getInitializer();

                if (init == null) {
                    continue;
                }

                UsageInfo usage = analyzeUsage(stmts, i, varName);

                if (usage.count == 1 && usage.canInline && usage.usageStmtIndex > i) {
                    if (tryInline(stmts, i, usage.usageStmtIndex, varName, init)) {
                        changed = true;
                        i--;
                        continue;
                    }
                }
            }

            if (transformNested(stmt)) {
                changed = true;
            }
        }

        return changed;
    }

    private static class UsageInfo {
        int count = 0;
        int usageStmtIndex = -1;
        boolean canInline = true;
        boolean usedInLoop = false;
    }

    private UsageInfo analyzeUsage(List<Statement> stmts, int declIndex, String varName) {
        UsageInfo info = new UsageInfo();

        for (int i = declIndex + 1; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            int usesInStmt = countUses(stmt, varName);

            if (usesInStmt > 0) {
                info.count += usesInStmt;
                if (info.usageStmtIndex == -1) {
                    info.usageStmtIndex = i;
                }

                if (isInLoop(stmt, varName)) {
                    info.usedInLoop = true;
                    info.canInline = false;
                }
            }

            if (info.count == 0 && hasSideEffects(stmt)) {
                info.canInline = false;
            }

            if (info.count > 1) {
                info.canInline = false;
                break;
            }
        }

        return info;
    }

    private int countUses(Statement stmt, String varName) {
        UsageCounter counter = new UsageCounter(varName);
        stmt.accept(counter);
        return counter.count;
    }

    private boolean isInLoop(Statement stmt, String varName) {
        if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            return usesVar(whileStmt.getBody(), varName);
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            return usesVar(doWhile.getBody(), varName);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            return usesVar(forStmt.getBody(), varName);
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            return usesVar(forEach.getBody(), varName);
        }
        return false;
    }

    private boolean usesVar(Statement stmt, String varName) {
        UsageCounter counter = new UsageCounter(varName);
        stmt.accept(counter);
        return counter.count > 0;
    }

    private boolean tryInline(List<Statement> stmts, int declIndex, int useIndex, String varName, Expression init) {
        Statement useStmt = stmts.get(useIndex);

        ExpressionReplacer replacer = new ExpressionReplacer(varName, init);
        Statement replaced = replaceInStatement(useStmt, replacer);

        if (replacer.replacementCount == 1) {
            stmts.set(useIndex, replaced);
            stmts.remove(declIndex);
            return true;
        }

        return false;
    }

    private Statement replaceInStatement(Statement stmt, ExpressionReplacer replacer) {
        if (stmt instanceof ExprStmt) {
            ExprStmt exprStmt = (ExprStmt) stmt;
            Expression newExpr = replaceInExpression(exprStmt.getExpression(), replacer);
            return new ExprStmt(newExpr);
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null) {
                Expression newExpr = replaceInExpression(ret.getValue(), replacer);
                return new ReturnStmt(newExpr);
            }
            return stmt;
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            Expression newCond = replaceInExpression(ifStmt.getCondition(), replacer);
            return new IfStmt(newCond, ifStmt.getThenBranch(), ifStmt.getElseBranch());
        } else if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            Expression newCond = replaceInExpression(whileStmt.getCondition(), replacer);
            return new WhileStmt(newCond, whileStmt.getBody());
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            Expression newCond = replaceInExpression(doWhile.getCondition(), replacer);
            return new DoWhileStmt(doWhile.getBody(), newCond);
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            Expression newCond = forStmt.getCondition() != null ?
                replaceInExpression(forStmt.getCondition(), replacer) : null;
            return new ForStmt(forStmt.getInit(), newCond, forStmt.getUpdate(), forStmt.getBody());
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            Expression newExpr = replaceInExpression(switchStmt.getSelector(), replacer);
            return new SwitchStmt(newExpr, switchStmt.getCases());
        } else if (stmt instanceof ThrowStmt) {
            ThrowStmt throwStmt = (ThrowStmt) stmt;
            Expression newExpr = replaceInExpression(throwStmt.getException(), replacer);
            return new ThrowStmt(newExpr);
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            Expression newExpr = replaceInExpression(syncStmt.getLock(), replacer);
            return new SynchronizedStmt(newExpr, syncStmt.getBody());
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (decl.getInitializer() != null) {
                Expression newInit = replaceInExpression(decl.getInitializer(), replacer);
                return new VarDeclStmt(decl.getType(), decl.getName(), newInit);
            }
            return stmt;
        }

        return stmt;
    }

    private Expression replaceInExpression(Expression expr, ExpressionReplacer replacer) {
        if (expr instanceof VarRefExpr) {
            VarRefExpr varRef = (VarRefExpr) expr;
            if (varRef.getName().equals(replacer.varName)) {
                replacer.replacementCount++;
                return replacer.replacement;
            }
            return expr;
        } else if (expr instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expr;
            Expression newLeft = replaceInExpression(binary.getLeft(), replacer);
            Expression newRight = replaceInExpression(binary.getRight(), replacer);
            if (newLeft != binary.getLeft() || newRight != binary.getRight()) {
                return new BinaryExpr(binary.getOperator(), newLeft, newRight, binary.getType());
            }
            return expr;
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr unary = (UnaryExpr) expr;
            Expression newOperand = replaceInExpression(unary.getOperand(), replacer);
            if (newOperand != unary.getOperand()) {
                return new UnaryExpr(unary.getOperator(), newOperand, unary.getType());
            }
            return expr;
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr call = (MethodCallExpr) expr;
            Expression newReceiver = call.getReceiver() != null ?
                replaceInExpression(call.getReceiver(), replacer) : null;
            List<Expression> newArgs = new ArrayList<>();
            boolean argsChanged = false;
            for (Expression arg : call.getArguments()) {
                Expression newArg = replaceInExpression(arg, replacer);
                newArgs.add(newArg);
                if (newArg != arg) argsChanged = true;
            }
            if (newReceiver != call.getReceiver() || argsChanged) {
                return new MethodCallExpr(newReceiver, call.getMethodName(), call.getOwnerClass(),
                    newArgs, call.isStatic(), call.getType());
            }
            return expr;
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null) {
                Expression newReceiver = replaceInExpression(field.getReceiver(), replacer);
                if (newReceiver != field.getReceiver()) {
                    return new FieldAccessExpr(newReceiver, field.getFieldName(), field.getOwnerClass(),
                        field.isStatic(), field.getType());
                }
            }
            return expr;
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr arr = (ArrayAccessExpr) expr;
            Expression newArray = replaceInExpression(arr.getArray(), replacer);
            Expression newIndex = replaceInExpression(arr.getIndex(), replacer);
            if (newArray != arr.getArray() || newIndex != arr.getIndex()) {
                return new ArrayAccessExpr(newArray, newIndex, arr.getType());
            }
            return expr;
        } else if (expr instanceof CastExpr) {
            CastExpr cast = (CastExpr) expr;
            Expression newExpr = replaceInExpression(cast.getExpression(), replacer);
            if (newExpr != cast.getExpression()) {
                return new CastExpr(cast.getTargetType(), newExpr);
            }
            return expr;
        } else if (expr instanceof InstanceOfExpr) {
            InstanceOfExpr instOf = (InstanceOfExpr) expr;
            Expression newExpr = replaceInExpression(instOf.getExpression(), replacer);
            if (newExpr != instOf.getExpression()) {
                return new InstanceOfExpr(newExpr, instOf.getCheckType());
            }
            return expr;
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr ternary = (TernaryExpr) expr;
            Expression newCond = replaceInExpression(ternary.getCondition(), replacer);
            Expression newThen = replaceInExpression(ternary.getThenExpr(), replacer);
            Expression newElse = replaceInExpression(ternary.getElseExpr(), replacer);
            if (newCond != ternary.getCondition() || newThen != ternary.getThenExpr() ||
                newElse != ternary.getElseExpr()) {
                return new TernaryExpr(newCond, newThen, newElse, ternary.getType());
            }
            return expr;
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            List<Expression> newArgs = new ArrayList<>();
            boolean argsChanged = false;
            for (Expression arg : newExpr.getArguments()) {
                Expression newArg = replaceInExpression(arg, replacer);
                newArgs.add(newArg);
                if (newArg != arg) argsChanged = true;
            }
            if (argsChanged) {
                return new NewExpr(newExpr.getClassName(), newArgs, newExpr.getType());
            }
            return expr;
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr newArr = (NewArrayExpr) expr;
            List<Expression> newDims = new ArrayList<>();
            boolean dimsChanged = false;
            for (Expression dim : newArr.getDimensions()) {
                Expression newDim = replaceInExpression(dim, replacer);
                newDims.add(newDim);
                if (newDim != dim) dimsChanged = true;
            }
            if (dimsChanged) {
                return new NewArrayExpr(newArr.getElementType(), newDims,
                    newArr.getInitializer(), newArr.getType(), newArr.getLocation());
            }
            return expr;
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr arrInit = (ArrayInitExpr) expr;
            List<Expression> newElems = new ArrayList<>();
            boolean elemsChanged = false;
            for (Expression elem : arrInit.getElements()) {
                Expression newElem = replaceInExpression(elem, replacer);
                newElems.add(newElem);
                if (newElem != elem) elemsChanged = true;
            }
            if (elemsChanged) {
                return new ArrayInitExpr(newElems, arrInit.getType());
            }
            return expr;
        }

        return expr;
    }

    private static class ExpressionReplacer {
        final String varName;
        final Expression replacement;
        int replacementCount = 0;

        ExpressionReplacer(String varName, Expression replacement) {
            this.varName = varName;
            this.replacement = replacement;
        }
    }

    private boolean transformNested(Statement stmt) {
        boolean changed = false;

        if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            if (whileStmt.getBody() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) whileStmt.getBody()).getStatements());
            }
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            if (doWhile.getBody() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) doWhile.getBody()).getStatements());
            }
        } else if (stmt instanceof ForStmt) {
            ForStmt forStmt = (ForStmt) stmt;
            if (forStmt.getBody() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) forStmt.getBody()).getStatements());
            }
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt forEach = (ForEachStmt) stmt;
            if (forEach.getBody() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) forEach.getBody()).getStatements());
            }
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            if (ifStmt.getThenBranch() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) ifStmt.getThenBranch()).getStatements());
            }
            if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) ifStmt.getElseBranch()).getStatements());
            }
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            if (tryCatch.getTryBlock() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) tryCatch.getTryBlock()).getStatements());
            }
            for (CatchClause clause : tryCatch.getCatches()) {
                if (clause.body() instanceof BlockStmt) {
                    changed |= inlineSingleUseVars(((BlockStmt) clause.body()).getStatements());
                }
            }
            if (tryCatch.getFinallyBlock() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) tryCatch.getFinallyBlock()).getStatements());
            }
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            for (SwitchCase caseStmt : switchStmt.getCases()) {
                List<Statement> caseStmts = caseStmt.statements();
                if (caseStmts instanceof ArrayList) {
                    changed |= inlineSingleUseVars(caseStmts);
                }
            }
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            if (syncStmt.getBody() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) syncStmt.getBody()).getStatements());
            }
        } else if (stmt instanceof BlockStmt) {
            changed |= inlineSingleUseVars(((BlockStmt) stmt).getStatements());
        }

        changed |= transformLambdasInStatement(stmt);

        return changed;
    }

    private boolean transformLambdasInStatement(Statement stmt) {
        boolean changed = false;
        LambdaFinder finder = new LambdaFinder();
        stmt.accept(finder);

        for (LambdaExpr lambda : finder.lambdas) {
            if (lambda.isBlockBody() && lambda.getBlockBody() instanceof BlockStmt) {
                changed |= inlineSingleUseVars(((BlockStmt) lambda.getBlockBody()).getStatements());
            }
        }
        return changed;
    }

    private static class LambdaFinder extends AbstractSourceVisitor<Void> {
        List<LambdaExpr> lambdas = new ArrayList<>();

        @Override
        public Void visitLambda(LambdaExpr expr) {
            lambdas.add(expr);
            return super.visitLambda(expr);
        }
    }

    private boolean hasSideEffects(Statement stmt) {
        SideEffectChecker checker = new SideEffectChecker();
        stmt.accept(checker);
        return checker.hasSideEffects;
    }

    private static class UsageCounter extends AbstractSourceVisitor<Void> {
        private final String varName;
        int count = 0;

        UsageCounter(String varName) {
            this.varName = varName;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            if (expr.getName().equals(varName)) {
                count++;
            }
            return super.visitVarRef(expr);
        }

        @Override
        public Void visitBinary(BinaryExpr expr) {
            if (expr.getOperator() == BinaryOperator.ASSIGN && expr.getLeft() instanceof VarRefExpr) {
                VarRefExpr left = (VarRefExpr) expr.getLeft();
                if (left.getName().equals(varName)) {
                    count += 100;
                }
                expr.getRight().accept(this);
                return null;
            }
            return super.visitBinary(expr);
        }
    }

    private static class SideEffectChecker extends AbstractSourceVisitor<Void> {
        boolean hasSideEffects = false;

        @Override
        public Void visitMethodCall(MethodCallExpr expr) {
            hasSideEffects = true;
            return super.visitMethodCall(expr);
        }

        @Override
        public Void visitNew(NewExpr expr) {
            hasSideEffects = true;
            return super.visitNew(expr);
        }

        @Override
        public Void visitBinary(BinaryExpr expr) {
            if (expr.getOperator().isAssignment()) {
                hasSideEffects = true;
            }
            return super.visitBinary(expr);
        }

        @Override
        public Void visitUnary(UnaryExpr expr) {
            UnaryOperator op = expr.getOperator();
            if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC ||
                op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC) {
                hasSideEffects = true;
            }
            return super.visitUnary(expr);
        }
    }
}
