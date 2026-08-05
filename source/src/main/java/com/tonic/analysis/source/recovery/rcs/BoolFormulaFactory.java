package com.tonic.analysis.source.recovery.rcs;

/**
 * The reaching-condition boolean engine for one method's structuring pass. Every operation updates the
 * canonical {@link Bdd} and the syntactic {@link Nnf} in lockstep, so a {@link BoolFormula} is always
 * consistent across both layers. Equivalence, satisfiability and implication are answered by the BDD;
 * emission consumes the NNF. Switch selectors register their case atoms as a mutually-exclusive group
 * whose "at most one" constraint is folded into a domain formula, so reasoning that is only sound under
 * that domain (a switch's default guard, dead-combination pruning) uses the {@code *Given} variants.
 *
 * Atoms are opaque non-negative integer indices here; mapping an index to a concrete branch/switch
 * predicate (and to a readable leaf {@code Expression}) is the caller's concern.
 */
public final class BoolFormulaFactory
{

    private final BddFactory bdds = new BddFactory();
    private final NnfFactory nnfs = new NnfFactory();

    /**
     * The constant true formula.
     */
    public final BoolFormula truth;

    /**
     * The constant false formula.
     */
    public final BoolFormula falsity;

    /**
     * Conjunction of every registered mutual-exclusion group's "at most one" constraint.
     */
    private Bdd domain;

    /**
     * Creates an engine with fresh BDD and NNF factories and an unconstrained domain.
     */
    public BoolFormulaFactory()
    {
        this.truth = new BoolFormula(bdds.one, nnfs.trueNode);
        this.falsity = new BoolFormula(bdds.zero, nnfs.falseNode);
        this.domain = bdds.one;
    }

    /**
     * Builds the positive-literal formula for one atom.
     *
     * @param var non-negative atom index
     * @return the formula asserting that atom
     */
    public BoolFormula atom(int var)
    {
        return new BoolFormula(bdds.atom(var), nnfs.leaf(var, false));
    }

    /**
     * Conjoins two formulas in both the BDD and NNF layers.
     *
     * @param a the left operand
     * @param b the right operand
     * @return the conjunction, collapsed to a constant when the BDD proves it constant
     */
    public BoolFormula and(BoolFormula a, BoolFormula b)
    {
        return wrap(bdds.and(a.bdd, b.bdd), nnfs.and(a.nnf, b.nnf));
    }

    /**
     * Disjoins two formulas in both the BDD and NNF layers.
     *
     * @param a the left operand
     * @param b the right operand
     * @return the disjunction, collapsed to a constant when the BDD proves it constant
     */
    public BoolFormula or(BoolFormula a, BoolFormula b)
    {
        return wrap(bdds.or(a.bdd, b.bdd), nnfs.or(a.nnf, b.nnf));
    }

    /**
     * Negates a formula in both the BDD and NNF layers.
     *
     * @param a the operand
     * @return the negation, collapsed to a constant when the BDD proves it constant
     */
    public BoolFormula not(BoolFormula a)
    {
        return wrap(bdds.not(a.bdd), nnfs.not(a.nnf));
    }

    /**
     * Pairs a BDD with an NNF, collapsing to the shared constant formula when the BDD has proven the
     * result constant - that keeps the emitted NNF from carrying a redundant tautology/contradiction.
     */
    private BoolFormula wrap(Bdd bdd, Nnf nnf)
    {
        if (bdd == bdds.one)
        {
            return truth;
        }
        if (bdd == bdds.zero)
        {
            return falsity;
        }
        return new BoolFormula(bdd, nnf);
    }

    /**
     * Tests equality of the two canonical BDDs, ignoring the registered domains.
     *
     * @param a the left operand
     * @param b the right operand
     * @return true if both denote the same boolean function
     */
    public boolean equivalent(BoolFormula a, BoolFormula b)
    {
        return a.bdd == b.bdd;
    }

    /**
     * Tests whether a formula holds under every assignment, ignoring the registered domains.
     *
     * @param a the formula
     * @return true if the formula is unconditionally true
     */
    public boolean isTautology(BoolFormula a)
    {
        return a.bdd == bdds.one;
    }

    /**
     * Tests whether a formula holds under some assignment, ignoring the registered domains.
     *
     * @param a the formula
     * @return true if the formula is not unconditionally false
     */
    public boolean isSatisfiable(BoolFormula a)
    {
        return a.bdd != bdds.zero;
    }

    /**
     * Tests entailment, ignoring the registered domains.
     *
     * @param a the antecedent
     * @param b the consequent
     * @return true if every assignment satisfying a satisfies b
     */
    public boolean implies(BoolFormula a, BoolFormula b)
    {
        return bdds.implies(a.bdd, b.bdd);
    }

    /**
     * Folds one switch selector's "at most one case holds" constraint into the domain.
     *
     * @param atoms the selector's case atom indices
     */
    public void addMutualExclusion(int[] atoms)
    {
        domain = bdds.and(domain, bdds.atMostOne(atoms));
    }

    /**
     * Tests equivalence restricted to the assignments the registered domains allow.
     *
     * @param a the left operand
     * @param b the right operand
     * @return true if both agree on every domain-consistent assignment
     */
    public boolean equivalentGiven(BoolFormula a, BoolFormula b)
    {
        return bdds.and(domain, a.bdd) == bdds.and(domain, b.bdd);
    }

    /**
     * Tests satisfiability restricted to the assignments the registered domains allow.
     *
     * @param a the formula
     * @return true if some domain-consistent assignment satisfies it
     */
    public boolean satisfiableGiven(BoolFormula a)
    {
        return bdds.and(domain, a.bdd) != bdds.zero;
    }

    /**
     * Reports whether the BDD node budget was exceeded, after which callers must emit from the
     * NNF layer only.
     *
     * @return true once the budget was exceeded
     */
    public boolean overflowed()
    {
        return bdds.overflowed();
    }

    /**
     * The canonical-true terminal, for callers walking a formula's BDD to emit minimized conditions.
     */
    Bdd bddOne()
    {
        return bdds.one;
    }

    /**
     * The canonical-false terminal.
     */
    Bdd bddZero()
    {
        return bdds.zero;
    }

    /**
     * Evaluates the syntactic NNF layer.
     *
     * @param f the formula
     * @param assignment truth values indexed by atom
     * @return the value of the NNF under that assignment
     */
    public boolean evalSyntactic(BoolFormula f, boolean[] assignment)
    {
        return NnfFactory.eval(f.nnf, assignment);
    }

    /**
     * Evaluates the canonical BDD layer by walking from its root to a terminal.
     *
     * @param f the formula
     * @param assignment truth values indexed by atom
     * @return the value of the BDD under that assignment
     */
    public boolean evalCanonical(BoolFormula f, boolean[] assignment)
    {
        Bdd b = f.bdd;
        while (!b.isTerminal())
        {
            b = assignment[b.var] ? b.high : b.low;
        }
        return b == bdds.one;
    }
}
