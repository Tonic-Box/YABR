package com.tonic.analysis.oracle;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end fidelity of a try-with-resources statement through the full pipeline. javac desugars
 * {@code try (r) { body }} into a normal-path {@code r.close()} plus a cleanup handler that closes and chains
 * suppressed exceptions via {@code Throwable.addSuppressed}; the decompiler folds that back into concise
 * try-with-resources ({@code SomeType r = ...; try (r) { ... }}). This test compiles such a class with javac,
 * decompiles it, recompiles the decompiled source, and asserts BOTH that it is a round-trip fixed point (the
 * recompiled class decompiles identically) AND that execution is preserved - specifically that the resource is
 * still closed, which a recompiler that drops the resource management would silently break.
 *
 * <p>The fixture is the resource itself: {@code close()} increments a static counter that {@code closeCount()}
 * reports, kept separate from {@code f()}'s return value so the test does not depend on whether the close runs
 * before or after a returned value is read - a recompile that drops the close is caught by {@code f()} still
 * running while {@code closeCount()} stays 0.
 */
class TryWithResourcesFidelityTest {

    private static final String SOURCE =
            "public class TwrFixture implements AutoCloseable {\n"
            + "    static int closes;\n"
            + "    public void close() { closes += 1; }\n"
            + "    public static int f() {\n"
            + "        closes = 0;\n"
            + "        TwrFixture r = new TwrFixture();\n"
            + "        try (r) {\n"
            + "            return 42;\n"
            + "        }\n"
            + "    }\n"
            + "    public static int closeCount() { return closes; }\n"
            + "}\n";

    @Test
    void conciseTryWithResourcesRoundTripsAndCloses() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("twr-fidelity");
        Path src = dir.resolve("TwrFixture.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        byte[] bytes = Files.readAllBytes(dir.resolve("TwrFixture.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.contains("try ("), "decompiler should fold to try-with-resources:\n" + d1);

        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "the try-with-resources method must be recompilable");

        String d2 = ClassDecompiler.decompile(recovered);
        assertEquals(d1, d2, "try-with-resources must be a round-trip fixed point");

        Class<?> recompiledClass = TestUtils.loadAndVerify(recovered);
        Method f = recompiledClass.getDeclaredMethod("f");
        Method closeCount = recompiledClass.getDeclaredMethod("closeCount");
        assertEquals(42, f.invoke(null), "recompiled method must return the same value");
        assertEquals(1, closeCount.invoke(null), "the resource must still be closed after decompile+recompile");
    }
}
