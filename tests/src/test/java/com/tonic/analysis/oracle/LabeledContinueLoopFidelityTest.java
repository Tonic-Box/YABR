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
 * Fidelity of an inner loop whose only non-natural exit is a {@code continue <label>} to the enclosing
 * loop. That continue leaves the inner loop through a bridge block that jumps to the outer header, so the
 * inner loop appears to diverge to two continuations (the labeled outer header and its own natural exit)
 * and was declined to the legacy walk. The reaching-condition engine now recognizes an exit whose settled
 * continuation is an enclosing loop's continue/break target as a labeled jump, not a divergent break, and
 * structures the loop natively. Asserts the labeled continue and inner break are recovered and that the
 * natively structured, recompiled bytecode computes the original result on every path. (A strict
 * decompile-idempotence assertion is deliberately omitted here: this shape also carries a constant-equality
 * guard - {@code v == 5} - whose orientation belongs to the switch reconstructor, an orthogonal concern.)
 */
class LabeledContinueLoopFidelityTest {

    private static final String SOURCE =
            "public class LabeledCont {\n"
            + "    public static int scan(int rows, int cols) {\n"
            + "        int total = 0;\n"
            + "        int r = 0;\n"
            + "        outer:\n"
            + "        while (r < rows) {\n"
            + "            int i = 0;\n"
            + "            while (i < cols) {\n"
            + "                int v = (r * 7 + i * 3) % 11 - 3;\n"
            + "                if (v < 0) {\n"
            + "                    r++;\n"
            + "                    continue outer;\n"
            + "                }\n"
            + "                if (v == 5) {\n"
            + "                    break;\n"
            + "                }\n"
            + "                total += v;\n"
            + "                i++;\n"
            + "            }\n"
            + "            total += 1000;\n"
            + "            r++;\n"
            + "        }\n"
            + "        return total;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRoundTrip() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("labeled-cont");
        Path src = dir.resolve("LabeledCont.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("LabeledCont.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile r1 = Recompile.recompiledClone(cf, pool);
        assertNotNull(r1, "LabeledCont must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(r1);
    }

    @Test
    void labeledContinueAndBreakSurvive() {
        assertTrue(d1.contains("continue outer") || d1.contains("continue label"),
                "the labeled continue to the enclosing loop must survive:\n" + d1);
        assertTrue(d1.contains("break"), "the inner loop's break must survive:\n" + d1);
    }

    @Test
    void behaviorSurvivesTheRoundTrip() throws Exception {
        for (int rows = 0; rows < 6; rows++) {
            for (int cols = 0; cols < 6; cols++) {
                assertEquals(reference(rows, cols),
                        recompiledClass.getDeclaredMethod("scan", int.class, int.class).invoke(null, rows, cols),
                        "every path (labeled continue, break, fall-through) must match at rows=" + rows
                                + " cols=" + cols);
            }
        }
    }

    /** A faithful re-implementation of the fixture's {@code scan}, to check the recompiled behavior against. */
    private static int reference(int rows, int cols) {
        int total = 0;
        int r = 0;
        outer:
        while (r < rows) {
            int i = 0;
            while (i < cols) {
                int v = (r * 7 + i * 3) % 11 - 3;
                if (v < 0) {
                    r++;
                    continue outer;
                }
                if (v == 5) {
                    break;
                }
                total += v;
                i++;
            }
            total += 1000;
            r++;
        }
        return total;
    }
}
