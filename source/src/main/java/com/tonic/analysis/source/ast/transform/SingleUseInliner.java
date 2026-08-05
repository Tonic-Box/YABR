package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;
import com.tonic.analysis.ssa.value.SSAValue;
import java.util.*;

/**
 * Inlines single-use temporary variables into their usage site.
 * This transform identifies variable declarations where the variable is used
 * exactly once and inlines the initializer expression at the usage site.
 * Example before:
 *   boolean flag2 = local1.isSuccess();
 *   if (!flag2) { ... }
 * Example after:
 *   if (!local1.isSuccess()) { ... }
 * Safety conditions:
 * - Variable must be used exactly once
 * - No intervening statements with side effects that could affect the result
 * - Usage must be in same scope or nested scope (not a loop body for non-loop vars)
 * - Initializer expression must be safe to move (no order-dependent side effects)
 */
public class SingleUseInliner implements ASTTransform
{

    @Override
    public String getName()
    {
        return "SingleUseInliner";
    }

    /**
     * The method root, for method-wide SSA-identity reference counts; see the inline guard.
     */
    private BlockStmt rootBlock;

    @Override
    public boolean transform(BlockStmt block)
    {
        boolean changed = false;
        boolean madeProgress;

        rootBlock = block;
        do
        {
            madeProgress = inlineSingleUseVars(block.getStatements(), Collections.emptySet());
            if (madeProgress)
            {
                changed = true;
            }
        } while (madeProgress);

        return changed;
    }

    /**
     * Counts references anywhere in the method to the exact SSA value {@code ssa}. The per-list use
     * scan sees only the declaration's own statement list; a recovered capture can be referenced
     * from a guard in a DIFFERENT subtree under the same name and the same underlying value -
     * deleting the declaration would orphan those. SSA identity distinguishes that hazard from
     * ordinary same-named shadow ranges, which are different values.
     */
    private int countSsaRefs(SSAValue ssa)
    {
        int[] n = {0};
        rootBlock.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr expr)
            {
                if (expr.getSsaValue() == ssa)
                {
                    n[0]++;
                }
                return null;
            }
        });
        return n[0];
    }

    /**
     * Counts assignments (plain, compound, increment/decrement) targeting {@code varName} anywhere
     * in the method. A reused name can have another occupant's bare assignment in a sibling subtree
     * that this declaration is the sole declaration for - removing it would orphan that write into
     * an undeclared-variable error, so any method-wide write blocks the inline-and-remove. A true
     * single-use temp is never re-assigned, so ordinary inlining is unaffected.
     */
    private int countMethodWideWrites(String varName)
    {
        int[] n = {0};
        rootBlock.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitBinary(BinaryExpr expr)
            {
                if (expr.getOperator().isAssignment() && expr.getLeft() instanceof VarRefExpr
                        && varName.equals(((VarRefExpr) expr.getLeft()).getName()))
                {
                    n[0]++;
                }
                return super.visitBinary(expr);
            }

            @Override
            public Void visitUnary(UnaryExpr expr)
            {
                UnaryOperator op = expr.getOperator();
                if ((op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC
                        || op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC)
                        && expr.getOperand() instanceof VarRefExpr
                        && varName.equals(((VarRefExpr) expr.getOperand()).getName()))
                {
                    n[0]++;
                }
                return super.visitUnary(expr);
            }
        });
        return n[0];
    }

    /**
     * The SSA value of the first {@code varName} reference inside {@code stmt}, or null.
     */
    private SSAValue refSsaIn(Statement stmt, String varName)
    {
        SSAValue[] found = {null};
        stmt.accept(new AbstractSourceVisitor<Void>() {
            @Override
            public Void visitVarRef(VarRefExpr expr)
            {
                if (found[0] == null && varName.equals(expr.getName()) && expr.getSsaValue() != null)
                {
                    found[0] = expr.getSsaValue();
                }
                return null;
            }
        });
        return found[0];
    }

    /**
     * @param escapeRefs names of variables that, though declared in the current statement list, are read
     *        from an enclosing scope this list sits inside - specifically a {@code try} body whose value
     *        is used after the try (or in its catch/finally). javac saves such a value to a temp before the
     *        inlined finally (e.g. {@code return x++} in a try/finally), which recovery declares inside the
     *        try and the declaration hoister later lifts to method scope. Inlining and removing that
     *        declaration first would orphan the outside read, so a name in {@code escapeRefs} is never
     *        inlined-and-removed. Only try boundaries populate this set, so ordinary nested temps (loop
     *        counters, if-arm locals) are unaffected.
     */
    private boolean inlineSingleUseVars(List<Statement> stmts, Set<String> escapeRefs)
    {
        boolean changed = false;

        for (int i = 0; i < stmts.size(); i++)
        {
            Statement stmt = stmts.get(i);

            if (stmt instanceof VarDeclStmt)
            {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                String varName = decl.getName();
                Expression init = decl.getInitializer();

                if (init == null)
                {
                    continue;
                }

                UsageInfo usage = analyzeUsage(stmts, i, varName);
                if (System.getProperty("yabr.trace.inline") != null)
                {
                    System.err.println("[INLINE] var=" + varName + " count=" + usage.count
                            + " canInline=" + usage.canInline + " useIdx=" + usage.usageStmtIndex
                            + " declIdx=" + i + " escaped=" + escapeRefs.contains(varName));
                }

                if (usage.count == 1 && usage.canInline && usage.usageStmtIndex > i && !escapeRefs.contains(varName))
                {
                    SSAValue useSsa =
                            refSsaIn(stmts.get(usage.usageStmtIndex), varName);
                    if (useSsa != null && countSsaRefs(useSsa) > usage.count)
                    {
                        continue;
                    }
                    if (countMethodWideWrites(varName) > 0)
                    {
                        continue;
                    }
                    if (tryInline(stmts, i, usage.usageStmtIndex, varName, init))
                    {
                        changed = true;
                        i--;
                        continue;
                    }
                }
            }

            Set<String> tryBodyEscape = escapeRefs;
            if (stmt instanceof TryCatchStmt)
            {
                tryBodyEscape = new HashSet<>(escapeRefs);
                for (int j = i + 1; j < stmts.size(); j++)
                {
                    addFreeRefs(stmts.get(j), tryBodyEscape);
                }
                TryCatchStmt tc = (TryCatchStmt) stmt;
                for (CatchClause clause : tc.getCatches())
                {
                    if (clause.body() != null)
                    {
                        addFreeRefs(clause.body(), tryBodyEscape);
                    }
                }
                if (tc.getFinallyBlock() != null)
                {
                    addFreeRefs(tc.getFinallyBlock(), tryBodyEscape);
                }
            }

            if (transformNested(stmt, escapeRefs, tryBodyEscape))
            {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Adds the free variable names of a subtree (referenced but not declared within it) to {@code out}.
     */
    private void addFreeRefs(Statement stmt, Set<String> out)
    {
        FreeRefCollector c = new FreeRefCollector();
        stmt.accept(c);
        out.addAll(c.free());
    }

    private static class UsageInfo
    {
        int count = 0;
        int usageStmtIndex = -1;
        boolean canInline = true;
        boolean usedInLoop = false;
    }

    private UsageInfo analyzeUsage(List<Statement> stmts, int declIndex, String varName)
    {
        UsageInfo info = new UsageInfo();

        for (int i = declIndex + 1; i < stmts.size(); i++)
        {
            Statement stmt = stmts.get(i);
            int usesInStmt = countUses(stmt, varName);

            if (usesInStmt > 0)
            {
                info.count += usesInStmt;
                if (info.usageStmtIndex == -1)
                {
                    info.usageStmtIndex = i;
                }

                if (isInLoop(stmt, varName))
                {
                    info.usedInLoop = true;
                    info.canInline = false;
                }
            }

            if (info.count == 0 && hasSideEffects(stmt))
            {
                info.canInline = false;
            }

            if (info.count > 1)
            {
                info.canInline = false;
                break;
            }
        }

        return info;
    }

    private int countUses(Statement stmt, String varName)
    {
        UsageCounter counter = new UsageCounter(varName);
        stmt.accept(counter);
        return counter.count;
    }

    private boolean isInLoop(Statement stmt, String varName)
    {
        // A use is "in a loop" - and so must NOT be inlined - if it is re-evaluated every iteration: the
        // body, but ALSO the loop CONDITION and the for-update. Inlining a once-computed loop-invariant
        // (e.g. a method call like abs(b)) into `for (; i < absB; )` would recompute it each iteration,
        // which changes call frequency and breaks decompile/recompile round-trip stability.
        if (stmt instanceof WhileStmt)
        {
            WhileStmt whileStmt = (WhileStmt) stmt;
            return usesVar(whileStmt.getBody(), varName)
                || usesInExpr(whileStmt.getCondition(), varName);
        }
        else if (stmt instanceof DoWhileStmt)
        {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            return usesVar(doWhile.getBody(), varName)
                || usesInExpr(doWhile.getCondition(), varName);
        }
        else if (stmt instanceof ForStmt)
        {
            ForStmt forStmt = (ForStmt) stmt;
            if (usesVar(forStmt.getBody(), varName) || usesInExpr(forStmt.getCondition(), varName))
            {
                return true;
            }
            if (forStmt.getUpdate() != null)
            {
                for (Expression update : forStmt.getUpdate())
                {
                    if (usesInExpr(update, varName))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        else if (stmt instanceof ForEachStmt)
        {
            // The iterable is evaluated once (inlining there is safe); only the body re-runs.
            ForEachStmt forEach = (ForEachStmt) stmt;
            return usesVar(forEach.getBody(), varName);
        }
        return false;
    }

    private boolean usesVar(Statement stmt, String varName)
    {
        UsageCounter counter = new UsageCounter(varName);
        stmt.accept(counter);
        return counter.count > 0;
    }

    private boolean usesInExpr(Expression expr, String varName)
    {
        if (expr == null)
        {
            return false;
        }
        UsageCounter counter = new UsageCounter(varName);
        expr.accept(counter);
        return counter.count > 0;
    }

    private boolean tryInline(List<Statement> stmts, int declIndex, int useIndex, String varName, Expression init)
    {
        Statement useStmt = stmts.get(useIndex);

        ExpressionReplacer replacer = new ExpressionReplacer(varName, init);
        Statement replaced = replaceInStatement(useStmt, replacer);

        if (replacer.replacementCount == 1)
        {
            stmts.set(useIndex, replaced);
            stmts.remove(declIndex);
            return true;
        }

        return false;
    }

    private Statement replaceInStatement(Statement stmt, ExpressionReplacer replacer)
    {
        Statement replaced = replaceInStatement0(stmt, replacer);
        if (replaced != stmt)
        {
            Locations.copy(stmt, replaced);
        }
        return replaced;
    }

    private Statement replaceInStatement0(Statement stmt, ExpressionReplacer replacer)
    {
        if (stmt instanceof ExprStmt)
        {
            ExprStmt exprStmt = (ExprStmt) stmt;
            Expression newExpr = replaceInExpression(exprStmt.getExpression(), replacer);
            return new ExprStmt(newExpr);
        }
        else if (stmt instanceof ReturnStmt)
        {
            ReturnStmt ret = (ReturnStmt) stmt;
            if (ret.getValue() != null)
            {
                Expression newExpr = replaceInExpression(ret.getValue(), replacer);
                // Inlining an int-typed spill into a boolean method's return surfaces the JVM's 0/1 form;
                // render the boolean literal the source had.
                if (ret.getMethodReturnType() == PrimitiveSourceType.BOOLEAN
                        && newExpr instanceof LiteralExpr
                        && ((LiteralExpr) newExpr).getValue() instanceof Integer)
                {
                    int iv = (Integer) ((LiteralExpr) newExpr).getValue();
                    if (iv == 0 || iv == 1)
                    {
                        newExpr = LiteralExpr.ofBoolean(iv != 0);
                    }
                }
                ReturnStmt rebuilt = new ReturnStmt(newExpr);
                rebuilt.setMethodReturnType(ret.getMethodReturnType());
                return rebuilt;
            }
            return stmt;
        }
        else if (stmt instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) stmt;
            Expression newCond = replaceInExpression(ifStmt.getCondition(), replacer);
            return new IfStmt(newCond, ifStmt.getThenBranch(), ifStmt.getElseBranch());
        }
        else if (stmt instanceof WhileStmt)
        {
            WhileStmt whileStmt = (WhileStmt) stmt;
            Expression newCond = replaceInExpression(whileStmt.getCondition(), replacer);
            return new WhileStmt(newCond, whileStmt.getBody());
        }
        else if (stmt instanceof DoWhileStmt)
        {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            Expression newCond = replaceInExpression(doWhile.getCondition(), replacer);
            return new DoWhileStmt(doWhile.getBody(), newCond);
        }
        else if (stmt instanceof ForStmt)
        {
            ForStmt forStmt = (ForStmt) stmt;
            Expression newCond = forStmt.getCondition() != null ?
                replaceInExpression(forStmt.getCondition(), replacer) : null;
            return new ForStmt(forStmt.getInit(), newCond, forStmt.getUpdate(), forStmt.getBody());
        }
        else if (stmt instanceof SwitchStmt)
        {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            Expression newExpr = replaceInExpression(switchStmt.getSelector(), replacer);
            return new SwitchStmt(newExpr, switchStmt.getCases());
        }
        else if (stmt instanceof ThrowStmt)
        {
            ThrowStmt throwStmt = (ThrowStmt) stmt;
            Expression newExpr = replaceInExpression(throwStmt.getException(), replacer);
            return new ThrowStmt(newExpr);
        }
        else if (stmt instanceof SynchronizedStmt)
        {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            Expression newExpr = replaceInExpression(syncStmt.getLock(), replacer);
            return new SynchronizedStmt(newExpr, syncStmt.getBody());
        }
        else if (stmt instanceof VarDeclStmt)
        {
            VarDeclStmt decl = (VarDeclStmt) stmt;
            if (decl.getInitializer() != null)
            {
                Expression newInit = replaceInExpression(decl.getInitializer(), replacer);
                return new VarDeclStmt(decl.getType(), decl.getName(), newInit);
            }
            return stmt;
        }

        return stmt;
    }

    private Expression replaceInExpression(Expression expr, ExpressionReplacer replacer)
    {
        if (expr instanceof VarRefExpr)
        {
            VarRefExpr varRef = (VarRefExpr) expr;
            if (varRef.getName().equals(replacer.varName))
            {
                replacer.replacementCount++;
                return replacer.replacement;
            }
            return expr;
        }
        else if (expr instanceof BinaryExpr)
        {
            BinaryExpr binary = (BinaryExpr) expr;
            Expression newLeft = replaceInExpression(binary.getLeft(), replacer);
            Expression newRight = replaceInExpression(binary.getRight(), replacer);
            if (newLeft != binary.getLeft() || newRight != binary.getRight())
            {
                return new BinaryExpr(binary.getOperator(), newLeft, newRight, binary.getType());
            }
            return expr;
        }
        else if (expr instanceof UnaryExpr)
        {
            UnaryExpr unary = (UnaryExpr) expr;
            Expression newOperand = replaceInExpression(unary.getOperand(), replacer);
            if (newOperand != unary.getOperand())
            {
                return new UnaryExpr(unary.getOperator(), newOperand, unary.getType());
            }
            return expr;
        }
        else if (expr instanceof MethodCallExpr)
        {
            MethodCallExpr call = (MethodCallExpr) expr;
            Expression newReceiver = call.getReceiver() != null ?
                replaceInExpression(call.getReceiver(), replacer) : null;
            List<Expression> newArgs = new ArrayList<>();
            boolean argsChanged = false;
            for (Expression arg : call.getArguments())
            {
                Expression newArg = replaceInExpression(arg, replacer);
                newArgs.add(newArg);
                if (newArg != arg) argsChanged = true;
            }
            if (newReceiver != call.getReceiver() || argsChanged)
            {
                return new MethodCallExpr(newReceiver, call.getMethodName(), call.getOwnerClass(),
                    newArgs, call.isStatic(), call.getType()).withDescriptor(call.getDescriptor())
                    .withSuperCall(call.isSuperCall());
            }
            return expr;
        }
        else if (expr instanceof FieldAccessExpr)
        {
            FieldAccessExpr field = (FieldAccessExpr) expr;
            if (field.getReceiver() != null)
            {
                Expression newReceiver = replaceInExpression(field.getReceiver(), replacer);
                if (newReceiver != field.getReceiver())
                {
                    return new FieldAccessExpr(newReceiver, field.getFieldName(), field.getOwnerClass(),
                        field.isStatic(), field.getType()).withDescriptor(field.getDescriptor());
                }
            }
            return expr;
        }
        else if (expr instanceof ArrayAccessExpr)
        {
            ArrayAccessExpr arr = (ArrayAccessExpr) expr;
            Expression newArray = replaceInExpression(arr.getArray(), replacer);
            Expression newIndex = replaceInExpression(arr.getIndex(), replacer);
            if (newArray != arr.getArray() || newIndex != arr.getIndex())
            {
                return new ArrayAccessExpr(newArray, newIndex, arr.getType());
            }
            return expr;
        }
        else if (expr instanceof CastExpr)
        {
            CastExpr cast = (CastExpr) expr;
            Expression newExpr = replaceInExpression(cast.getExpression(), replacer);
            if (newExpr != cast.getExpression())
            {
                return new CastExpr(cast.getTargetType(), newExpr);
            }
            return expr;
        }
        else if (expr instanceof InstanceOfExpr)
        {
            InstanceOfExpr instOf = (InstanceOfExpr) expr;
            Expression newExpr = replaceInExpression(instOf.getExpression(), replacer);
            if (newExpr != instOf.getExpression())
            {
                InstanceOfExpr rebuilt = new InstanceOfExpr(newExpr, instOf.getCheckType());
                if (instOf.hasPatternVariable())
                {
                    rebuilt.withPatternVariable(instOf.getPatternVariable());
                }
                return rebuilt;
            }
            return expr;
        }
        else if (expr instanceof TernaryExpr)
        {
            TernaryExpr ternary = (TernaryExpr) expr;
            Expression newCond = replaceInExpression(ternary.getCondition(), replacer);
            Expression newThen = replaceInExpression(ternary.getThenExpr(), replacer);
            Expression newElse = replaceInExpression(ternary.getElseExpr(), replacer);
            if (newCond != ternary.getCondition() || newThen != ternary.getThenExpr() ||
                newElse != ternary.getElseExpr())
                {
                return new TernaryExpr(newCond, newThen, newElse, ternary.getType());
            }
            return expr;
        }
        else if (expr instanceof NewExpr)
        {
            NewExpr newExpr = (NewExpr) expr;
            List<Expression> newArgs = new ArrayList<>();
            boolean argsChanged = false;
            for (Expression arg : newExpr.getArguments())
            {
                Expression newArg = replaceInExpression(arg, replacer);
                newArgs.add(newArg);
                if (newArg != arg) argsChanged = true;
            }
            if (argsChanged)
            {
                return new NewExpr(newExpr.getClassName(), newArgs, newExpr.getType())
                    .withDescriptor(newExpr.getDescriptor());
            }
            return expr;
        }
        else if (expr instanceof NewArrayExpr)
        {
            NewArrayExpr newArr = (NewArrayExpr) expr;
            List<Expression> newDims = new ArrayList<>();
            boolean dimsChanged = false;
            for (Expression dim : newArr.getDimensions())
            {
                Expression newDim = replaceInExpression(dim, replacer);
                newDims.add(newDim);
                if (newDim != dim) dimsChanged = true;
            }
            if (dimsChanged)
            {
                return new NewArrayExpr(newArr.getElementType(), newDims,
                    newArr.getInitializer(), newArr.getType(), newArr.getLocation());
            }
            return expr;
        }
        else if (expr instanceof ArrayInitExpr)
        {
            ArrayInitExpr arrInit = (ArrayInitExpr) expr;
            List<Expression> newElems = new ArrayList<>();
            boolean elemsChanged = false;
            for (Expression elem : arrInit.getElements())
            {
                Expression newElem = replaceInExpression(elem, replacer);
                newElems.add(newElem);
                if (newElem != elem) elemsChanged = true;
            }
            if (elemsChanged)
            {
                return new ArrayInitExpr(newElems, arrInit.getType());
            }
            return expr;
        }

        return expr;
    }

    private static class ExpressionReplacer
    {
        final String varName;
        final Expression replacement;
        int replacementCount = 0;

        ExpressionReplacer(String varName, Expression replacement)
        {
            this.varName = varName;
            this.replacement = replacement;
        }
    }

    private boolean transformNested(Statement stmt, Set<String> escapeRefs, Set<String> tryBodyEscape)
    {
        boolean changed = false;

        if (stmt instanceof WhileStmt)
        {
            WhileStmt whileStmt = (WhileStmt) stmt;
            if (whileStmt.getBody() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) whileStmt.getBody()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof DoWhileStmt)
        {
            DoWhileStmt doWhile = (DoWhileStmt) stmt;
            if (doWhile.getBody() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) doWhile.getBody()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof ForStmt)
        {
            ForStmt forStmt = (ForStmt) stmt;
            if (forStmt.getBody() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) forStmt.getBody()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof ForEachStmt)
        {
            ForEachStmt forEach = (ForEachStmt) stmt;
            if (forEach.getBody() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) forEach.getBody()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof IfStmt)
        {
            IfStmt ifStmt = (IfStmt) stmt;
            if (ifStmt.getThenBranch() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) ifStmt.getThenBranch()).getStatements(), escapeRefs);
            }
            if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) ifStmt.getElseBranch()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof TryCatchStmt)
        {
            TryCatchStmt tryCatch = (TryCatchStmt) stmt;
            if (tryCatch.getTryBlock() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) tryCatch.getTryBlock()).getStatements(), tryBodyEscape);
            }
            for (CatchClause clause : tryCatch.getCatches())
            {
                if (clause.body() instanceof BlockStmt)
                {
                    changed |= inlineSingleUseVars(((BlockStmt) clause.body()).getStatements(), escapeRefs);
                }
            }
            if (tryCatch.getFinallyBlock() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) tryCatch.getFinallyBlock()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof SwitchStmt)
        {
            SwitchStmt switchStmt = (SwitchStmt) stmt;
            for (SwitchCase caseStmt : switchStmt.getCases())
            {
                List<Statement> caseStmts = caseStmt.statements();
                if (caseStmts instanceof ArrayList)
                {
                    changed |= inlineSingleUseVars(caseStmts, escapeRefs);
                }
            }
        }
        else if (stmt instanceof SynchronizedStmt)
        {
            SynchronizedStmt syncStmt = (SynchronizedStmt) stmt;
            if (syncStmt.getBody() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) syncStmt.getBody()).getStatements(), escapeRefs);
            }
        }
        else if (stmt instanceof BlockStmt)
        {
            changed |= inlineSingleUseVars(((BlockStmt) stmt).getStatements(), escapeRefs);
        }

        changed |= transformLambdasInStatement(stmt, escapeRefs);

        return changed;
    }

    private boolean transformLambdasInStatement(Statement stmt, Set<String> escapeRefs)
    {
        boolean changed = false;
        LambdaFinder finder = new LambdaFinder();
        stmt.accept(finder);

        for (LambdaExpr lambda : finder.lambdas)
        {
            if (lambda.isBlockBody() && lambda.getBlockBody() instanceof BlockStmt)
            {
                changed |= inlineSingleUseVars(((BlockStmt) lambda.getBlockBody()).getStatements(), escapeRefs);
            }
        }
        return changed;
    }

    private static class LambdaFinder extends AbstractSourceVisitor<Void>
    {
        List<LambdaExpr> lambdas = new ArrayList<>();

        @Override
        public Void visitLambda(LambdaExpr expr)
        {
            lambdas.add(expr);
            return super.visitLambda(expr);
        }
    }

    private boolean hasSideEffects(Statement stmt)
    {
        SideEffectChecker checker = new SideEffectChecker();
        stmt.accept(checker);
        return checker.hasSideEffects;
    }

    private static class UsageCounter extends AbstractSourceVisitor<Void>
    {
        private final String varName;
        int count = 0;

        UsageCounter(String varName)
        {
            this.varName = varName;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr)
        {
            if (expr.getName().equals(varName))
            {
                count++;
            }
            return super.visitVarRef(expr);
        }

        @Override
        public Void visitBinary(BinaryExpr expr)
        {
            if (expr.getOperator() == BinaryOperator.ASSIGN && expr.getLeft() instanceof VarRefExpr)
            {
                VarRefExpr left = (VarRefExpr) expr.getLeft();
                if (left.getName().equals(varName))
                {
                    count += 100;
                }
                expr.getRight().accept(this);
                return null;
            }
            return super.visitBinary(expr);
        }
    }

    /**
     * Collects a subtree's free variable names: referenced but not declared within it.
     */
    private static class FreeRefCollector extends AbstractSourceVisitor<Void>
    {
        private final Set<String> referenced = new HashSet<>();
        private final Set<String> declared = new HashSet<>();

        Set<String> free()
        {
            Set<String> f = new HashSet<>(referenced);
            f.removeAll(declared);
            return f;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr)
        {
            referenced.add(expr.getName());
            return super.visitVarRef(expr);
        }

        @Override
        public Void visitVarDecl(VarDeclStmt stmt)
        {
            declared.add(stmt.getName());
            return super.visitVarDecl(stmt);
        }
    }

    private static class SideEffectChecker extends AbstractSourceVisitor<Void>
    {
        boolean hasSideEffects = false;

        @Override
        public Void visitMethodCall(MethodCallExpr expr)
        {
            hasSideEffects = true;
            return super.visitMethodCall(expr);
        }

        @Override
        public Void visitNew(NewExpr expr)
        {
            hasSideEffects = true;
            return super.visitNew(expr);
        }

        @Override
        public Void visitBinary(BinaryExpr expr)
        {
            if (expr.getOperator().isAssignment())
            {
                hasSideEffects = true;
            }
            return super.visitBinary(expr);
        }

        @Override
        public Void visitUnary(UnaryExpr expr)
        {
            UnaryOperator op = expr.getOperator();
            if (op == UnaryOperator.PRE_INC || op == UnaryOperator.PRE_DEC ||
                op == UnaryOperator.POST_INC || op == UnaryOperator.POST_DEC)
                {
                hasSideEffects = true;
            }
            return super.visitUnary(expr);
        }
    }
}
