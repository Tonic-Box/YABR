package com.tonic.analysis.oracle;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Recompile fidelity of a loop with interdependent loop-carried variables - one's update reads the other's
 * value before it is itself updated: {@code a = a + b; b = b * 2}. Phi elimination inserts a back-edge copy
 * per loop-carried variable; batching them at the block end left both computed results on the operand stack,
 * so they were stored in reverse (LIFO) order and the re-decompile read that literally, swapping the body to
 * {@code b = b * 2; a = a + b} (a wrong-looking, non-idempotent, re-recompile-miscompiling order). The copy
 * whose source reads the OTHER loop-carried phi is now placed right after its definition so it stores in
 * computation order. Asserts a round-trip fixed point, the preserved body order, and correct execution.
 */
class InterdependentLoopVarsRecompileFidelityTest {

    private static final String SOURCE =
            "public class Inter {\n"
            + "    public long run(int n) {\n"
            + "        long a = 0;\n"
            + "        long b = 1;\n"
            + "        for (int i = 0; i < n; i++) {\n"
            + "            a = a + b;\n"
            + "            b = b * 2;\n"
            + "        }\n"
            + "        return a;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("interdependent-loop");
        Path src = dir.resolve("Inter.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("Inter.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "Inter must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void bodyOrderIsPreservedAndRoundTripsFixed() {
        int add = d1.indexOf("a = a + b");
        int mul = d1.indexOf("b = b * 2");
        assertTrue(add >= 0 && mul >= 0 && add < mul,
                "the loop body must accumulate before doubling, in that order:\n" + d1);
        assertEquals(d1, d2, "a loop with interdependent loop-carried updates must be a round-trip fixed point");
    }

    @Test
    void executesEquivalently() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        // a += b then b *= 2, b starting at 1: after n iterations a = 1 + 2 + 4 + ... + 2^(n-1) = 2^n - 1
        assertEquals(7L, recompiledClass.getMethod("run", int.class).invoke(inst, 3),
                "3 iterations: 1 + 2 + 4 = 7");
        assertEquals(1023L, recompiledClass.getMethod("run", int.class).invoke(inst, 10),
                "10 iterations: 2^10 - 1 = 1023");
    }
}
