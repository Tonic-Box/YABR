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
 * A try/finally nested inside a catch clause whose fall-through joins the try's continuation. The walking
 * recovery folded the inlined finally copy into the {@code finally} clause AND re-emitted the already
 * processed copy blocks after it - the finally's side effects ran twice on the exception path (a lock
 * released twice throws), and a spill local from the re-emitted copy leaked in undeclared, so the output
 * did not even compile.
 */
class FinallyInCatchJoinFidelityTest
{

    private static final String SOURCE =
            "import java.util.concurrent.locks.ReentrantLock;\n"
            + "public class CatchFin {\n"
            + "    static final ReentrantLock lock = new ReentrantLock();\n"
            + "    static int unlocks = 0;\n"
            + "    static int state = 0;\n"
            + "    static void work(boolean fail) {\n"
            + "        if (fail) {\n"
            + "            throw new RuntimeException();\n"
            + "        }\n"
            + "    }\n"
            + "    public static void run(boolean fail) {\n"
            + "        try {\n"
            + "            work(fail);\n"
            + "            state = 1;\n"
            + "        } catch (Exception e) {\n"
            + "            state = 2;\n"
            + "            lock.lock();\n"
            + "            try {\n"
            + "                state = 3;\n"
            + "            } finally {\n"
            + "                lock.unlock();\n"
            + "                unlocks++;\n"
            + "            }\n"
            + "        }\n"
            + "        state = state + 10;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("catch-fin");
        Path src = dir.resolve("CatchFin.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("CatchFin.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "CatchFin must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void finallyRunsOnceOnTheExceptionPath() throws Exception
    {
        recompiledClass.getMethod("run", boolean.class).invoke(null, true);
        java.lang.reflect.Field unlocks = recompiledClass.getDeclaredField("unlocks");
        unlocks.setAccessible(true);
        assertEquals(1, unlocks.getInt(null), "the finally body must run exactly once on the caught path:\n" + d1);
        java.lang.reflect.Field state = recompiledClass.getDeclaredField("state");
        state.setAccessible(true);
        assertEquals(13, state.getInt(null), "the shared continuation must run after the catch:\n" + d1);
    }

    @Test
    void normalPathIsUntouched() throws Exception
    {
        java.lang.reflect.Field state = recompiledClass.getDeclaredField("state");
        state.setAccessible(true);
        java.lang.reflect.Field unlocks = recompiledClass.getDeclaredField("unlocks");
        unlocks.setAccessible(true);
        state.setInt(null, 0);
        unlocks.setInt(null, 0);
        recompiledClass.getMethod("run", boolean.class).invoke(null, false);
        assertEquals(11, state.getInt(null), "the normal path must skip the catch:\n" + d1);
        assertEquals(0, unlocks.getInt(null), "the finally must not run on the normal path:\n" + d1);
    }
}
