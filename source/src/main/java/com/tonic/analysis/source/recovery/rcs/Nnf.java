package com.tonic.analysis.source.recovery.rcs;

import java.util.List;

/**
 * A node of a negation-normal-form boolean formula over integer atoms.
 */
final class Nnf
{

    enum Kind { TRUE, FALSE, LEAF, AND, OR }

    final Kind kind;

    /**
     * LEAF: the atom index.
     */
    final int atom;

    /**
     * LEAF: whether the atom is negated.
     */
    final boolean negate;

    /**
     * AND/OR: operands, held in ascending {@link #id} order so the node is a canonical set.
     */
    final List<Nnf> ops;

    /**
     * Dense identifier assigned by the owning factory.
     */
    final int id;

    Nnf(Kind kind, int atom, boolean negate, List<Nnf> ops, int id)
    {
        this.kind = kind;
        this.atom = atom;
        this.negate = negate;
        this.ops = ops;
        this.id = id;
    }

    boolean isConstant()
    {
        return kind == Kind.TRUE || kind == Kind.FALSE;
    }
}
