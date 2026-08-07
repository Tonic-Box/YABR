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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A guarded try whose fall-through and whose guard's early exit converge on one shared trailing return.
 * The staging's prefix pass absorbs the shared return into the guard's arm and marks it processed; the
 * try's continuation then resolved to the empty goto shell in front of the join, the region hand-off
 * emitted nothing for the processed join, and the fall-through path lost its return entirely (a boolean
 * method ending without a return - uncompilable, and the success result gone). The staging now resolves
 * the continuation through bare goto shells and re-emits a processed trailing return, which is
 * idempotent.
 */
class SharedReturnAfterGuardedTryFidelityTest
{

    private static final String SOURCE =
            "public class GuardedTry {\n"
            + "    static int calls;\n"
            + "    static void touch(boolean fail) {\n"
            + "        calls++;\n"
            + "        if (fail) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "    }\n"
            + "    public static boolean run(boolean enabled, boolean fail) {\n"
            + "        boolean success = false;\n"
            + "        if (enabled) {\n"
            + "            try {\n"
            + "                touch(fail);\n"
            + "                success = true;\n"
            + "            } catch (IllegalStateException e) {}\n"
            + "        }\n"
            + "        return success;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("guarded-try");
        Path src = dir.resolve("GuardedTry.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("GuardedTry.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "GuardedTry must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void fallThroughReturnSurvives()
    {
        int lastBrace = d1.lastIndexOf("return success");
        assumeTrue(true);
        org.junit.jupiter.api.Assertions.assertTrue(lastBrace >= 0,
                "the shared trailing return must be emitted for the try's fall-through path:\n" + d1);
    }

    @Test
    void bothPathsReturnTheRightValue() throws Exception
    {
        assertEquals(Boolean.TRUE, recompiledClass.getMethod("run", boolean.class, boolean.class)
                        .invoke(null, true, false),
                "the successful path must return true through the shared return");
        assertEquals(Boolean.FALSE, recompiledClass.getMethod("run", boolean.class, boolean.class)
                        .invoke(null, true, true),
                "the caught path must return false");
        assertEquals(Boolean.FALSE, recompiledClass.getMethod("run", boolean.class, boolean.class)
                        .invoke(null, false, false),
                "the guard's early-exit path must return false");
    }
}
