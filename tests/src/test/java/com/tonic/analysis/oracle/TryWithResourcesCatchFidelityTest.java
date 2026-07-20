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
 * Fidelity of a try-with-resources statement that carries a user {@code catch} clause. javac compiles
 * {@code try (r) { body } catch (E e) { handler }} as the resource-management desugaring nested inside the user
 * catch; the resource cleanup handlers (a primary that closes and rethrows, and a suppress handler that chains a
 * close failure with {@code addSuppressed}) sit alongside the user catch in the exception table. The decompiler
 * must fold the cleanup handlers into {@code try (r)} while PRESERVING the user catch - dropping it silently
 * changes behavior. This compiles such a class with javac, decompiles it, recompiles the decompiled source, and
 * asserts the catch survives (structurally and in execution) on both the normal and the caught-exception path.
 */
class TryWithResourcesCatchFidelityTest {

    private static final String SOURCE =
            "public class TwrCatch implements AutoCloseable {\n"
            + "    static int closes;\n"
            + "    public void close() { closes += 1; }\n"
            + "    public static int closeCount() { return closes; }\n"
            + "    public static String withCatch(int x) {\n"
            + "        closes = 0;\n"
            + "        TwrCatch r = new TwrCatch();\n"
            + "        try (r) {\n"
            + "            if (x < 0) { throw new RuntimeException(\"neg\"); }\n"
            + "            return \"v\" + x;\n"
            + "        } catch (RuntimeException e) {\n"
            + "            return \"caught:\" + e.getMessage();\n"
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
        Path dir = Files.createTempDirectory("twr-catch-fidelity");
        Path src = dir.resolve("TwrCatch.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("TwrCatch.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "TwrCatch must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void preservesTheUserCatch() {
        assertTrue(d1.contains("try ("), "decompiler should fold to try-with-resources:\n" + d1);
        assertTrue(d1.contains("catch"), "the user catch clause must be preserved, not dropped:\n" + d1);
    }

    @Test
    void roundTripPreservesTheCatchAndBody() {
        assertTrue(d2.contains("try ("), "the recompiled class must still fold to try-with-resources:\n" + d2);
        assertTrue(d2.contains("catch (RuntimeException"),
                "the user catch must survive the round-trip, not be dropped on re-decompile:\n" + d2);
        assertTrue(d2.contains("throw new RuntimeException(\"neg\")"),
                "the body must survive the round-trip, not collapse to an empty try (r) {}:\n" + d2);
    }

    @Test
    void normalPathClosesAndReturns() throws Exception {
        assertEquals("v7", recompiledClass.getDeclaredMethod("withCatch", int.class).invoke(null, 7),
                "the normal path must return the body's value");
        assertEquals(1, recompiledClass.getDeclaredMethod("closeCount").invoke(null),
                "the resource must be closed on the normal path");
    }

    @Test
    void caughtPathRunsTheCatchAfterClosing() throws Exception {
        assertEquals("caught:neg", recompiledClass.getDeclaredMethod("withCatch", int.class).invoke(null, -1),
                "the user catch must run when the body throws");
        assertEquals(1, recompiledClass.getDeclaredMethod("closeCount").invoke(null),
                "the resource must be closed before the catch runs");
    }
}
