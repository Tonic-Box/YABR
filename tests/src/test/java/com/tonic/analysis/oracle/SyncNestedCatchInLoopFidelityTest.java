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
 * A {@code try/catch} nested INSIDE a loop INSIDE a {@code synchronized} block. A synchronized region's
 * finally is a monitorexit, so its outer handler drove recovery through the finally staging path, which
 * declined a nested catch and fell to the legacy walk - and the legacy walk silently DROPPED the nested catch,
 * producing uncompilable output (the checked/handled exception no longer caught) with changed behavior. The
 * finally staging now delegates a synchronized region with a nested catch to the outer-handler scaffolding,
 * which recovers the nested try/catch. Asserts the catch is preserved (compilable, correct bytecode) and that
 * the caught exception is actually swallowed (the loop continues) rather than propagating. (A full d1==d2
 * round trip is not yet a fixed point: the RECOMPILED synchronized layout still drops the catch on the second
 * decompile - a separate residual - but the recompiled bytecode itself is correct, as the execution asserts.)
 */
class SyncNestedCatchInLoopFidelityTest {

    private static final String SOURCE =
            "public class SyncNestedCatch {\n"
            + "    final Object lock = new Object();\n"
            + "    int sum;\n"
            + "    static int f(int x) {\n"
            + "        if (x == 1) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "        return x;\n"
            + "    }\n"
            + "    public int run() {\n"
            + "        synchronized (lock) {\n"
            + "            int i = 0;\n"
            + "            while (i < 4) {\n"
            + "                try {\n"
            + "                    sum += f(i);\n"
            + "                } catch (RuntimeException e) {\n"
            + "                    // swallow, keep looping\n"
            + "                }\n"
            + "                i++;\n"
            + "            }\n"
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
        Path dir = Files.createTempDirectory("sync-nested-catch");
        Path src = dir.resolve("SyncNestedCatch.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("SyncNestedCatch.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "SyncNestedCatch must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void nestedCatchIsPreservedAndCompilable() {
        assertTrue(d1.contains("synchronized"), "the synchronized block must be preserved:\n" + d1);
        assertTrue(d1.contains("catch (RuntimeException"),
                "the nested catch inside the loop must NOT be dropped (was uncompilable output):\n" + d1);
    }

    @Test
    void theCaughtExceptionIsSwallowedNotPropagated() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        // i=0: sum+=0; i=1: f(1) throws, caught, sum unchanged; i=2: sum+=2; i=3: sum+=3 -> 5
        assertEquals(5, recompiledClass.getMethod("run").invoke(inst),
                "f(1) throws and is caught (loop continues); if the catch were dropped the method would throw");
    }
}
