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
 * Fidelity of a {@code synchronized(lock) { body }} whose body carries real control flow (branches and an
 * early return). javac compiles it to a monitor-protected region with a catch-all monitorexit/rethrow handler
 * and an inlined monitorexit on every normal exit; the monitor instructions are dropped during recovery, so
 * the reaching-condition engine can structure the body directly with no finally to emit. This asserts the
 * block is preserved, is a round-trip fixed point, and executes equivalently on each path.
 */
class SynchronizedBlockNativeFidelityTest {

    private static final String SOURCE =
            "public class SyncBody {\n"
            + "    final Object lock = new Object();\n"
            + "    int state;\n"
            + "    public int classify(int x) {\n"
            + "        synchronized (lock) {\n"
            + "            if (x < 0) {\n"
            + "                state = -1;\n"
            + "                return -1;\n"
            + "            }\n"
            + "            if (x == 0) {\n"
            + "                return 0;\n"
            + "            }\n"
            + "            state = x;\n"
            + "            return x * 2;\n"
            + "        }\n"
            + "    }\n"
            + "    public int state() { return state; }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("sync-body-native");
        Path src = dir.resolve("SyncBody.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("SyncBody.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "SyncBody must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void isRoundTripFixedPoint() {
        assertTrue(d1.contains("synchronized"), "the synchronized block must be preserved:\n" + d1);
        assertEquals(d1, d2, "a synchronized block with a multi-exit body must be a round-trip fixed point");
    }

    @Test
    void eachPathExecutesEquivalently() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        assertEquals(-1, recompiledClass.getMethod("classify", int.class).invoke(inst, -5),
                "the negative path returns -1");
        assertEquals(-1, recompiledClass.getMethod("state").invoke(inst),
                "the negative path set state to -1");

        assertEquals(0, recompiledClass.getMethod("classify", int.class).invoke(inst, 0),
                "the zero path returns 0 without touching state");
        assertEquals(-1, recompiledClass.getMethod("state").invoke(inst),
                "the zero path left state unchanged");

        assertEquals(14, recompiledClass.getMethod("classify", int.class).invoke(inst, 7),
                "the positive path returns x*2");
        assertEquals(7, recompiledClass.getMethod("state").invoke(inst),
                "the positive path set state to x");
    }
}
