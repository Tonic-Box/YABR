package com.tonic.analysis.oracle;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Deterministic semantic fidelity of a {@code switch} nested inside a loop: the method is decompiled and
 * recompiled (the {@link Recompile#recompiledClone} round trip the oracle uses), then the original and the
 * recovered method are executed over a fixed input set and must return identical results. This is checked
 * directly rather than through the coverage-guided oracle because a trip-count-dependent divergence is not a
 * branch-coverage signal, so the fuzzer can miss it. Each fixture reproduces javac's exact control-flow
 * (verified against javac 11).
 *
 * <p>{@code breakOuter} exercises the current miscompile: a labeled break out of the loop from inside a case
 * is mis-structured into a fall-through plus an unconditional post-switch break, so the loop runs a single
 * iteration. {@code contFromCase} is a passing regression guard for the working shapes.
 */
class SwitchInLoopFidelityTest {

    private static final int[] INPUTS = {0, 1, 2, 3, 5, 10};

    /**
     * {@code outer: for (i=0;i<n;i++) switch(i){ case 0: sum+=1; break; case 2: break outer; default: sum+=100; }}
     */
    @Test
    void breakOuterFromCaseIsFaithful() throws Exception {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchInLoopBreakOuter")
                .publicStaticMethod("breakOuter", "(I)I");
        Label loopStart = mb.newLabel();
        Label loopEnd = mb.newLabel();
        Label case0 = mb.newLabel();
        Label case2 = mb.newLabel();
        Label defaultCase = mb.newLabel();
        Label afterSwitch = mb.newLabel();

        mb.iconst(0).istore(1)
                .iconst(0).istore(2)
                .label(loopStart)
                .iload(2).iload(0).if_icmpge(loopEnd)
                .iload(2).lookupswitch(Map.of(0, case0, 2, case2), defaultCase)
                .label(case0).iinc(1, 1).goto_(afterSwitch)
                .label(case2).goto_(loopEnd)
                .label(defaultCase).iinc(1, 100)
                .label(afterSwitch).iinc(2, 1).goto_(loopStart)
                .label(loopEnd).iload(1).ireturn();

        assertBehaviourPreserved(mb.build(), "breakOuter");
    }

    /**
     * {@code for (i=0;i<n;i++){ switch(i){ case 0: continue; case 1: sum+=10; break; default: sum+=100; } sum+=1; }}
     */
    @Test
    void continueFromCaseIsFaithful() throws Exception {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchInLoopContinue")
                .publicStaticMethod("contFromCase", "(I)I");
        Label loopStart = mb.newLabel();
        Label loopEnd = mb.newLabel();
        Label case0 = mb.newLabel();
        Label case1 = mb.newLabel();
        Label defaultCase = mb.newLabel();
        Label afterSwitch = mb.newLabel();
        Label contTarget = mb.newLabel();

        mb.iconst(0).istore(1)
                .iconst(0).istore(2)
                .label(loopStart)
                .iload(2).iload(0).if_icmpge(loopEnd)
                .iload(2).lookupswitch(Map.of(0, case0, 1, case1), defaultCase)
                .label(case0).goto_(contTarget)
                .label(case1).iinc(1, 10).goto_(afterSwitch)
                .label(defaultCase).iinc(1, 100)
                .label(afterSwitch).iinc(1, 1)
                .label(contTarget).iinc(2, 1).goto_(loopStart)
                .label(loopEnd).iload(1).ireturn();

        assertBehaviourPreserved(mb.build(), "contFromCase");
    }

    private static void assertBehaviourPreserved(ClassFile built, String name) throws Exception {
        byte[] bytes = built.write();
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);

        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, name + " must be recompilable");

        Method original = TestUtils.loadAndVerify(cf).getDeclaredMethod(name, int.class);
        Method recompiled = TestUtils.loadAndVerify(recovered).getDeclaredMethod(name, int.class);

        for (int n : INPUTS) {
            Object expected = original.invoke(null, n);
            Object actual = recompiled.invoke(null, n);
            assertEquals(expected, actual, name + "(" + n + ") diverged after decompile+recompile");
        }
    }
}
