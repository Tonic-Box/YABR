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
 * A {@code try { while (v != 0) {...; v = v / 2} } catch (...)} that wraps a NON-counted loop, with the loop
 * variable initialized to a non-default value in the prelude before the try. The loop is the entry of the
 * staged try-body region, so its pre-header (holding {@code v = seed}) is outside that region. Only a
 * for-induction counter's init must be re-realized at the loop (its declaration is skipped); a non-counter
 * init like {@code v = seed} was already emitted by the surrounding recovery, so it must NOT be re-emitted -
 * doing so duplicated it ({@code long v = seed; try { v = seed; while ... }}), which produced a broken frame
 * and a {@code VerifyError} on recompile. Asserts a single init and a recompile that verifies and executes.
 */
class NonCountedLoopInTryRecompileFidelityTest {

    private static final String SOURCE =
            "public class NonCountedLoop {\n"
            + "    long result;\n"
            + "    public long run(long seed) {\n"
            + "        long total = 0;\n"
            + "        long v = seed;\n"
            + "        try {\n"
            + "            while (v != 0) {\n"
            + "                if (v == 66) {\n"
            + "                    throw new IllegalStateException(\"x\");\n"
            + "                }\n"
            + "                total += v;\n"
            + "                v = v / 2;\n"
            + "            }\n"
            + "        } catch (RuntimeException e) {\n"
            + "            total = -1;\n"
            + "        }\n"
            + "        result = total;\n"
            + "        return total;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("non-counted-loop");
        Path src = dir.resolve("NonCountedLoop.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("NonCountedLoop.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "NonCountedLoop must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void theInitIsNotDuplicated() {
        int first = d1.indexOf("v = seed");
        assertTrue(first >= 0, "the loop variable must be initialized:\n" + d1);
        assertEquals(-1, d1.indexOf("v = seed", first + 1),
                "the non-counter loop init must appear exactly once, not be duplicated at the loop:\n" + d1);
    }

    @Test
    void recompilesVerifiesAndExecutes() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        // 40 + 20 + 10 + 5 + 2 + 1 = 78 (halving to zero, never equal to 66)
        assertEquals(78L, recompiledClass.getMethod("run", long.class).invoke(inst, 40L),
                "the recompiled method must verify and compute the halving sum");
        assertEquals(-1L, recompiledClass.getMethod("run", long.class).invoke(inst, 66L),
                "the throwing path is caught and yields -1");
    }
}
