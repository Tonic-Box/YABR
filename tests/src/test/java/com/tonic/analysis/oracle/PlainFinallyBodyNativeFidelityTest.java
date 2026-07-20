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
 * Fidelity of a plain {@code try { body } finally { f }} whose finally body is straight-line but which javac
 * compiles with the finally inlined on each of the body's several exits, and whose handler is split across
 * blocks by javac's finally-self-protection. The reaching-condition engine can structure the multi-exit body
 * natively only once the inlined finally copies are excised at the IR level; this test exercises that path with
 * a body that has real control flow (an early return) so the native structuring is meaningful, and asserts a
 * round-trip fixed point plus that both exits run the finally in the correct order (the return value is read
 * before the finally mutates shared state).
 */
class PlainFinallyBodyNativeFidelityTest {

    private static final String SOURCE =
            "public class PlainFinallyBody {\n"
            + "    static int events;\n"
            + "    static int cleared;\n"
            + "    public static int m(int x) {\n"
            + "        events = 7;\n"
            + "        try {\n"
            + "            if (x < 0) { return events + 100; }\n"
            + "            return events + x;\n"
            + "        } finally {\n"
            + "            cleared = events;\n"
            + "            events = 0;\n"
            + "        }\n"
            + "    }\n"
            + "    public static int events() { return events; }\n"
            + "    public static int cleared() { return cleared; }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("plain-finally-native");
        Path src = dir.resolve("PlainFinallyBody.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("PlainFinallyBody.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "PlainFinallyBody must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void isRoundTripFixedPoint() {
        assertTrue(d1.contains("finally"), "the finally clause must be preserved:\n" + d1);
        assertEquals(d1, d2, "a try/finally with a multi-exit body must be a round-trip fixed point");
    }

    @Test
    void bothExitsRunTheFinallyInOrder() throws Exception {
        assertEquals(12, recompiledClass.getDeclaredMethod("m", int.class).invoke(null, 5),
                "the normal path reads events (7) + x (5) before the finally zeroes events");
        assertEquals(0, recompiledClass.getDeclaredMethod("events").invoke(null),
                "the finally must run on the normal-return path");
        assertEquals(7, recompiledClass.getDeclaredMethod("cleared").invoke(null),
                "the finally captured events before zeroing it");

        assertEquals(107, recompiledClass.getDeclaredMethod("m", int.class).invoke(null, -1),
                "the early-return path reads events (7) + 100 before the finally zeroes events");
        assertEquals(0, recompiledClass.getDeclaredMethod("events").invoke(null),
                "the finally must run on the early-return path too");
    }
}
