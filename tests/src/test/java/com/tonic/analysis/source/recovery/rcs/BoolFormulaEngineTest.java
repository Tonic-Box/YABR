package com.tonic.analysis.source.recovery.rcs;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate for the reaching-condition boolean engine. Cross-checks both layers of {@link BoolFormula}
 * against ground-truth boolean semantics over exhaustive assignments: the BDD layer must be canonical
 * (equivalence = identity), the NNF layer must denote the same function, and switch mutual-exclusion
 * must prune impossible case combinations under the registered domain.
 */
class BoolFormulaEngineTest {

    /** A built formula paired with its reference boolean function over an assignment array. */
    private static final class Built {
        final BoolFormula f;
        final Predicate<boolean[]> ref;

        Built(BoolFormula f, Predicate<boolean[]> ref) {
            this.f = f;
            this.ref = ref;
        }
    }

    private Built build(BoolFormulaFactory ff, Random rnd, int atoms, int depth) {
        if (depth <= 0 || rnd.nextInt(100) < 35) {
            int v = rnd.nextInt(atoms);
            boolean neg = rnd.nextBoolean();
            BoolFormula leaf = neg ? ff.not(ff.atom(v)) : ff.atom(v);
            return new Built(leaf, a -> a[v] ^ neg);
        }
        Built x = build(ff, rnd, atoms, depth - 1);
        Built y = build(ff, rnd, atoms, depth - 1);
        switch (rnd.nextInt(3)) {
            case 0:
                return new Built(ff.and(x.f, y.f), a -> x.ref.test(a) && y.ref.test(a));
            case 1:
                return new Built(ff.or(x.f, y.f), a -> x.ref.test(a) || y.ref.test(a));
            default:
                return new Built(ff.not(x.f), a -> !x.ref.test(a));
        }
    }

    /** Runs {@code body} for every assignment over {@code atoms} boolean atoms. */
    private void forEachAssignment(int atoms, java.util.function.Consumer<boolean[]> body) {
        boolean[] a = new boolean[atoms];
        for (int mask = 0; mask < (1 << atoms); mask++) {
            for (int i = 0; i < atoms; i++) {
                a[i] = (mask & (1 << i)) != 0;
            }
            body.accept(a);
        }
    }

    @Test
    void bothLayersMatchBooleanSemanticsOnEveryAssignment() {
        BoolFormulaFactory ff = new BoolFormulaFactory();
        Random rnd = new Random(0xC0FFEEL);
        int atoms = 5;
        for (int iter = 0; iter < 2000; iter++) {
            Built b = build(ff, rnd, atoms, 5);
            forEachAssignment(atoms, a -> {
                boolean expected = b.ref.test(a);
                assertEquals(expected, ff.evalCanonical(b.f, a), "BDD layer diverged from reference");
                assertEquals(expected, ff.evalSyntactic(b.f, a), "NNF layer diverged from reference");
            });
        }
    }

    @Test
    void bddEquivalenceIsExactlyAgreementOnAllAssignments() {
        BoolFormulaFactory ff = new BoolFormulaFactory();
        Random rnd = new Random(0x1234L);
        int atoms = 5;
        for (int iter = 0; iter < 3000; iter++) {
            Built x = build(ff, rnd, atoms, 4);
            Built y = build(ff, rnd, atoms, 4);
            boolean agreeEverywhere = agreeOnAll(atoms, x, y);
            assertEquals(agreeEverywhere, ff.equivalent(x.f, y.f),
                    "canonical equivalence must match all-assignment agreement");
        }
    }

    private boolean agreeOnAll(int atoms, Built x, Built y) {
        boolean[] a = new boolean[atoms];
        for (int mask = 0; mask < (1 << atoms); mask++) {
            for (int i = 0; i < atoms; i++) {
                a[i] = (mask & (1 << i)) != 0;
            }
            if (x.ref.test(a) != y.ref.test(a)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void constantsSatisfiabilityAndImplication() {
        BoolFormulaFactory ff = new BoolFormulaFactory();
        BoolFormula a = ff.atom(0);
        BoolFormula b = ff.atom(1);

        assertTrue(ff.isTautology(ff.or(a, ff.not(a))));
        assertFalse(ff.isSatisfiable(ff.and(a, ff.not(a))));
        assertTrue(ff.isSatisfiable(ff.and(a, b)));

        // De Morgan, distribution, absorption are all identities under canonical equivalence.
        assertTrue(ff.equivalent(ff.not(ff.and(a, b)), ff.or(ff.not(a), ff.not(b))));
        assertTrue(ff.equivalent(ff.and(a, ff.or(b, ff.atom(2))),
                ff.or(ff.and(a, b), ff.and(a, ff.atom(2)))));
        assertTrue(ff.equivalent(ff.or(a, ff.and(a, b)), a));

        assertTrue(ff.implies(ff.and(a, b), a));
        assertTrue(ff.implies(a, ff.or(a, b)));
        assertFalse(ff.implies(a, b));
    }

    @Test
    void mutualExclusionPrunesImpossibleCaseCombinations() {
        BoolFormulaFactory ff = new BoolFormulaFactory();
        int c0 = 0;
        int c1 = 1;
        int c2 = 2;
        ff.addMutualExclusion(new int[]{c0, c1, c2});

        BoolFormula case0 = ff.atom(c0);
        BoolFormula case1 = ff.atom(c1);
        BoolFormula bothCases = ff.and(case0, case1);

        // Two distinct cases of one selector can never both hold under the domain.
        assertFalse(ff.satisfiableGiven(bothCases), "two switch cases cannot both be taken");
        assertTrue(ff.equivalentGiven(bothCases, ff.falsity));

        // A single case, and the default (no case), remain reachable.
        assertTrue(ff.satisfiableGiven(case0));
        BoolFormula dflt = ff.and(ff.and(ff.not(case0), ff.not(case1)), ff.not(ff.atom(c2)));
        assertTrue(ff.satisfiableGiven(dflt), "the default edge must stay reachable");

        // Without the domain, the same conjunction is still (abstractly) satisfiable.
        assertTrue(ff.isSatisfiable(bothCases));
    }

    @Test
    void largeConjunctionStaysCanonicalAndCheap() {
        BoolFormulaFactory ff = new BoolFormulaFactory();
        BoolFormula acc = ff.truth;
        for (int i = 0; i < 40; i++) {
            acc = ff.and(acc, ff.atom(i));
        }
        // Order of conjunction must not matter (canonical form).
        BoolFormula rev = ff.truth;
        for (int i = 39; i >= 0; i--) {
            rev = ff.and(rev, ff.atom(i));
        }
        assertTrue(ff.equivalent(acc, rev));
        assertFalse(ff.overflowed(), "a 40-atom conjunction must not exhaust the node budget");
        assertTrue(ff.isSatisfiable(acc));
        assertFalse(ff.isTautology(acc));
    }
}
