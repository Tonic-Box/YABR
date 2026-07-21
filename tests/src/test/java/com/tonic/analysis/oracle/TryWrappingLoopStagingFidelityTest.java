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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A {@code try { while (...) {...} } catch (...) {...}} that WRAPS a loop, sitting between a prelude and a
 * continuation, has its start block AT the loop header. The sequential-try staging must distinguish this
 * (linearly stageable: prefix, try, continuation - the loop is wholly within the protected range) from a try
 * nested inside an ENCLOSING loop (not stageable, since the prefix re-runs each iteration). Treating any
 * in-loop try-start as unstageable sent the whole shape to the legacy walk; staging it hands the pieces to
 * the reaching-condition engine.
 *
 * <p>This asserts decompile fidelity only: the loop body keeps its statement order, the prelude locals are
 * preserved (not dropped), and the try/catch/loop nest is intact. A full recompile round trip of this shape
 * is blocked by an unrelated, pre-existing recompiler defect (a monitor/long-local frame error when a loop
 * inside a try is followed by a continuation), so it is not exercised here.
 */
class TryWrappingLoopStagingFidelityTest {

    private static final String SOURCE =
            "public class TryWrapLoop {\n"
            + "    long result;\n"
            + "    public long run(long seed) {\n"
            + "        long total = 0;\n"
            + "        long v = seed;\n"
            + "        try {\n"
            + "            while (v != 0) {\n"
            + "                if (v == 66) {\n"
            + "                    throw new IllegalStateException(\"sixty-six\");\n"
            + "                }\n"
            + "                total += v;\n"
            + "                v = v / 2;\n"
            + "            }\n"
            + "        } catch (RuntimeException e) {\n"
            + "            total = -1;\n"
            + "        }\n"
            + "        result = total;\n"
            + "        return total;\n"
            + "    }\n"
            + "}\n";

    private static String d1;

    @BeforeAll
    static void compile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("try-wrap-loop");
        Path src = dir.resolve("TryWrapLoop.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("TryWrapLoop.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
    }

    @Test
    void tryCatchAndLoopArePreserved() {
        assertTrue(d1.contains("try"), "the try/catch must be preserved:\n" + d1);
        assertTrue(d1.contains("while") || d1.contains("for"), "the loop must be preserved:\n" + d1);
        assertTrue(d1.contains("catch (RuntimeException e)"), "the catch clause must be preserved:\n" + d1);
    }

    @Test
    void preludeLocalsAndBodyOrderAreCorrect() {
        assertTrue(d1.contains("long v = seed"), "the prelude local v must be declared, not dropped:\n" + d1);
        // The loop body accumulates BEFORE halving; that order must survive (a reorder is a miscompile).
        int add = d1.indexOf("total = total + v");
        int half = d1.indexOf("v = v / 2");
        assertTrue(add >= 0 && half >= 0 && add < half,
                "the loop body must accumulate before halving, in that order:\n" + d1);
    }
}
