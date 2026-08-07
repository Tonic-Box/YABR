package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.Locations;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.SwitchExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.SourceType;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconstructs switch expressions (Java 14) from the classic statement form javac lowers them to.
 */
public class SwitchExpressionReconstructor implements ASTTransform
{

    @Override
    public String getName()
    {
        return "SwitchExpressionReconstructor";
    }

    @Override
    public boolean transform(BlockStmt block)
    {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts)
    {
        boolean changed = reconstruct(stmts);
        for (Statement s : stmts)
        {
            changed |= recurse(s);
        }
        return changed;
    }

    private boolean recurse(ASTNode node)
    {
        boolean changed = false;
        for (ASTNode child : node.getChildren())
        {
            if (child instanceof BlockStmt)
            {
                changed |= process(((BlockStmt) child).getStatements());
            }
            else
            {
                changed |= recurse(child);
            }
        }
        return changed;
    }

    private boolean reconstruct(List<Statement> stmts)
    {
        boolean changed = false;
        for (int i = 0; i + 1 < stmts.size(); i++)
        {
            if (!(stmts.get(i) instanceof VarDeclStmt) || !(stmts.get(i + 1) instanceof SwitchStmt))
            {
                continue;
            }
            VarDeclStmt decl = (VarDeclStmt) stmts.get(i);
            SwitchStmt sw = (SwitchStmt) stmts.get(i + 1);

            SwitchExpr folded = tryFoldAssignmentSwitch(decl, sw);
            if (folded != null)
            {
                // A SYNTHETIC carrier (a materialized stack phi, not a source local) immediately
                // returned, with no other use, is the return idiom itself: `return switch (sel)
                // { ... };`. A real local keeps its declaration - the source had one.
                if (decl.isSynthetic()
                        && i + 2 < stmts.size() && isReturnOf(stmts.get(i + 2), decl.getName())
                        && !anyReferences(stmts, i + 3, decl.getName()))
                {
                    ReturnStmt returnStmt = new ReturnStmt(folded);
                    Locations.copy(decl, returnStmt);
                    stmts.set(i, returnStmt);
                    stmts.remove(i + 2);
                    stmts.remove(i + 1);
                }
                else
                {
                    VarDeclStmt foldedDecl = new VarDeclStmt(decl.getType(), decl.getName(), folded);
                    Locations.copy(decl, foldedDecl);
                    stmts.set(i, foldedDecl);
                    stmts.remove(i + 1);
                }
                changed = true;
                continue;
            }

            SwitchExpr returned = tryFoldReturnSwitch(decl, sw);
            if (returned != null)
            {
                ReturnStmt returnStmt = new ReturnStmt(returned);
                Locations.copy(decl, returnStmt);
                stmts.set(i, returnStmt);
                stmts.remove(i + 1); // the switch
                // Drop the (now dead) trailing `return v`.
                if (i + 1 < stmts.size() && isReturnOf(stmts.get(i + 1), decl.getName()))
                {
                    stmts.remove(i + 1);
                }
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Folds a declaration followed by an assigning switch into a switch expression initializer.
     */
    private SwitchExpr tryFoldReturnSwitch(VarDeclStmt decl, SwitchStmt sw)
    {
        if (!sw.hasDefault() || !decl.hasInitializer())
        {
            return null;
        }
        String varName = decl.getName();
        List<SwitchExpr.Arm> arms = new ArrayList<>();
        for (SwitchCase c : sw.getCases())
        {
            List<Statement> body = c.statements();
            if (body.isEmpty() || !isReturnOf(body.get(body.size() - 1), varName))
            {
                return null;
            }
            Expression result;
            if (body.size() == 1)
            {
                // just `return v`: yields the variable's current value (the declared init)
                result = decl.getInitializer();
            }
            else if (body.size() == 2 && body.get(0) instanceof ExprStmt)
            {
                result = assignmentValueTo((ExprStmt) body.get(0), varName);
                if (result == null)
                {
                    return null;
                }
            }
            else
            {
                return null;
            }
            arms.add(new SwitchExpr.Arm(armLabels(c), c.isDefault(), result));
        }
        return new SwitchExpr(sw.getSelector(), arms, decl.getType());
    }

    private static boolean isReturnOf(Statement s, String varName)
    {
        if (!(s instanceof ReturnStmt))
        {
            return false;
        }
        Expression v = ((ReturnStmt) s).getValue();
        return v instanceof VarRefExpr && varName.equals(((VarRefExpr) v).getName());
    }

    /**
     * Returns the RHS of {@code v = expr} in an ExprStmt, or null if it isn't that.
     */
    private static Expression assignmentValueTo(ExprStmt stmt, String varName)
    {
        Expression e = stmt.getExpression();
        if (!(e instanceof BinaryExpr))
        {
            return null;
        }
        BinaryExpr assign = (BinaryExpr) e;
        if (assign.getOperator() != BinaryOperator.ASSIGN
                || !(assign.getLeft() instanceof VarRefExpr)
                || !varName.equals(((VarRefExpr) assign.getLeft()).getName()))
        {
            return null;
        }
        return assign.getRight();
    }

    private SwitchExpr tryFoldAssignmentSwitch(VarDeclStmt decl, SwitchStmt sw)
    {
        if (!sw.hasDefault())
        {
            return null;
        }
        String varName = decl.getName();
        SourceType type = decl.getType();
        List<SwitchExpr.Arm> arms = new ArrayList<>();
        for (SwitchCase c : sw.getCases())
        {
            Expression result = singleAssignmentValue(c.statements(), varName);
            if (result == null && !c.fallsThrough() && isPassiveArm(c.statements())
                    && isPureInitializer(decl.getInitializer()))
            {
                // An arm that assigns nothing and leaves the switch keeps the declared initial
                // value: its yielded value IS the (pure) initializer.
                result = decl.getInitializer();
            }
            if (result == null)
            {
                return null;
            }
            // An arm value that READS the assigned variable is an accumulation carried across
            // fall-through (`r += k`), not a self-contained value - a switch EXPRESSION binds each arm
            // to a fresh value with no prior binding, so folding one silently drops the accumulation
            // (and the fall-through it depends on). A real switch expression's arm never reads the
            // target, so this rejects exactly the mis-fold.
            if (referencesVar(result, varName))
            {
                return null;
            }
            arms.add(new SwitchExpr.Arm(armLabels(c), c.isDefault(), result));
        }
        return new SwitchExpr(sw.getSelector(), arms, type);
    }

    /**
     * Whether an arm body carries no effect at all: empty, or a lone {@code break}.
     */
    private static boolean isPassiveArm(List<Statement> stmts)
    {
        return stmts.isEmpty() || (stmts.size() == 1 && stmts.get(0) instanceof BreakStmt);
    }

    /**
     * Whether an initializer may be re-homed into an arm: a literal or a plain variable read.
     */
    private static boolean isPureInitializer(Expression init)
    {
        return init instanceof LiteralExpr || init instanceof VarRefExpr;
    }

    /**
     * Whether any statement from {@code from} onward references the variable.
     */
    private static boolean anyReferences(List<Statement> stmts, int from, String varName)
    {
        for (int i = from; i < stmts.size(); i++)
        {
            if (referencesNode(stmts.get(i), varName))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean referencesNode(ASTNode node, String varName)
    {
        if (node instanceof VarRefExpr && varName.equals(((VarRefExpr) node).getName()))
        {
            return true;
        }
        for (ASTNode child : node.getChildren())
        {
            if (referencesNode(child, varName))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code expr} contains any reference to the variable named {@code varName}.
     */
    private static boolean referencesVar(Expression expr, String varName)
    {
        if (expr instanceof VarRefExpr)
        {
            return varName.equals(((VarRefExpr) expr).getName());
        }
        for (ASTNode child : expr.getChildren())
        {
            if (child instanceof Expression && referencesVar((Expression) child, varName))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * If {@code stmts} is exactly {@code v = expr;} (with an optional trailing break), returns expr.
     */
    private Expression singleAssignmentValue(List<Statement> stmts, String varName)
    {
        if (stmts.isEmpty())
        {
            return null;
        }
        int effective = stmts.size();
        if (stmts.get(effective - 1) instanceof BreakStmt)
        {
            effective--;
        }
        if (effective != 1 || !(stmts.get(0) instanceof ExprStmt))
        {
            return null;
        }
        Expression e = ((ExprStmt) stmts.get(0)).getExpression();
        if (!(e instanceof BinaryExpr))
        {
            return null;
        }
        BinaryExpr assign = (BinaryExpr) e;
        if (assign.getOperator() != BinaryOperator.ASSIGN
                || !(assign.getLeft() instanceof VarRefExpr)
                || !varName.equals(((VarRefExpr) assign.getLeft()).getName()))
        {
            return null;
        }
        return assign.getRight();
    }

    private List<Expression> armLabels(SwitchCase c)
    {
        if (c.isDefault())
        {
            return new ArrayList<>();
        }
        if (c.hasExpressionLabels())
        {
            return new ArrayList<>(c.expressionLabels());
        }
        List<Expression> labels = new ArrayList<>();
        for (Integer label : c.labels())
        {
            labels.add(LiteralExpr.ofInt(label));
        }
        return labels;
    }
}
