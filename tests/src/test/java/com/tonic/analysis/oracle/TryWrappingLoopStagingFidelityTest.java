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
 * A {@code try { while (...) {...; i++} } catch (...)} that WRAPS a counted loop, with the counter declared
 * in the prelude before the try. The counter's init sits in a pre-header block OUTSIDE the (staged) try-body
 * region, so the loop emission must still realize the induction phi's forward-edge value there - otherwise
 * the counter's initial value is dropped and the {@code for} counter is left undeclared
 * ({@code for (; i < n; i++)} referencing an undeclared {@code i}), which fails to recompile. Asserts a
 * round-trip fixed point and correct execution on the normal and exceptional paths.
 */
class TryWrappingLoopStagingFidelityTest {

    private static final String SOURCE =
            "public class TryWrapLoop {\n"
            + "    public int run(int n) {\n"
            + "        int sum = 0;\n"
            + "        int i = 0;\n"
            + "        try {\n"
            + "            while (i < n) {\n"
            + "                if (i == 7) {\n"
            + "                    throw new IllegalStateException(\"seven\");\n"
            + "                }\n"
            + "                sum += i;\n"
            + "                i++;\n"
            + "            }\n"
            + "        } catch (RuntimeException e) {\n"
            + "            sum = -1;\n"
            + "        }\n"
            + "        return sum;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("try-wrap-loop");
        Path src = dir.resolve("TryWrapLoop.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("TryWrapLoop.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "TryWrapLoop must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void counterIsDeclaredAndRoundTripsFixed() {
        assertTrue(d1.contains("try"), "the try/catch must be preserved:\n" + d1);
        // The counter's init lives in a pre-header outside the staged try-body region; it must still be
        // realized so the for-counter is declared. The trailing `return` after the try/catch must stay a
        // continuation, not be pulled into the try body when the recompiler lays it out between the split
        // protected ranges. Together these make the shape a round-trip fixed point.
        assertTrue(d1.contains("for (int i = 0"),
                "the loop counter must be declared in the for-init:\n" + d1);
        assertEquals(d1, d2, "a try wrapping a counted loop, with a trailing continuation, must be a round-trip fixed point");
    }

    @Test
    void bothPathsExecuteEquivalently() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        // sum 0..4 = 10, no i == 7 reached
        assertEquals(10, recompiledClass.getMethod("run", int.class).invoke(inst, 5),
                "the normal path sums 0..4");
        // i reaches 7 -> throws -> catch sets sum to -1
        assertEquals(-1, recompiledClass.getMethod("run", int.class).invoke(inst, 20),
                "the loop throws at i == 7 and the catch sets sum to -1");
    }
}
