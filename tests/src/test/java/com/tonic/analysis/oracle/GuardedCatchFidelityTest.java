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
 * A typed catch whose body GUARDS an effect on a variable assigned inside the try -
 * {@code catch (E e) { if (in != null) closes++; return -1; }}. Two independent defects used to
 * mangle this shape. The recovery's flat successor walk visited the handler's arms in set order,
 * placed the guarded increment behind the other arm's return and filtered it out - the decompiled
 * catch showed only {@code return -1;}, silently dropping the effect (and the variable with it).
 * And on recompile, the catch entry re-bound the variable to its PRE-try value, so the guard
 * evaluated the stale binding (an inlined {@code null}) instead of the fault-time value - skipping
 * the close even when the try's assignment had executed. The branchy handler subtree is now
 * recovered structurally, and the catch binds the try's final value under a slot affinity so the
 * guard reads the variable's one home slot.
 */
class GuardedCatchFidelityTest {

    private static final String SOURCE =
            "public class GuardedCatch {\n"
            + "    static int closes = 0;\n"
            + "    static Object open() { return \"res\"; }\n"
            + "    static int work(boolean fail) {\n"
            + "        if (fail) { throw new IllegalStateException(); }\n"
            + "        return 7;\n"
            + "    }\n"
            + "    public static int use(boolean fail) {\n"
            + "        Object in = null;\n"
            + "        try {\n"
            + "            in = open();\n"
            + "            return work(fail);\n"
            + "        } catch (IllegalStateException e) {\n"
            + "            if (in != null) { closes++; }\n"
            + "            return -1;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("guarded-catch");
        Path src = dir.resolve("GuardedCatch.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("GuardedCatch.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "GuardedCatch must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    private static int closes() throws Exception {
        java.lang.reflect.Field f = recompiledClass.getDeclaredField("closes");
        f.setAccessible(true);
        return f.getInt(null);
    }

    private static void reset() throws Exception {
        java.lang.reflect.Field f = recompiledClass.getDeclaredField("closes");
        f.setAccessible(true);
        f.setInt(null, 0);
    }

    @Test
    void decompiledCatchKeepsTheGuardedEffect() {
        assertTrue(d1.contains("closes++") || d1.contains("closes = closes + 1"),
                "the catch body's guarded increment must survive decompilation:\n" + d1);
        assertTrue(d1.contains("in != null") || d1.contains("null != in"),
                "the guard on the try-assigned variable must survive decompilation:\n" + d1);
    }

    @Test
    void normalPathReturnsResultWithoutClosing() throws Exception {
        reset();
        Object r = recompiledClass.getMethod("use", boolean.class).invoke(null, false);
        assertEquals(7, r);
        assertEquals(0, closes(), "no exception, no close:\n" + d1);
    }

    @Test
    void exceptionPathSeesTheFaultTimeValueAndClosesOnce() throws Exception {
        reset();
        Object r = recompiledClass.getMethod("use", boolean.class).invoke(null, true);
        assertEquals(-1, r);
        assertEquals(1, closes(),
                "the catch guard must read the fault-time value of the try-assigned variable:\n" + d1);
    }
}
