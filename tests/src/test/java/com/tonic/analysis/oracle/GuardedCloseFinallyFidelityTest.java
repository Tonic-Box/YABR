package com.tonic.analysis.oracle;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A finally whose body carries control flow - the guarded close {@code if (in != null) closes++;} -
 * inlined by javac as a branchy multi-block copy on the normal exit. The contiguous finally matcher
 * cannot see such a copy, and the recovery previously mangled the construct: the result variable's
 * stores and the trailing return vanished (uncompilable), or the copy survived after the clause and the
 * close ran twice. The subgraph template de-duplication now matches the copy block-for-block along the
 * branch structure and excises it, keeping the clause as the single close site.
 */
class GuardedCloseFinallyFidelityTest {

    private static final String SOURCE =
            "public class GuardedClose {\n"
            + "    static int closes = 0;\n"
            + "    static Object open() { return \"res\"; }\n"
            + "    static int work(boolean fail) {\n"
            + "        if (fail) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "        return 7;\n"
            + "    }\n"
            + "    public static int use(boolean fail) {\n"
            + "        Object in = null;\n"
            + "        int r = 0;\n"
            + "        try {\n"
            + "            in = open();\n"
            + "            r = work(fail);\n"
            + "        } finally {\n"
            + "            if (in != null) {\n"
            + "                closes++;\n"
            + "            }\n"
            + "        }\n"
            + "        return r;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("guarded-close");
        Path src = dir.resolve("GuardedClose.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("GuardedClose.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "GuardedClose must be recompilable");
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
    void normalPathClosesOnceAndReturnsTheResult() throws Exception {
        reset();
        Object r = recompiledClass.getMethod("use", boolean.class).invoke(null, false);
        assertEquals(7, r, "the result must flow through the finally to the return:\n" + d1);
        assertEquals(1, closes(), "the guarded close must run exactly once on the normal path:\n" + d1);
    }

    @Test
    void exceptionPathClosesOnceAndPropagates() throws Exception {
        reset();
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> recompiledClass.getMethod("use", boolean.class).invoke(null, true));
        assertEquals(IllegalStateException.class, ex.getCause().getClass());
        assertEquals(1, closes(),
                "the guarded close must run exactly once on the exception path: " + d1);
    }
}
