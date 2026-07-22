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
 * A {@code try/catch} preceded by a branch with an early return and followed by a continuation. The
 * continuation (here the trailing {@code return}) is a shared merge reached from BOTH the try's normal exit
 * and the catch, so its immediate dominator lies outside the try/continuation region. The reaching-condition
 * engine's region collection used to absorb that merge and then decline the whole region on the single-entry
 * check (routing to the legacy walk); it now treats a successor the region entry does not dominate as an
 * implicit boundary, so the region stays single-entry and the merge is recovered as the continuation.
 * Asserts a round-trip fixed point and correct execution on the early-return, normal, and throwing paths.
 */
class TryAfterBranchFidelityTest {

    private static final String SOURCE =
            "public class TryAfterBranch {\n"
            + "    int state;\n"
            + "    public int run(int x) {\n"
            + "        if (x < 0) {\n"
            + "            return -1;\n"
            + "        }\n"
            + "        try {\n"
            + "            state = compute(x);\n"
            + "        } catch (RuntimeException e) {\n"
            + "            state = -2;\n"
            + "        }\n"
            + "        return state;\n"
            + "    }\n"
            + "    static int compute(int x) {\n"
            + "        if (x == 7) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "        return x * 10;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("try-after-branch");
        Path src = dir.resolve("TryAfterBranch.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("TryAfterBranch.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "TryAfterBranch must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void tryAndContinuationArePreservedAndRoundTripFixed() {
        assertTrue(d1.contains("try") && d1.contains("catch"), "the try/catch must be preserved:\n" + d1);
        assertTrue(d1.contains("return this.state"), "the shared continuation must be preserved:\n" + d1);
        assertEquals(d1, d2, "a try/catch after a branch, with a shared continuation, must be a round-trip fixed point");
    }

    @Test
    void allThreePathsExecuteEquivalently() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        assertEquals(-1, recompiledClass.getMethod("run", int.class).invoke(inst, -5),
                "the early-return path returns -1");
        assertEquals(30, recompiledClass.getMethod("run", int.class).invoke(inst, 3),
                "the normal path returns compute(3) = 30 via the continuation");
        assertEquals(-2, recompiledClass.getMethod("run", int.class).invoke(inst, 7),
                "the throwing path (x == 7) is caught and the continuation returns -2");
    }
}
