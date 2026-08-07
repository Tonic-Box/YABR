package com.tonic.analysis.source.recovery.rcs;

/**
 * A reaching-condition boolean formula carried as two representations kept in sync by {@link
 * BoolFormulaFactory}.
 */
public final class BoolFormula
{

    final Bdd bdd;
    final Nnf nnf;

    BoolFormula(Bdd bdd, Nnf nnf)
    {
        this.bdd = bdd;
        this.nnf = nnf;
    }
}
