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
 * Fidelity of try/finally regions that begin after a prelude, staged by delegating to the outer-handler
 * scaffolding. Covers the two boundary-terminal placements: a finally that WRITES a live local the return
 * reads ({@code pre += 10}; the return must be pushed out of the try so it evaluates after the finally),
 * and a finally that writes NO local (a plain {@code unlock()}; the return stays inside the try, matching
 * the natural layout and avoiding an escaped-local spill). Asserts a round-trip fixed point and execution.
 */
class FinallyStageFidelityTest {

    private static final String SOURCE =
            "import java.util.concurrent.locks.ReentrantLock;\n"
            + "public class FinStage {\n"
            + "    private final ReentrantLock lock = new ReentrantLock();\n"
            + "    private int failures;\n"
            + "    public String writesLocal(String a, boolean f) {\n"
            + "        int pre = a.length();\n"
            + "        if (f) {\n"
            + "            pre++;\n"
            + "        }\n"
            + "        int val = 0;\n"
            + "        try {\n"
            + "            val = Integer.parseInt(a) + pre;\n"
            + "        } finally {\n"
            + "            pre += 10;\n"
            + "        }\n"
            + "        return pre + \":\" + val;\n"
            + "    }\n"
            + "    public String returnsInTry(String a) {\n"
            + "        lock.lock();\n"
            + "        try {\n"
            + "            return a + \":\" + a.length();\n"
            + "        } finally {\n"
            + "            lock.unlock();\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static String d3;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRoundTrip() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("finally-stage");
        Path src = dir.resolve("FinStage.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("FinStage.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile r1 = Recompile.recompiledClone(cf, pool);
        assertNotNull(r1, "FinStage must be recompilable");
        d2 = ClassDecompiler.decompile(r1);
        recompiledClass = TestUtils.loadAndVerify(r1);
        ClassFile r2 = Recompile.recompiledClone(r1, TestUtils.emptyPool());
        assertNotNull(r2, "the recompiled FinStage must recompile again");
        d3 = ClassDecompiler.decompile(r2);
    }

    @Test
    void bothBoundaryPlacementsAreRecovered() {
        assertEquals(1, countOccurrences(d1, "pre + 10"),
                "the finally's local write must appear exactly once (no surviving inlined copy):\n" + d1);
        assertTrue(d1.contains("return a + \":\" + a.length();"),
                "a return that does not read the finally must stay inside the try:\n" + d1);
    }

    @Test
    void roundTripIsAFixedPoint() {
        assertEquals(d1, d2, "the staged finally recovery must be layout-independent (javac vs recompiled)");
        assertEquals(d2, d3, "and stable on its own output");
    }

    @Test
    void behaviorSurvivesTheRoundTrip() throws Exception {
        Object o = recompiledClass.getDeclaredConstructor().newInstance();
        assertEquals("13:126", recompiledClass.getDeclaredMethod("writesLocal", String.class, boolean.class)
                .invoke(o, "123", false), "finally-writes-local, normal path: pre 3 + 10, val 123 + 3");
        assertEquals("14:127", recompiledClass.getDeclaredMethod("writesLocal", String.class, boolean.class)
                .invoke(o, "123", true), "prelude branch: pre 3 + 1 + 10, val 123 + 4");
        assertEquals("hi:2", recompiledClass.getDeclaredMethod("returnsInTry", String.class)
                .invoke(o, "hi"), "return-in-try must compute through the finally");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
