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
 * Deterministic fidelity of a {@code switch} enclosed in an {@code if} whose merge the switch cases share: the
 * post-switch join is reached both from every breaking case and from the {@code if}'s skip edge, so it is dominated
 * by the {@code if} rather than the switch header. The method is decompiled and recompiled, then the original and
 * recovered method are executed over a fixed input set and must return identical results.
 */
class SwitchInIfFidelityTest {

    private static final int[][] INPUTS = {
            {0, 0}, {0, 1}, {0, 2}, {0, 5}, {1, 0}, {1, 1}, {1, 2}, {1, 5}
    };

    /**
     * {@code int h(int cond, int x)} == {@code int r = 0; if (cond != 0) { switch (x) { case 0: r = 10; ... default:
     * r = 19; } } return r + 100;} - the switch's break target and the {@code if}'s skip edge meet at the same block.
     */
    @Test
    void switchInsideIfSharesMerge() throws Exception {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("com/test/SwitchInIf")
                .publicStaticMethod("h", "(II)I");
        Label merge = mb.newLabel();
        Label c0 = mb.newLabel();
        Label c1 = mb.newLabel();
        Label c2 = mb.newLabel();
        Label cd = mb.newLabel();

        mb.iconst(0).istore(2)
                .iload(0).ifeq(merge)
                .iload(1).lookupswitch(Map.of(0, c0, 1, c1, 2, c2), cd)
                .label(c0).iconst(10).istore(2).goto_(merge)
                .label(c1).iconst(11).istore(2).goto_(merge)
                .label(c2).iconst(12).istore(2).goto_(merge)
                .label(cd).iconst(19).istore(2).goto_(merge)
                .label(merge).iload(2).iconst(100).iadd().ireturn();

        assertBehaviourPreserved(mb.build(), "h");
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
