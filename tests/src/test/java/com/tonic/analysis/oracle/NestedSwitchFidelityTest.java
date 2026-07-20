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
 * Deterministic fidelity of a {@code switch} nested inside another switch's case, where each outer case's inner
 * switch falls through to the next outer case on no match (javac's layout for {@code switch(i){ case 0:
 * switch(j){...} case 1: switch(j){...} ... }} with no {@code break}). The method is decompiled and recompiled, then
 * the original and recovered method are executed over a fixed input set and must return identical results.
 *
 * <p>The mis-structuring this guards against nests the outer cases 1..n inside case 0's inner default, so a valid
 * {@code (i, j)} with {@code i >= 1} throws or returns the wrong value instead of the {@code i}-th row.
 */
class NestedSwitchFidelityTest {

    private static final int[][] INPUTS = {
            {0, 0}, {0, 1}, {0, 2}, {1, 0}, {1, 1}, {1, 2},
            {2, 0}, {2, 1}, {2, 2}, {0, 5}, {1, 7}, {2, 9}, {3, 0}, {-1, 0}
    };

    /**
     * {@code int g(int i, int j)} == a 3x3 table lookup: {@code switch(i){ case r: switch(j){ case c: return
     * 100*(r+1)+c; } // falls through } default: return -1; }}.
     */
    @Test
    void nestedSwitchFallthroughIsFaithful() throws Exception {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/NestedSwitch")
                .publicStaticMethod("g", "(II)I");
        Label row0 = mb.newLabel();
        Label row1 = mb.newLabel();
        Label row2 = mb.newLabel();
        Label dflt = mb.newLabel();
        Label[][] cell = new Label[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cell[r][c] = mb.newLabel();
            }
        }

        mb.iload(0).lookupswitch(Map.of(0, row0, 1, row1, 2, row2), dflt)
                .label(row0).iload(1)
                .lookupswitch(Map.of(0, cell[0][0], 1, cell[0][1], 2, cell[0][2]), row1)
                .label(row1).iload(1)
                .lookupswitch(Map.of(0, cell[1][0], 1, cell[1][1], 2, cell[1][2]), row2)
                .label(row2).iload(1)
                .lookupswitch(Map.of(0, cell[2][0], 1, cell[2][1], 2, cell[2][2]), dflt);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                mb.label(cell[r][c]).iconst(100 * (r + 1) + c).ireturn();
            }
        }
        mb.label(dflt).iconst(-1).ireturn();

        assertBehaviourPreserved(mb.build(), "g");
    }

    private static void assertBehaviourPreserved(ClassFile built, String name) throws Exception {
        byte[] bytes = built.write();
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);

        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, name + " must be recompilable");

        Method original = TestUtils.loadAndVerify(cf).getDeclaredMethod(name, int.class, int.class);
        Method recompiled = TestUtils.loadAndVerify(recovered).getDeclaredMethod(name, int.class, int.class);

        for (int[] in : INPUTS) {
            Object expected = original.invoke(null, in[0], in[1]);
            Object actual = recompiled.invoke(null, in[0], in[1]);
            assertEquals(expected, actual, name + "(" + in[0] + "," + in[1] + ") diverged after decompile+recompile");
        }
    }
}
