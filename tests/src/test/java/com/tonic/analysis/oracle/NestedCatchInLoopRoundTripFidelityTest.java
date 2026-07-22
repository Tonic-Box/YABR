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
 * A {@code try/catch} nested INSIDE a loop that is itself wrapped by an OUTER (non-synchronized) try/catch.
 * The recompiler can split the outer catch's protected range so that its second piece begins at the same
 * in-loop block where the nested try starts; both handlers then start at that block. The loop body walk
 * looked up the handler by widest range and, finding the already-recovered outer handler, skipped handler
 * recovery entirely and DROPPED the nested catch on the second decompile. The lookup now selects the widest
 * still-unrecovered handler at a block, and a nested handler that lives inside a loop body is left
 * unprocessed so the loop's own walk recovers it. Asserts a full round-trip fixed point (the nested catch
 * survives both decompiles) and correct execution (the caught exception is swallowed and the loop resumes).
 */
class NestedCatchInLoopRoundTripFidelityTest {

    private static final String SOURCE =
            "public class OuterNestedCatch {\n"
            + "    int n;\n"
            + "    boolean done;\n"
            + "    void step() {\n"
            + "        n++;\n"
            + "        if (n == 2) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "        if (n >= 4) {\n"
            + "            done = true;\n"
            + "        }\n"
            + "    }\n"
            + "    void onError() {\n"
            + "        n = -100;\n"
            + "    }\n"
            + "    public int run() {\n"
            + "        try {\n"
            + "            while (!done) {\n"
            + "                try {\n"
            + "                    step();\n"
            + "                } catch (Exception e) {\n"
            + "                    // swallow, keep looping\n"
            + "                }\n"
            + "            }\n"
            + "        } catch (RuntimeException re) {\n"
            + "            onError();\n"
            + "        }\n"
            + "        return n;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("outer-nested-catch");
        Path src = dir.resolve("OuterNestedCatch.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("OuterNestedCatch.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "OuterNestedCatch must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void nestedCatchSurvivesBothDecompiles() {
        assertTrue(d1.contains("catch (Exception"),
                "the nested catch inside the loop must be recovered on the first decompile:\n" + d1);
        assertTrue(d2.contains("catch (Exception"),
                "the nested catch must NOT be dropped on the second decompile (round-trip fixed point):\n" + d2);
    }

    @Test
    void theCaughtExceptionIsSwallowedNotPropagated() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        // n=1; n=2 throws, caught by the inner catch (loop continues); n=3; n=4 -> done -> return 4.
        // If the inner catch were dropped the throw would reach the outer catch -> onError() -> -100.
        assertEquals(4, recompiledClass.getMethod("run").invoke(inst),
                "the inner catch swallows the throw and the loop resumes");
    }
}
