package com.tonic.analysis.source.recovery.rcs;

/**
 * A node of a reduced, ordered, hash-consed binary decision diagram (ROBDD) over boolean variables
 * identified by a non-negative {@code var} index (a lower index sits higher in the diagram). Because
 * {@link BddFactory} interns every node and never creates a node whose branches are identical, two
 * formulas are logically equivalent iff they are the same object - equivalence is reference identity.
 */
final class Bdd {

    /** Variable index tested at this node; {@link BddFactory#TERMINAL_VAR} for the two terminals. */
    final int var;

    /** Subdiagram taken when {@link #var} is false; {@code null} for a terminal. */
    final Bdd low;

    /** Subdiagram taken when {@link #var} is true; {@code null} for a terminal. */
    final Bdd high;

    /** Dense identifier assigned by the owning factory; terminals are 0 (zero) and 1 (one). */
    final int id;

    Bdd(int var, Bdd low, Bdd high, int id) {
        this.var = var;
        this.low = low;
        this.high = high;
        this.id = id;
    }

    boolean isTerminal() {
        return low == null;
    }
}
