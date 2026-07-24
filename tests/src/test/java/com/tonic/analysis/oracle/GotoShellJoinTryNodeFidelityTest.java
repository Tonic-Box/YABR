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
 * A try whose fall-through exits through a bare goto shell in front of the join the catch reaches
 * directly. The try-node decoder compared raw successors, saw the shell and the join as two distinct
 * continuations, and declined the node - sending the whole region to the walking recovery. The decoder
 * now resolves each continuation candidate through goto shells before comparing, so both paths name the
 * same join and the node decodes.
 */
class GotoShellJoinTryNodeFidelityTest {

    private static final String SOURCE =
            "public class ShellJoin {\n"
            + "    static void touch(boolean fail) {\n"
            + "        if (fail) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "    }\n"
            + "    public static int run(boolean enabled, boolean fail) {\n"
            + "        int result = 0;\n"
            + "        if (enabled) {\n"
            + "            try {\n"
            + "                touch(fail);\n"
            + "                result = 1;\n"
            + "            } catch (IllegalStateException e) {\n"
            + "                result = 2;\n"
            + "            }\n"
            + "            result = result + 10;\n"
            + "        }\n"
            + "        return result;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("shell-join");
        Path src = dir.resolve("ShellJoin.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ShellJoin.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "ShellJoin must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void joinIsSharedNotDuplicated() {
        int first = d1.indexOf("result + 10");
        int last = d1.lastIndexOf("result + 10");
        assertTrue(first >= 0, "the shared join statement must be present:\n" + d1);
        assertEquals(first, last, "the shared join must be emitted once, not per path:\n" + d1);
    }

    @Test
    void allPathsComputeTheRightValue() throws Exception {
        assertEquals(11, recompiledClass.getMethod("run", boolean.class, boolean.class)
                .invoke(null, true, false), "the fall-through path reaches the join through the shell");
        assertEquals(12, recompiledClass.getMethod("run", boolean.class, boolean.class)
                .invoke(null, true, true), "the caught path reaches the join directly");
        assertEquals(0, recompiledClass.getMethod("run", boolean.class, boolean.class)
                .invoke(null, false, false), "the guard's bypass skips the join");
    }
}
