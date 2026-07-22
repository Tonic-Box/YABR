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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A {@code finally} whose body carries control flow ({@code if (res != null) closed++;}). Its rethrow
 * handler spans several blocks with a branch, so the flat catch-clause walk dropped the branch condition
 * and lost the body - the decompiled output showed an empty {@code finally {}} and the exception-path
 * cleanup was gone (a resource leak on the throwing path). The clause is now recovered as a structured
 * region over the handler's dominator subtree. Asserts the finally body survives decompilation and that
 * the recompiled bytecode runs the cleanup on both the normal and the throwing path.
 */
class ControlFlowFinallyClauseFidelityTest {

    private static final String SOURCE =
            "public class CfFinally {\n"
            + "    public static String res;\n"
            + "    public static int closed;\n"
            + "    static void step(boolean fail) {\n"
            + "        if (fail) {\n"
            + "            throw new IllegalStateException();\n"
            + "        }\n"
            + "    }\n"
            + "    public static void run(boolean fail) {\n"
            + "        try {\n"
            + "            step(fail);\n"
            + "        } finally {\n"
            + "            if (res != null) {\n"
            + "                closed++;\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("cf-finally");
        Path src = dir.resolve("CfFinally.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("CfFinally.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "CfFinally must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void finallyBodySurvivesDecompilation() {
        assertFalse(d1.contains("finally {}"),
                "the control-flow finally body must not collapse to an empty clause:\n" + d1);
        assertTrue(d1.contains("finally"), "the finally clause must be present:\n" + d1);
        int finallyAt = d1.indexOf("finally");
        assertTrue(d1.indexOf("closed", finallyAt) > 0,
                "the finally clause must contain the guarded cleanup:\n" + d1);
    }

    @Test
    void cleanupRunsOnBothPaths() throws Exception {
        java.lang.reflect.Field res = recompiledClass.getField("res");
        java.lang.reflect.Field closed = recompiledClass.getField("closed");
        res.set(null, "open");

        closed.setInt(null, 0);
        recompiledClass.getMethod("run", boolean.class).invoke(null, false);
        assertEquals(1, closed.getInt(null), "normal path must run the cleanup exactly once");

        closed.setInt(null, 0);
        try {
            recompiledClass.getMethod("run", boolean.class).invoke(null, true);
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }
        assertEquals(1, closed.getInt(null),
                "the throwing path must run the finally cleanup (was dropped with the empty clause)");
    }
}
