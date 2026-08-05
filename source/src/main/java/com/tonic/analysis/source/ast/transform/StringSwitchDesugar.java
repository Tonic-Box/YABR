package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.MethodCallExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.ExprStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.VarDeclStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites a {@code switch} over {@code String} labels into the two-switch dispatch the JVM can execute:
 * the selector is evaluated once into a temporary, a first switch over {@code hashCode()} narrows to an
 * {@code equals} check per literal (same-hash literals chain within one case) assigning a dense index,
 * and a second switch over the index carries the ORIGINAL case bodies verbatim - breaks, continues and
 * fall-through included. The bytecode switch instruction only dispatches on ints, so a String switch that
 * reaches lowering undesugared loses every case (no label converts) and dispatches straight to default.
 */
public class StringSwitchDesugar implements ASTTransform
{

    private int tempCounter = 0;

    @Override
    public String getName()
    {
        return "StringSwitchDesugar";
    }

    @Override
    public boolean transform(BlockStmt block)
    {
        return process(block.getStatements());
    }

    private boolean process(List<Statement> stmts)
    {
        boolean changed = false;
        for (int i = 0; i < stmts.size(); i++)
        {
            Statement s = stmts.get(i);
            if (s instanceof SwitchStmt && isStringSwitch((SwitchStmt) s))
            {
                List<Statement> desugared = desugar((SwitchStmt) s);
                stmts.remove(i);
                stmts.addAll(i, desugared);
                i += desugared.size() - 1;
                changed = true;
            }
        }
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

    private static boolean isStringSwitch(SwitchStmt sw)
    {
        boolean sawString = false;
        for (SwitchCase c : sw.getCases())
        {
            if (c.isDefault())
            {
                continue;
            }
            if (!c.hasExpressionLabels())
            {
                return false;
            }
            for (Expression label : c.expressionLabels())
            {
                if (!(label instanceof LiteralExpr) || !(((LiteralExpr) label).getValue() instanceof String))
                {
                    return false;
                }
                sawString = true;
            }
        }
        return sawString;
    }

    private List<Statement> desugar(SwitchStmt sw)
    {
        String strName = "$str" + tempCounter;
        String idxName = "$idx" + tempCounter;
        tempCounter++;

        List<Statement> out = new ArrayList<>();
        out.add(new VarDeclStmt(ReferenceSourceType.STRING, strName, sw.getSelector()));
        out.add(new VarDeclStmt(PrimitiveSourceType.INT, idxName, LiteralExpr.ofInt(-1)));

        Map<Integer, List<int[]>> hashGroups = new LinkedHashMap<>();
        List<String> literals = new ArrayList<>();
        List<SwitchCase> indexCases = new ArrayList<>();
        for (SwitchCase c : sw.getCases())
        {
            if (c.isDefault())
            {
                indexCases.add(SwitchCase.defaultCase(c.statements()));
                continue;
            }
            List<Integer> indices = new ArrayList<>();
            for (Expression label : c.expressionLabels())
            {
                String lit = (String) ((LiteralExpr) label).getValue();
                int idx = literals.size();
                literals.add(lit);
                indices.add(idx);
                hashGroups.computeIfAbsent(lit.hashCode(), k -> new ArrayList<>()).add(new int[]{idx});
            }
            indexCases.add(SwitchCase.of(indices, c.statements()));
        }

        List<SwitchCase> hashCases = new ArrayList<>();
        for (Map.Entry<Integer, List<int[]>> group : hashGroups.entrySet())
        {
            IfStmt chain = null;
            for (int g = group.getValue().size() - 1; g >= 0; g--)
            {
                int idx = group.getValue().get(g)[0];
                Expression eq = new MethodCallExpr(
                        new VarRefExpr(strName, ReferenceSourceType.STRING), "equals", "java/lang/String",
                        Collections.singletonList(LiteralExpr.ofString(literals.get(idx))), false,
                        PrimitiveSourceType.BOOLEAN).withDescriptor("(Ljava/lang/Object;)Z");
                Statement assign = new ExprStmt(new BinaryExpr(BinaryOperator.ASSIGN,
                        new VarRefExpr(idxName, PrimitiveSourceType.INT), LiteralExpr.ofInt(idx),
                        PrimitiveSourceType.INT));
                chain = new IfStmt(eq, new BlockStmt(Collections.singletonList(assign)), chain);
            }
            List<Statement> body = new ArrayList<>();
            body.add(chain);
            body.add(new BreakStmt());
            hashCases.add(SwitchCase.of(group.getKey(), body));
        }
        out.add(new SwitchStmt(new MethodCallExpr(
                new VarRefExpr(strName, ReferenceSourceType.STRING), "hashCode", "java/lang/String",
                Collections.emptyList(), false, PrimitiveSourceType.INT).withDescriptor("()I"), hashCases));

        out.add(new SwitchStmt(new VarRefExpr(idxName, PrimitiveSourceType.INT), indexCases));
        return out;
    }
}
