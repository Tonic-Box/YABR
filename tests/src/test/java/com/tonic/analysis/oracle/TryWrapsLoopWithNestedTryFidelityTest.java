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
 * A try/finally that WRAPS an infinite grow-and-retry loop whose body contains its own nested
 * try-with-resources and whose exits are internal returns - the lwjgl {@code getPath}/{@code extract}
 * shape. The legacy structural analyzer classified the goto-headed {@code while (true)} header (no
 * conditional latch, exits via returns) as a plain SEQUENCE, so the walk flattened the body and dropped
 * the loop entirely: an uncompilable method that fell off its end. The header is now recovered as a
 * {@code while (true)}, its internal return paths (which the loop analysis excludes because they leave
 * the method) stay inside the body rather than truncating it as stop blocks, and no dead statement is
 * appended after the infinite loop.
 */
class TryWrapsLoopWithNestedTryFidelityTest
{

    private static final String SOURCE =
            "public class TryLoop2 {\n"
            + "    static int frees = 0;\n"
            + "    static java.io.StringReader push() { return new java.io.StringReader(\"x\"); }\n"
            + "    static int[] alloc(int n) { return new int[n]; }\n"
            + "    static void free(int[] b) { frees++; }\n"
            + "    static int fill(java.io.StringReader s, int[] b) { return b.length >= 24 ? 5 : 0; }\n"
            + "    public static String get() {\n"
            + "        int maxLen = 8;\n"
            + "        int[] buffer = alloc(maxLen);\n"
            + "        try {\n"
            + "            while (true) {\n"
            + "                int len;\n"
            + "                int e = 0;\n"
            + "                try (java.io.StringReader s = push()) {\n"
            + "                    len = fill(s, buffer);\n"
            + "                    e = len == 0 ? 122 : 0;\n"
            + "                }\n"
            + "                if (e == 0) {\n"
            + "                    return len == 0 ? null : (\"len\" + len);\n"
            + "                }\n"
            + "                if (e != 122) {\n"
            + "                    return null;\n"
            + "                }\n"
            + "                maxLen = maxLen * 3 / 2;\n"
            + "                buffer = alloc(maxLen);\n"
            + "            }\n"
            + "        } finally {\n"
            + "            free(buffer);\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("try-loop2");
        Path src = dir.resolve("TryLoop2.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("TryLoop2.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "TryLoop2 must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void loopFormsWithBothExitsAndTheRetryArm()
    {
        assertTrue(d1.contains("while (true)"), "the retry loop must be recovered as a loop:\n" + d1);
        assertTrue(d1.contains("continue;"), "the grow-and-retry arm must continue the loop:\n" + d1);
        int firstReturn = d1.indexOf("return null;");
        int lastReturn = d1.lastIndexOf("return");
        assertTrue(firstReturn >= 0 && lastReturn > firstReturn,
                "both return exits must survive inside the loop:\n" + d1);
        int loopAt = d1.indexOf("while (true)");
        int closeBrace = d1.lastIndexOf("}");
        assertTrue(d1.substring(loopAt).indexOf("free(buffer)") > 0,
                "the outer finally must still free the buffer:\n" + d1);
    }

    @Test
    void executesTheRetryToCompletion() throws Exception
    {
        assertEquals("len5", recompiledClass.getMethod("get").invoke(null),
                "the loop must grow the buffer until fill succeeds and return the result");
        java.lang.reflect.Field frees = recompiledClass.getDeclaredField("frees");
        frees.setAccessible(true);
        assertTrue(frees.getInt(null) >= 1, "the finally must run on the returning path");
    }
}
