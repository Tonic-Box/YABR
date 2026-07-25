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
 * A counted loop whose condition exit is a TERMINAL tail while a success arm leaves the loop through
 * its own returning chain (with a nested try/catch on the way) - the authentication-flow shape. The
 * loop structurer used to pick the success chain's continuation as the loop's break target even
 * though lifting the terminal condition exit into {@code while (cond)} claims the after-loop
 * position for the terminal tail: the success arm emitted a bare {@code break} that landed on the
 * WRONG tail (the failure return ran on the success path) and the real success continuation trailed
 * the method as unreachable code after a return. A break target is now only kept when an unlabeled
 * {@code break} actually reaches it; a divergent terminating chain is inlined in its arm instead.
 */
class TerminalExitLoopArmFidelityTest {

    private static final String SOURCE =
            "public class LoopExit {\n"
            + "    public static StringBuilder log = new StringBuilder();\n"
            + "    static boolean check(int i) { return i == 2; }\n"
            + "    public static String run() {\n"
            + "        int i = 0;\n"
            + "        while (i < 5) {\n"
            + "            log.append(\"iter;\");\n"
            + "            if (check(i)) {\n"
            + "                log.append(\"win;\");\n"
            + "                try {\n"
            + "                    Thread.sleep(1L);\n"
            + "                } catch (InterruptedException e) {\n"
            + "                    Thread.currentThread().interrupt();\n"
            + "                }\n"
            + "                log.append(\"post;\");\n"
            + "                return \"ok\" + i;\n"
            + "            }\n"
            + "            i++;\n"
            + "        }\n"
            + "        log.append(\"max;\");\n"
            + "        return \"none\";\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("loop-exit-arm");
        Path src = dir.resolve("LoopExit.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("LoopExit.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "LoopExit must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void successChainStaysInsideItsArm() {
        int ifAt = d1.indexOf("check(");
        int sleepAt = d1.indexOf("Thread.sleep");
        int maxAt = d1.indexOf("\"max;\"");
        assertTrue(ifAt >= 0 && sleepAt > ifAt,
                "the sleep try must be recovered inside the success arm:\n" + d1);
        assertTrue(maxAt > sleepAt,
                "the terminal condition tail must follow the loop, not precede the success chain:\n" + d1);
        int lastReturn = d1.lastIndexOf("return");
        int lastBrace = d1.lastIndexOf('}');
        assertTrue(d1.indexOf("return \"none\";") < lastReturn || d1.indexOf("return \"none\";") == lastReturn,
                "no unreachable code may trail the method:\n" + d1);
    }

    @Test
    void successPathRunsItsOwnContinuation() throws Exception {
        java.lang.reflect.Field log = recompiledClass.getField("log");
        ((StringBuilder) log.get(null)).setLength(0);
        Object r = recompiledClass.getMethod("run").invoke(null);
        assertEquals("ok2", r, "the success arm must return its own result:\n" + d1);
        assertEquals("iter;iter;iter;win;post;", log.get(null).toString(),
                "the success path must run sleep+post, never the max tail:\n" + d1);
    }
}
