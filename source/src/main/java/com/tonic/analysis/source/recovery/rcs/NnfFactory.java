package com.tonic.analysis.source.recovery.rcs;

import java.util.*;

/**
 * Builds and interns {@link Nnf} nodes for one method's structuring pass. {@code and}/{@code or}
 * flatten operands of the same kind, drop identity/absorbing constants, and de-duplicate by identity
 * (safe because every node is interned); {@code not} pushes negation to the leaves via De Morgan.
 * Operand lists are kept in ascending id order so a compound node is canonical up to commutativity,
 * which makes interning and de-duplication exact.
 */
final class NnfFactory {

    final Nnf trueNode = new Nnf(Nnf.Kind.TRUE, 0, false, null, 0);
    final Nnf falseNode = new Nnf(Nnf.Kind.FALSE, 0, false, null, 1);

    private final Map<Long, Nnf> leaves = new HashMap<>();
    private final Map<String, Nnf> compounds = new HashMap<>();
    private int nextId = 2;

    Nnf leaf(int atom, boolean negate) {
        long key = (((long) atom) << 1) | (negate ? 1 : 0);
        Nnf existing = leaves.get(key);
        if (existing != null) {
            return existing;
        }
        Nnf node = new Nnf(Nnf.Kind.LEAF, atom, negate, null, nextId++);
        leaves.put(key, node);
        return node;
    }

    Nnf and(Nnf a, Nnf b) {
        if (a == falseNode || b == falseNode) {
            return falseNode;
        }
        if (a == trueNode) {
            return b;
        }
        if (b == trueNode) {
            return a;
        }
        Set<Nnf> operands = new LinkedHashSet<>();
        flatten(operands, a, Nnf.Kind.AND);
        flatten(operands, b, Nnf.Kind.AND);
        return compound(Nnf.Kind.AND, operands);
    }

    Nnf or(Nnf a, Nnf b) {
        if (a == trueNode || b == trueNode) {
            return trueNode;
        }
        if (a == falseNode) {
            return b;
        }
        if (b == falseNode) {
            return a;
        }
        Set<Nnf> operands = new LinkedHashSet<>();
        flatten(operands, a, Nnf.Kind.OR);
        flatten(operands, b, Nnf.Kind.OR);
        return compound(Nnf.Kind.OR, operands);
    }

    Nnf not(Nnf n) {
        switch (n.kind) {
            case TRUE:
                return falseNode;
            case FALSE:
                return trueNode;
            case LEAF:
                return leaf(n.atom, !n.negate);
            case AND: {
                Nnf result = falseNode;
                for (Nnf op : n.ops) {
                    result = or(result, not(op));
                }
                return result;
            }
            case OR:
            default: {
                Nnf result = trueNode;
                for (Nnf op : n.ops) {
                    result = and(result, not(op));
                }
                return result;
            }
        }
    }

    private static void flatten(Set<Nnf> into, Nnf n, Nnf.Kind kind) {
        if (n.kind == kind) {
            into.addAll(n.ops);
        } else {
            into.add(n);
        }
    }

    private Nnf compound(Nnf.Kind kind, Set<Nnf> operands) {
        if (operands.size() == 1) {
            return operands.iterator().next();
        }
        List<Nnf> ops = new ArrayList<>(operands);
        ops.sort(Comparator.comparingInt(x -> x.id));
        StringBuilder key = new StringBuilder(kind == Nnf.Kind.AND ? "&" : "|");
        for (Nnf op : ops) {
            key.append(op.id).append(',');
        }
        String k = key.toString();
        Nnf existing = compounds.get(k);
        if (existing != null) {
            return existing;
        }
        Nnf node = new Nnf(kind, 0, false, List.copyOf(ops), nextId++);
        compounds.put(k, node);
        return node;
    }

    /** Evaluates {@code n} under an assignment indexed by atom. For tests and equivalence checks. */
    static boolean eval(Nnf n, boolean[] assignment) {
        switch (n.kind) {
            case TRUE:
                return true;
            case FALSE:
                return false;
            case LEAF:
                return assignment[n.atom] ^ n.negate;
            case AND:
                for (Nnf op : n.ops) {
                    if (!eval(op, assignment)) {
                        return false;
                    }
                }
                return true;
            case OR:
            default:
                for (Nnf op : n.ops) {
                    if (eval(op, assignment)) {
                        return true;
                    }
                }
                return false;
        }
    }
}
