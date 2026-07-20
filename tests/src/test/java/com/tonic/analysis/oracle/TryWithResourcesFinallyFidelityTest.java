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
 * Fidelity of a try-with-resources statement that carries a user {@code finally} clause. javac compiles
 * {@code try (r) { body } finally { f }} as the resource-management desugaring wrapped in the user finally: the
 * finally body is inlined on the normal exit and run by a catch-all handler that rethrows, alongside the resource
 * cleanup handlers. The decompiler must fold the cleanup into {@code try (r)} while recovering the user finally as
 * a {@code finally} clause - not dropping it, duplicating its inlined copy, or reordering it before the close.
 * This compiles such a class with javac, decompiles it, recompiles the decompiled source, and asserts the finally
 * survives (structurally, as a round-trip fixed point, and in execution order: the resource closes before it runs).
 */
class TryWithResourcesFinallyFidelityTest {

    private static final String SOURCE =
            "public class TwrFin implements AutoCloseable {\n"
            + "    static int closes;\n"
            + "    static int events;\n"
            + "    public void close() { closes += 1; events = events * 10 + 1; }\n"
            + "    public static int closeCount() { return closes; }\n"
            + "    public static int events() { return events; }\n"
            + "    public static String withFinally(int x) {\n"
            + "        closes = 0;\n"
            + "        events = 0;\n"
            + "        TwrFin r = new TwrFin();\n"
            + "        try (r) {\n"
            + "            return \"v\" + x;\n"
            + "        } finally {\n"
            + "            events = events * 10 + 2;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("twr-finally-fidelity");
        Path src = dir.resolve("TwrFin.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("TwrFin.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "TwrFin must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void foldsToTryWithResourcesWithAFinally() {
        assertTrue(d1.contains("try ("), "decompiler should fold to try-with-resources:\n" + d1);
        assertTrue(d1.contains("finally"), "the user finally clause must be preserved:\n" + d1);
    }

    @Test
    void isRoundTripFixedPoint() {
        assertEquals(d1, d2, "try-with-resources with a finally must be a round-trip fixed point");
    }

    @Test
    void closesBeforeRunningTheFinally() throws Exception {
        assertEquals("v7", recompiledClass.getDeclaredMethod("withFinally", int.class).invoke(null, 7),
                "the normal path must return the body's value");
        assertEquals(1, recompiledClass.getDeclaredMethod("closeCount").invoke(null),
                "the resource must be closed once");
        assertEquals(12, recompiledClass.getDeclaredMethod("events").invoke(null),
                "the resource must close (1) before the finally runs (2)");
    }
}
