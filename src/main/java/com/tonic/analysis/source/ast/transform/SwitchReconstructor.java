package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.expr.VarRefExpr;
import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.ast.stmt.BreakStmt;
import com.tonic.analysis.source.ast.stmt.CatchClause;
import com.tonic.analysis.source.ast.stmt.ContinueStmt;
import com.tonic.analysis.source.ast.stmt.DoWhileStmt;
import com.tonic.analysis.source.ast.stmt.ForEachStmt;
import com.tonic.analysis.source.ast.stmt.ForStmt;
import com.tonic.analysis.source.ast.stmt.IfStmt;
import com.tonic.analysis.source.ast.stmt.LabeledStmt;
import com.tonic.analysis.source.ast.stmt.ReturnStmt;
import com.tonic.analysis.source.ast.stmt.Statement;
import com.tonic.analysis.source.ast.stmt.SwitchCase;
import com.tonic.analysis.source.ast.stmt.SwitchStmt;
import com.tonic.analysis.source.ast.stmt.SynchronizedStmt;
import com.tonic.analysis.source.ast.stmt.ThrowStmt;
import com.tonic.analysis.source.ast.stmt.TryCatchStmt;
import com.tonic.analysis.source.ast.stmt.WhileStmt;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds a cascade of equality comparisons against a single integer variable into a
 * {@code switch} statement.
 *
 * <p>Obfuscated bytecode often dispatches on one int via a hand-rolled chain of
 * {@code if_icmpne}/{@code if_icmpeq} branches instead of a {@code lookupswitch}.
 * Recovery renders this as a deeply nested {@code if (v != c) { rest } else { handler }}
 * staircase. Semantically it is a {@code switch (v) { case c: handler; ... default: ... }},
 * which this transform reconstructs.
 *
 * <p>The transform is deliberately surgical: it only rewrites AST that is unambiguously
 * an equality cascade on one int variable. Anything irregular (variable changes, a
 * non-constant comparison, a duplicate case label, fewer than {@link #MIN_CASES} cases,
 * a non-int selector, or a handler that would change meaning when wrapped in a switch)
 * causes it to leave the subtree untouched.
 */
public class SwitchReconstructor implements ASTTransform {

    /** Minimum distinct cases required before folding (avoid turning a small if/else into a switch). */
    private static final int MIN_CASES = 3;

    /** Signals that a cascade is unfoldable; caught internally to abandon a candidate cleanly. */
    private static final class Bail extends RuntimeException {
        Bail() { super(null, null, false, false); }
    }

    @Override
    public String getName() {
        return "SwitchReconstructor";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return transformBlock(block);
    }

    private boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        List<Statement> stmts = block.getStatements();
        for (int i = 0; i < stmts.size(); i++) {
            Statement s = stmts.get(i);
            if (s instanceof IfStmt) {
                SwitchStmt sw = tryBuildSwitch((IfStmt) s);
                if (sw != null) {
                    stmts.set(i, sw);
                    changed = true;
                    continue;
                }
            }
            changed |= recurseInto(s);
        }
        return changed;
    }

    /** Recurses into the bodies of container statements (but not into switch cases). */
    private boolean recurseInto(Statement s) {
        boolean changed = false;
        if (s instanceof BlockStmt) {
            changed |= transformBlock((BlockStmt) s);
        } else if (s instanceof IfStmt) {
            IfStmt f = (IfStmt) s;
            changed |= recurseInto(f.getThenBranch());
            if (f.hasElse()) {
                changed |= recurseInto(f.getElseBranch());
            }
        } else if (s instanceof WhileStmt) {
            changed |= recurseInto(((WhileStmt) s).getBody());
        } else if (s instanceof DoWhileStmt) {
            changed |= recurseInto(((DoWhileStmt) s).getBody());
        } else if (s instanceof ForStmt) {
            changed |= recurseInto(((ForStmt) s).getBody());
        } else if (s instanceof ForEachStmt) {
            changed |= recurseInto(((ForEachStmt) s).getBody());
        } else if (s instanceof SynchronizedStmt) {
            changed |= recurseInto(((SynchronizedStmt) s).getBody());
        } else if (s instanceof LabeledStmt) {
            changed |= recurseInto(((LabeledStmt) s).getStatement());
        } else if (s instanceof TryCatchStmt) {
            TryCatchStmt tc = (TryCatchStmt) s;
            if (tc.getTryBlock() != null) changed |= recurseInto(tc.getTryBlock());
            for (CatchClause cc : tc.getCatches()) {
                if (cc.body() != null) changed |= recurseInto(cc.body());
            }
            if (tc.getFinallyBlock() != null) changed |= recurseInto(tc.getFinallyBlock());
        }
        return changed;
    }

    /**
     * Attempts to fold the cascade rooted at {@code root} into a switch.
     * Returns null (leaving the AST untouched) if the structure is not a clean,
     * safe equality cascade.
     */
    private SwitchStmt tryBuildSwitch(IfStmt root) {
        LinkedHashMap<Integer, List<Statement>> cases = new LinkedHashMap<>();
        VarRefExpr[] selector = new VarRefExpr[1];
        List<Statement> defaultBody;
        try {
            defaultBody = walkCascade(root, cases, selector);
        } catch (Bail e) {
            return null;
        }

        if (selector[0] == null || cases.size() < MIN_CASES) {
            return null;
        }
        if (!isIntSwitchable(selector[0].getType())) {
            return null;
        }
        for (List<Statement> body : cases.values()) {
            if (containsBreakHazard(body)) return null;
        }
        if (defaultBody != null && containsBreakHazard(defaultBody)) {
            return null;
        }

        SwitchStmt sw = new SwitchStmt(new VarRefExpr(selector[0].getName(), selector[0].getType()));
        for (Map.Entry<Integer, List<Statement>> e : cases.entrySet()) {
            sw.addCase(SwitchCase.of(e.getKey(), e.getValue()));
        }
        if (defaultBody != null && !defaultBody.isEmpty()) {
            sw.addCase(SwitchCase.defaultCase(defaultBody));
        }
        return sw;
    }

    /**
     * Walks the cascade, recording (constant -&gt; handler body) pairs in source order.
     * Returns the default body (the terminal continuation), or null if there is none.
     * Throws {@link Bail} if the cascade is malformed (e.g. duplicate label).
     */
    private List<Statement> walkCascade(Statement node,
                                        LinkedHashMap<Integer, List<Statement>> cases,
                                        VarRefExpr[] selector) {
        Statement current = node;
        while (true) {
            Statement u = unwrapSingle(current);

            // Else-form step: if (v == c) handler else rest  /  if (v != c) rest else handler
            if (u instanceof IfStmt) {
                IfStmt f = (IfStmt) u;
                Cond m = matchEquality(f.getCondition());
                if (m != null && f.hasElse() && sameSelector(selector, m)) {
                    Statement handler = m.isEq ? f.getThenBranch() : f.getElseBranch();
                    Statement continuation = m.isEq ? f.getElseBranch() : f.getThenBranch();
                    addCase(cases, m.constant, statementsOf(handler));
                    current = continuation;
                    continue;
                }
                break; // if-statement that is not a continuation of this cascade → default body
            }

            // Guard-form terminal: a block [ if (v != c) { TERMINAL } , ...handler ]
            if (current instanceof BlockStmt) {
                List<Statement> list = ((BlockStmt) current).getStatements();
                if (list.size() >= 2 && list.get(0) instanceof IfStmt) {
                    IfStmt guard = (IfStmt) list.get(0);
                    Cond m = matchEquality(guard.getCondition());
                    if (m != null && !m.isEq && !guard.hasElse()
                            && sameSelector(selector, m)
                            && isEarlyExit(guard.getThenBranch())) {
                        List<Statement> handler = new ArrayList<>(list.subList(1, list.size()));
                        addCase(cases, m.constant, handler);
                        return statementsOf(guard.getThenBranch());
                    }
                }
            }

            break; // terminal → default body
        }
        return statementsOf(current);
    }

    private void addCase(LinkedHashMap<Integer, List<Statement>> cases, int label, List<Statement> body) {
        if (cases.containsKey(label)) {
            throw new Bail(); // duplicate case label is not switch-representable
        }
        cases.put(label, body);
    }

    /**
     * Establishes the selector on first match; afterwards requires the same variable name.
     * Returns false (→ caller treats current node as default) if the variable differs.
     */
    private boolean sameSelector(VarRefExpr[] selector, Cond m) {
        if (selector[0] == null) {
            selector[0] = m.var;
            return true;
        }
        return selector[0].getName().equals(m.var.getName());
    }

    /** Matches {@code var == const} / {@code var != const} with the int constant on either side. */
    private Cond matchEquality(Expression cond) {
        if (!(cond instanceof BinaryExpr)) return null;
        BinaryExpr b = (BinaryExpr) cond;
        boolean isEq;
        if (b.getOperator() == BinaryOperator.EQ) isEq = true;
        else if (b.getOperator() == BinaryOperator.NE) isEq = false;
        else return null;

        Expression l = b.getLeft();
        Expression r = b.getRight();
        if (l instanceof VarRefExpr && intLiteral(r) != null) {
            return new Cond((VarRefExpr) l, intLiteral(r), isEq);
        }
        if (r instanceof VarRefExpr && intLiteral(l) != null) {
            return new Cond((VarRefExpr) r, intLiteral(l), isEq);
        }
        return null;
    }

    private Integer intLiteral(Expression e) {
        if (e instanceof LiteralExpr) {
            Object v = ((LiteralExpr) e).getValue();
            if (v instanceof Integer) return (Integer) v;
        }
        return null;
    }

    private boolean isIntSwitchable(SourceType type) {
        return type == PrimitiveSourceType.INT
                || type == PrimitiveSourceType.SHORT
                || type == PrimitiveSourceType.BYTE
                || type == PrimitiveSourceType.CHAR;
    }

    private boolean isEarlyExit(Statement stmt) {
        Statement u = unwrapSingle(stmt);
        return u instanceof ReturnStmt || u instanceof ThrowStmt;
    }

    private Statement unwrapSingle(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            BlockStmt b = (BlockStmt) stmt;
            if (b.size() == 1) {
                return b.getStatements().get(0);
            }
        }
        return stmt;
    }

    private List<Statement> statementsOf(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            return new ArrayList<>(((BlockStmt) stmt).getStatements());
        }
        List<Statement> list = new ArrayList<>(1);
        list.add(stmt);
        return list;
    }

    /**
     * True if a handler body contains an unlabeled {@code break} that would change meaning
     * once wrapped in a switch (it currently targets the enclosing loop; inside a switch it
     * would target the switch). Breaks already captured by an inner loop/switch are safe, and
     * {@code continue} is never captured by a switch so it is always safe.
     */
    private boolean containsBreakHazard(List<Statement> stmts) {
        for (Statement s : stmts) {
            if (breakHazard(s)) return true;
        }
        return false;
    }

    private boolean breakHazard(Statement s) {
        if (s instanceof BreakStmt) {
            return ((BreakStmt) s).getTargetLabel() == null;
        }
        if (s instanceof ContinueStmt) {
            return false; // switch does not capture continue
        }
        // Inner loops and switches capture an unlabeled break.
        if (s instanceof ForStmt || s instanceof WhileStmt || s instanceof DoWhileStmt
                || s instanceof ForEachStmt || s instanceof SwitchStmt) {
            return false;
        }
        if (s instanceof BlockStmt) {
            return containsBreakHazard(((BlockStmt) s).getStatements());
        }
        if (s instanceof IfStmt) {
            IfStmt f = (IfStmt) s;
            return breakHazard(f.getThenBranch()) || (f.hasElse() && breakHazard(f.getElseBranch()));
        }
        if (s instanceof SynchronizedStmt) {
            return breakHazard(((SynchronizedStmt) s).getBody());
        }
        if (s instanceof LabeledStmt) {
            return breakHazard(((LabeledStmt) s).getStatement());
        }
        if (s instanceof TryCatchStmt) {
            TryCatchStmt tc = (TryCatchStmt) s;
            if (tc.getTryBlock() != null && breakHazard(tc.getTryBlock())) return true;
            for (CatchClause cc : tc.getCatches()) {
                if (cc.body() != null && breakHazard(cc.body())) return true;
            }
            return tc.getFinallyBlock() != null && breakHazard(tc.getFinallyBlock());
        }
        return false;
    }

    /** A matched equality condition: {@code var OP constant}. */
    private static final class Cond {
        final VarRefExpr var;
        final int constant;
        final boolean isEq;

        Cond(VarRefExpr var, int constant, boolean isEq) {
            this.var = var;
            this.constant = constant;
            this.isEq = isEq;
        }
    }
}
