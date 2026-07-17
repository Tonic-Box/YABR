package com.tonic.analysis.source.recovery.rcs;

/**
 * The reaching-condition boolean engine for one method's structuring pass. Every operation updates the
 * canonical {@link Bdd} and the syntactic {@link Nnf} in lockstep, so a {@link BoolFormula} is always
 * consistent across both layers. Equivalence, satisfiability and implication are answered by the BDD;
 * emission consumes the NNF. Switch selectors register their case atoms as a mutually-exclusive group
 * whose "at most one" constraint is folded into a domain formula, so reasoning that is only sound under
 * that domain (a switch's default guard, dead-combination pruning) uses the {@code *Given} variants.
 *
 * <p>Atoms are opaque non-negative integer indices here; mapping an index to a concrete branch/switch
 * predicate (and to a readable leaf {@code Expression}) is the caller's concern.
 */
public final class BoolFormulaFactory {

    private final BddFactory bdds = new BddFactory();
    private final NnfFactory nnfs = new NnfFactory();

    /** The constant true formula. */
    public final BoolFormula truth;

    /** The constant false formula. */
    public final BoolFormula falsity;

    /** Conjunction of every registered mutual-exclusion group's "at most one" constraint. */
    private Bdd domain;

    public BoolFormulaFactory() {
        this.truth = new BoolFormula(bdds.one, nnfs.trueNode);
        this.falsity = new BoolFormula(bdds.zero, nnfs.falseNode);
        this.domain = bdds.one;
    }

    /** The formula "atom {@code var} is true". */
    public BoolFormula atom(int var) {
        return new BoolFormula(bdds.atom(var), nnfs.leaf(var, false));
    }

    public BoolFormula and(BoolFormula a, BoolFormula b) {
        return wrap(bdds.and(a.bdd, b.bdd), nnfs.and(a.nnf, b.nnf));
    }

    public BoolFormula or(BoolFormula a, BoolFormula b) {
        return wrap(bdds.or(a.bdd, b.bdd), nnfs.or(a.nnf, b.nnf));
    }

    public BoolFormula not(BoolFormula a) {
        return wrap(bdds.not(a.bdd), nnfs.not(a.nnf));
    }

    /**
     * Pairs a BDD with an NNF, collapsing to the shared constant formula when the BDD has proven the
     * result constant - that keeps the emitted NNF from carrying a redundant tautology/contradiction.
     */
    private BoolFormula wrap(Bdd bdd, Nnf nnf) {
        if (bdd == bdds.one) {
            return truth;
        }
        if (bdd == bdds.zero) {
            return falsity;
        }
        return new BoolFormula(bdd, nnf);
    }

    /** True iff the two formulas denote the same boolean function (unconditionally). */
    public boolean equivalent(BoolFormula a, BoolFormula b) {
        return a.bdd == b.bdd;
    }

    public boolean isTautology(BoolFormula a) {
        return a.bdd == bdds.one;
    }

    public boolean isSatisfiable(BoolFormula a) {
        return a.bdd != bdds.zero;
    }

    public boolean implies(BoolFormula a, BoolFormula b) {
        return bdds.implies(a.bdd, b.bdd);
    }

    /** Registers {@code atoms} as the mutually-exclusive case atoms of one switch selector. */
    public void addMutualExclusion(int[] atoms) {
        domain = bdds.and(domain, bdds.atMostOne(atoms));
    }

    /** True iff {@code a} and {@code b} agree on every assignment allowed by the registered domains. */
    public boolean equivalentGiven(BoolFormula a, BoolFormula b) {
        return bdds.and(domain, a.bdd) == bdds.and(domain, b.bdd);
    }

    /** True iff some domain-consistent assignment satisfies {@code a}. */
    public boolean satisfiableGiven(BoolFormula a) {
        return bdds.and(domain, a.bdd) != bdds.zero;
    }

    /** True once the BDD node budget was exceeded; callers should emit from the NNF layer only. */
    public boolean overflowed() {
        return bdds.overflowed();
    }

    /** The canonical-true terminal, for callers walking a formula's BDD to emit minimized conditions. */
    Bdd bddOne() {
        return bdds.one;
    }

    /** The canonical-false terminal. */
    Bdd bddZero() {
        return bdds.zero;
    }

    /** Evaluates the syntactic (NNF) layer under an assignment indexed by atom. */
    public boolean evalSyntactic(BoolFormula f, boolean[] assignment) {
        return NnfFactory.eval(f.nnf, assignment);
    }

    /** Evaluates the canonical (BDD) layer under an assignment indexed by atom. */
    public boolean evalCanonical(BoolFormula f, boolean[] assignment) {
        Bdd b = f.bdd;
        while (!b.isTerminal()) {
            b = assignment[b.var] ? b.high : b.low;
        }
        return b == bdds.one;
    }
}
