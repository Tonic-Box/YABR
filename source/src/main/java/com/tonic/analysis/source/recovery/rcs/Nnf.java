package com.tonic.analysis.source.recovery.rcs;

import java.util.List;

/**
 * A node of a negation-normal-form boolean formula over integer atoms: {@code AND}/{@code OR} are
 * flattened n-ary nodes and negation lives only on {@code LEAF}s. This is the syntactic layer that
 * preserves the short-circuit shape built during structuring (the {@link Bdd} layer is canonical but
 * reorders by variable and loses that shape). {@link NnfFactory} interns every node, so structurally
 * equal formulas are the same object and operand de-duplication is an identity check.
 *
 * <p>This is the same algebra as {@code CompoundConditionBuilder.Node}, generalized from an
 * {@code IRBlock} leaf to an integer atom so it can carry any predicate.
 */
final class Nnf {

    enum Kind { TRUE, FALSE, LEAF, AND, OR }

    final Kind kind;

    /** LEAF: the atom index. */
    final int atom;

    /** LEAF: whether the atom is negated. */
    final boolean negate;

    /** AND/OR: operands, held in ascending {@link #id} order so the node is a canonical set. */
    final List<Nnf> ops;

    /** Dense identifier assigned by the owning factory. */
    final int id;

    Nnf(Kind kind, int atom, boolean negate, List<Nnf> ops, int id) {
        this.kind = kind;
        this.atom = atom;
        this.negate = negate;
        this.ops = ops;
        this.id = id;
    }

    boolean isConstant() {
        return kind == Kind.TRUE || kind == Kind.FALSE;
    }
}
