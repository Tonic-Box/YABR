package com.tonic.analysis.source.recovery.rcs;

/**
 * A reaching-condition boolean formula carried as two representations kept in sync by
 * {@link BoolFormulaFactory}: a canonical {@link Bdd} for reasoning (equivalence, satisfiability,
 * implication - all cheap because the BDD is reduced and interned) and a syntactic {@link Nnf} for
 * emission (it preserves the short-circuit shape the structurer built, which the variable-ordered BDD
 * does not). Reasoning reads {@code bdd}; readable {@code &&}/{@code ||} emission reads {@code nnf}.
 */
public final class BoolFormula {

    final Bdd bdd;
    final Nnf nnf;

    BoolFormula(Bdd bdd, Nnf nnf) {
        this.bdd = bdd;
        this.nnf = nnf;
    }
}
