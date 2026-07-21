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
 * Fidelity of a {@code try { body } finally { f }} nested inside an outer {@code catch}. javac inlines the
 * finally on the inner try's normal-exit continuation, which is the block at the inner try's exclusive end
 * offset; the inner-handler recovery must exclude that block from the try body, else the finally body is
 * emitted a second time at the end of the try and executes twice on the normal path (a doubled cleanup). The
 * counter here would read 2 instead of 1 under that bug.
 */
class FinallyInOuterCatchNoDoubleRunFidelityTest {

    private static final String SOURCE =
            "public class FinallyInOuterCatch {\n"
            + "    static int cleanups;\n"
            + "    static int result;\n"
            + "    public static void m(int r) {\n"
            + "        try {\n"
            + "            begin();\n"
            + "            try {\n"
            + "                result = r;\n"
            + "            } finally {\n"
            + "                cleanups++;\n"
            + "            }\n"
            + "        } catch (Exception e) {\n"
            + "            result = -1;\n"
            + "        }\n"
            + "    }\n"
            + "    static void begin() {}\n"
            + "    public static int cleanups() { return cleanups; }\n"
            + "    public static int result() { return result; }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("finally-in-outer-catch");
        Path src = dir.resolve("FinallyInOuterCatch.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("FinallyInOuterCatch.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "FinallyInOuterCatch must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void isRoundTripFixedPoint() {
        assertTrue(d1.contains("finally"), "the finally clause must be preserved:\n" + d1);
        assertEquals(d1, d2, "a try/finally nested in an outer catch must be a round-trip fixed point");
    }

    @Test
    void finallyRunsExactlyOnceOnTheNormalPath() throws Exception {
        recompiledClass.getDeclaredMethod("m", int.class).invoke(null, 5);
        assertEquals(5, recompiledClass.getDeclaredMethod("result").invoke(null),
                "the try body assigned the parameter");
        assertEquals(1, recompiledClass.getDeclaredMethod("cleanups").invoke(null),
                "the finally must run exactly once on the normal path, not twice");
    }
}
