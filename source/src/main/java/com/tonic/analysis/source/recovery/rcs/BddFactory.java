package com.tonic.analysis.source.recovery.rcs;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds and interns {@link Bdd} nodes for one method's structuring pass. All logical operations are
 * expressed through a single memoized {@code ite} (if-then-else) so the reduced-ordered invariant and
 * the apply cache are maintained in one place; {@code and}/{@code or}/{@code not}/{@code xor} are thin
 * wrappers. Node identifiers are packed into the unique-table and cache keys, so the factory refuses to
 * grow past {@link #MAX_ID} nodes and reports {@link #overflowed()} instead - the caller then falls back
 * to the syntactic layer (still correct, only less simplified) rather than producing a wrong key.
 */
final class BddFactory {

    /** Variable index of the two terminals; larger than any real variable so terminals sort last. */
    static final int TERMINAL_VAR = Integer.MAX_VALUE;

    /** Node-count ceiling. Ids must stay below 2^21 so three of them pack into a signed long key. */
    private static final int MAX_ID = 1 << 21;
    private static final long ID_MASK = MAX_ID - 1;

    final Bdd zero = new Bdd(TERMINAL_VAR, null, null, 0);
    final Bdd one = new Bdd(TERMINAL_VAR, null, null, 1);

    private final Map<Long, Bdd> unique = new HashMap<>();
    private final Map<Long, Bdd> iteCache = new HashMap<>();
    private int nextId = 2;
    private boolean overflowed;

    /** True once the node budget was hit; results after this point may be under-reduced. */
    boolean overflowed() {
        return overflowed;
    }

    /** The reduced, interned node testing {@code var} with the given branches. */
    Bdd mk(int var, Bdd low, Bdd high) {
        if (low == high) {
            return low;
        }
        long key = (((long) var) << 42) | (((long) low.id) << 21) | high.id;
        Bdd existing = unique.get(key);
        if (existing != null) {
            return existing;
        }
        if (nextId >= MAX_ID) {
            overflowed = true;
            return low;
        }
        Bdd node = new Bdd(var, low, high, nextId++);
        unique.put(key, node);
        return node;
    }

    /** The formula "variable {@code var} is true". */
    Bdd atom(int var) {
        return mk(var, zero, one);
    }

    Bdd ite(Bdd f, Bdd g, Bdd h) {
        if (f == one) {
            return g;
        }
        if (f == zero) {
            return h;
        }
        if (g == h) {
            return g;
        }
        if (g == one && h == zero) {
            return f;
        }
        long key = (((long) f.id) << 42) | (((long) g.id) << 21) | (h.id & ID_MASK);
        Bdd cached = iteCache.get(key);
        if (cached != null) {
            return cached;
        }
        int v = Math.min(f.var, Math.min(g.var, h.var));
        Bdd lo = ite(cofactor(f, v, false), cofactor(g, v, false), cofactor(h, v, false));
        Bdd hi = ite(cofactor(f, v, true), cofactor(g, v, true), cofactor(h, v, true));
        Bdd result = mk(v, lo, hi);
        iteCache.put(key, result);
        return result;
    }

    /** The branch of {@code n} taken when variable {@code v} has the given value ({@code n} unchanged if it does not test {@code v}). */
    private static Bdd cofactor(Bdd n, int v, boolean value) {
        if (!n.isTerminal() && n.var == v) {
            return value ? n.high : n.low;
        }
        return n;
    }

    Bdd and(Bdd f, Bdd g) {
        return ite(f, g, zero);
    }

    Bdd or(Bdd f, Bdd g) {
        return ite(f, one, g);
    }

    Bdd not(Bdd f) {
        return ite(f, zero, one);
    }

    Bdd xor(Bdd f, Bdd g) {
        return ite(f, not(g), g);
    }

    boolean implies(Bdd a, Bdd b) {
        return or(not(a), b) == one;
    }

    boolean isSat(Bdd f) {
        return f != zero;
    }

    boolean isTautology(Bdd f) {
        return f == one;
    }

    /** Cofactor of {@code f} by the assignment {@code var := value} (Shannon restriction). */
    Bdd restrict(Bdd f, int var, boolean value) {
        if (f.isTerminal() || f.var > var) {
            return f;
        }
        if (f.var == var) {
            return value ? f.high : f.low;
        }
        return mk(f.var, restrict(f.low, var, value), restrict(f.high, var, value));
    }

    /** The formula that is true when at most one of {@code vars} is true (the domain of a switch selector). */
    Bdd atMostOne(int[] vars) {
        Bdd result = one;
        for (int i = 0; i < vars.length; i++) {
            for (int j = i + 1; j < vars.length; j++) {
                result = and(result, not(and(atom(vars[i]), atom(vars[j]))));
            }
        }
        return result;
    }
}
