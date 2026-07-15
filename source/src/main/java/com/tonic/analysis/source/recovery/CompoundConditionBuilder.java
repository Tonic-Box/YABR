package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.BranchInstruction;
import com.tonic.analysis.source.ast.expr.BinaryExpr;
import com.tonic.analysis.source.ast.expr.BinaryOperator;
import com.tonic.analysis.source.ast.expr.Expression;
import com.tonic.analysis.source.ast.expr.LiteralExpr;
import com.tonic.analysis.source.ast.type.PrimitiveSourceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstructs the boolean expression of a short-circuit compound condition from its control-flow
 * DAG. The condition is a set of two-way branch blocks (each a single sub-condition) that all decide
 * between exactly two exits: {@code trueExit} (the whole condition is true) and {@code falseExit} (it
 * is false). Every {@code &&} / {@code ||} nesting, in any mix, is a series-parallel DAG over these
 * blocks; this builder walks it and folds it back into a single expression.
 *
 * <p>Only clean series-parallel short-circuit conditions are reconstructed; any shape that would need
 * a select/ternary to express (a genuine non-short-circuit diamond) makes {@link #build} return null,
 * so the caller falls back to its existing recovery and the output is always a plain {@code &&}/{@code ||}
 * chain.
 */
final class CompoundConditionBuilder {

    /** Resolves a single condition block's branch condition to an expression (negated when asked). */
    @FunctionalInterface
    interface ConditionResolver {
        Expression resolve(IRBlock block, boolean negate);
    }

    // ----- internal normalized boolean tree (AND/OR are flattened, NOT pushed into leaves) -----

    private static final class Node {
        enum Kind { TRUE, FALSE, LEAF, AND, OR }

        final Kind kind;
        final IRBlock block;   // LEAF: the block whose branch condition this is
        final boolean negate;  // LEAF: whether the condition is negated
        final List<Node> ops;  // AND/OR operands

        private Node(Kind kind, IRBlock block, boolean negate, List<Node> ops) {
            this.kind = kind;
            this.block = block;
            this.negate = negate;
            this.ops = ops;
        }

        static final Node TRUE = new Node(Kind.TRUE, null, false, null);
        static final Node FALSE = new Node(Kind.FALSE, null, false, null);

        static Node leaf(IRBlock b, boolean n) {
            return new Node(Kind.LEAF, b, n, null);
        }

        boolean eq(Node o) {
            if (this == o) {
                return true;
            }
            if (kind != o.kind) {
                return false;
            }
            switch (kind) {
                case TRUE:
                case FALSE:
                    return true;
                case LEAF:
                    return block == o.block && negate == o.negate;
                default:
                    return sameSet(ops, o.ops);
            }
        }
    }

    private final IRBlock trueExit;
    private final IRBlock falseExit;
    private final Set<IRBlock> region;
    private final ConditionResolver resolver;
    private final Map<IRBlock, Node> memo = new HashMap<>();
    private final Set<IRBlock> onPath = new HashSet<>();
    private boolean bailed = false;

    private CompoundConditionBuilder(IRBlock trueExit, IRBlock falseExit,
                                     Set<IRBlock> region, ConditionResolver resolver) {
        this.trueExit = trueExit;
        this.falseExit = falseExit;
        this.region = region;
        this.resolver = resolver;
    }

    /**
     * Reconstructs the condition guarding {@code header} that is true exactly when control reaches
     * {@code trueExit} (and false when it reaches {@code falseExit}), over the given condition blocks.
     * Returns null when the shape is not a clean short-circuit chain.
     */
    static Expression build(IRBlock header, IRBlock trueExit, IRBlock falseExit,
                            Set<IRBlock> region, ConditionResolver resolver) {
        CompoundConditionBuilder b = new CompoundConditionBuilder(trueExit, falseExit, region, resolver);
        Node root = b.solve(header);
        if (b.bailed || root == null) {
            return null;
        }
        return b.toExpression(root);
    }

    /**
     * True when the condition DAG is a clean series-parallel short-circuit condition that
     * {@link #build} can reconstruct (no cycle, no genuine ternary). Structural only - no resolver.
     */
    static boolean isReconstructible(IRBlock header, IRBlock trueExit, IRBlock falseExit, Set<IRBlock> region) {
        CompoundConditionBuilder b = new CompoundConditionBuilder(trueExit, falseExit, region, null);
        Node root = b.solve(header);
        return !b.bailed && root != null;
    }

    private Node solve(IRBlock block) {
        if (bailed) {
            return Node.TRUE;
        }
        if (block == trueExit) {
            return Node.TRUE;
        }
        if (block == falseExit) {
            return Node.FALSE;
        }
        Node cached = memo.get(block);
        if (cached != null) {
            return cached;
        }
        if (!region.contains(block) || !(block.getTerminator() instanceof BranchInstruction)) {
            bailed = true;
            return Node.TRUE;
        }
        if (!onPath.add(block)) {
            bailed = true; // a cycle - not a straight-line condition
            return Node.TRUE;
        }
        BranchInstruction branch = (BranchInstruction) block.getTerminator();
        Node t = solve(branch.getTrueTarget());
        Node f = solve(branch.getFalseTarget());
        onPath.remove(block);
        Node result = ite(Node.leaf(block, false), t, f);
        if (result == null) {
            bailed = true;
            return Node.TRUE;
        }
        memo.put(block, result);
        return result;
    }

    /** {@code if cond then thenExpr else elseExpr}, simplified to a short-circuit chain or null. */
    private Node ite(Node cond, Node thenExpr, Node elseExpr) {
        if (thenExpr.kind == Node.Kind.TRUE && elseExpr.kind == Node.Kind.FALSE) {
            return cond;
        }
        if (thenExpr.kind == Node.Kind.FALSE && elseExpr.kind == Node.Kind.TRUE) {
            return not(cond);
        }
        if (thenExpr.kind == Node.Kind.TRUE) {
            return or(cond, elseExpr);            // cond || elseExpr
        }
        if (elseExpr.kind == Node.Kind.FALSE) {
            return and(cond, thenExpr);           // cond && thenExpr
        }
        if (thenExpr.kind == Node.Kind.FALSE) {
            return and(not(cond), elseExpr);      // !cond && elseExpr
        }
        if (elseExpr.kind == Node.Kind.TRUE) {
            return or(not(cond), thenExpr);       // !cond || thenExpr
        }
        if (thenExpr.eq(elseExpr)) {
            return thenExpr;
        }
        // Both arms are compound: factor a shared sub-expression, else it is a genuine ternary - bail.
        Node r;
        if ((r = factorAnd(cond, thenExpr, elseExpr, false)) != null) {
            return r;
        }
        if ((r = factorAnd(cond, thenExpr, elseExpr, true)) != null) {
            return r;
        }
        if ((r = factorOr(cond, thenExpr, elseExpr, false)) != null) {
            return r;
        }
        if ((r = factorOr(cond, thenExpr, elseExpr, true)) != null) {
            return r;
        }
        return null;
    }

    // cond ? thenExpr : (thenExpr && extra)  ->  (cond || extra) && thenExpr      (swap=false)
    // cond ? (elseExpr && extra) : elseExpr  ->  (!cond || extra) && elseExpr     (swap=true)
    private Node factorAnd(Node cond, Node thenExpr, Node elseExpr, boolean swap) {
        Node whole = swap ? thenExpr : elseExpr;
        Node part = swap ? elseExpr : thenExpr;
        if (whole.kind != Node.Kind.AND) {
            return null;
        }
        List<Node> extra = subtract(whole.ops, part.kind == Node.Kind.AND ? part.ops : List.of(part));
        if (extra == null) {
            return null;
        }
        if (extra.isEmpty()) {
            return part;
        }
        Node extraNode = extra.size() == 1 ? extra.get(0) : new Node(Node.Kind.AND, null, false, extra);
        return and(or(swap ? not(cond) : cond, extraNode), part);
    }

    // cond ? thenExpr : (extra || thenExpr)  ->  (!cond && extra) || thenExpr     (swap=false)
    // cond ? (extra || elseExpr) : elseExpr  ->  (cond && extra) || elseExpr      (swap=true)
    private Node factorOr(Node cond, Node thenExpr, Node elseExpr, boolean swap) {
        Node whole = swap ? thenExpr : elseExpr;
        Node part = swap ? elseExpr : thenExpr;
        if (whole.kind != Node.Kind.OR) {
            return null;
        }
        List<Node> extra = subtract(whole.ops, part.kind == Node.Kind.OR ? part.ops : List.of(part));
        if (extra == null) {
            return null;
        }
        if (extra.isEmpty()) {
            return part;
        }
        Node extraNode = extra.size() == 1 ? extra.get(0) : new Node(Node.Kind.OR, null, false, extra);
        return or(and(swap ? cond : not(cond), extraNode), part);
    }

    // ----- normalized constructors -----

    private Node and(Node a, Node b) {
        if (a.kind == Node.Kind.FALSE || b.kind == Node.Kind.FALSE) {
            return Node.FALSE;
        }
        if (a.kind == Node.Kind.TRUE) {
            return b;
        }
        if (b.kind == Node.Kind.TRUE) {
            return a;
        }
        List<Node> ops = new ArrayList<>();
        addFlattened(ops, a, Node.Kind.AND);
        addFlattened(ops, b, Node.Kind.AND);
        return ops.size() == 1 ? ops.get(0) : new Node(Node.Kind.AND, null, false, ops);
    }

    private Node or(Node a, Node b) {
        if (a.kind == Node.Kind.TRUE || b.kind == Node.Kind.TRUE) {
            return Node.TRUE;
        }
        if (a.kind == Node.Kind.FALSE) {
            return b;
        }
        if (b.kind == Node.Kind.FALSE) {
            return a;
        }
        List<Node> ops = new ArrayList<>();
        addFlattened(ops, a, Node.Kind.OR);
        addFlattened(ops, b, Node.Kind.OR);
        return ops.size() == 1 ? ops.get(0) : new Node(Node.Kind.OR, null, false, ops);
    }

    private static void addFlattened(List<Node> into, Node n, Node.Kind kind) {
        if (n.kind == kind) {
            for (Node op : n.ops) {
                addUnique(into, op);
            }
        } else {
            addUnique(into, n);
        }
    }

    private static void addUnique(List<Node> into, Node n) {
        for (Node existing : into) {
            if (existing.eq(n)) {
                return;
            }
        }
        into.add(n);
    }

    private Node not(Node n) {
        switch (n.kind) {
            case TRUE:
                return Node.FALSE;
            case FALSE:
                return Node.TRUE;
            case LEAF:
                return Node.leaf(n.block, !n.negate);
            case AND: {
                Node result = Node.TRUE;
                for (Node op : n.ops) {
                    result = result == Node.TRUE ? not(op) : or(result, not(op));
                }
                return result;
            }
            case OR:
            default: {
                Node result = Node.FALSE;
                for (Node op : n.ops) {
                    result = result == Node.FALSE ? not(op) : and(result, not(op));
                }
                return result;
            }
        }
    }

    /** {@code whole} minus {@code part} as multisets, or null if {@code part} is not contained. */
    private static List<Node> subtract(List<Node> whole, List<Node> part) {
        List<Node> remaining = new ArrayList<>(whole);
        for (Node p : part) {
            boolean removed = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).eq(p)) {
                    remaining.remove(i);
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                return null;
            }
        }
        return remaining;
    }

    private static boolean sameSet(List<Node> a, List<Node> b) {
        if (a.size() != b.size()) {
            return false;
        }
        List<Node> rest = subtract(a, b);
        return rest != null && rest.isEmpty();
    }

    private Expression toExpression(Node n) {
        switch (n.kind) {
            case TRUE:
                return LiteralExpr.ofBoolean(true);
            case FALSE:
                return LiteralExpr.ofBoolean(false);
            case LEAF:
                return resolver.resolve(n.block, n.negate);
            case AND:
            case OR: {
                BinaryOperator op = n.kind == Node.Kind.AND ? BinaryOperator.AND : BinaryOperator.OR;
                Expression acc = toExpression(n.ops.get(0));
                for (int i = 1; i < n.ops.size(); i++) {
                    acc = new BinaryExpr(op, acc, toExpression(n.ops.get(i)), PrimitiveSourceType.BOOLEAN);
                }
                return acc;
            }
            default:
                return null;
        }
    }
}
