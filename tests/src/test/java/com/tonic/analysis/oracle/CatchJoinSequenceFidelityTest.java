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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A try with branchy catches - one rethrowing conditionally - falling through to a shared join that
 * carries further statements (the MDC-initializer shape). The join's only outside in-flow is catch
 * code, so the region structurer cannot guard it into a branch: it must run IN SEQUENCE right after
 * the try/catch for the clauses' fall-throughs to reach it. The engine now cuts the region at such a
 * join and structures each segment in order, instead of declining the whole region to the legacy walk.
 */
class CatchJoinSequenceFidelityTest
{

    private static final String SOURCE =
            "public class CatchJoin {\n"
            + "    public static StringBuilder log = new StringBuilder();\n"
            + "    static int work(int mode) {\n"
            + "        if (mode == 1) { throw new IllegalStateException(\"keep\"); }\n"
            + "        if (mode == 2) { throw new IllegalStateException(\"pass\"); }\n"
            + "        if (mode == 3) { throw new UnsupportedOperationException(); }\n"
            + "        return 10;\n"
            + "    }\n"
            + "    public static String run(int mode) {\n"
            + "        int r = 0;\n"
            + "        try {\n"
            + "            r = work(mode);\n"
            + "            log.append(\"try;\");\n"
            + "        } catch (IllegalStateException e) {\n"
            + "            log.append(\"ise;\");\n"
            + "            String m = e.getMessage();\n"
            + "            if (m != null && m.contains(\"pass\")) {\n"
            + "                throw e;\n"
            + "            }\n"
            + "            r = -1;\n"
            + "        } catch (UnsupportedOperationException e) {\n"
            + "            log.append(\"uoe;\");\n"
            + "            r = -2;\n"
            + "        }\n"
            + "        log.append(\"join;\");\n"
            + "        if (r > 0) {\n"
            + "            log.append(\"pos;\");\n"
            + "        }\n"
            + "        return \"r\" + r;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("catch-join");
        Path src = dir.resolve("CatchJoin.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("CatchJoin.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "CatchJoin must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    private static String run(int mode) throws Exception
    {
        java.lang.reflect.Field log = recompiledClass.getField("log");
        ((StringBuilder) log.get(null)).setLength(0);
        Object r = recompiledClass.getMethod("run", int.class).invoke(null, mode);
        return r + "|" + log.get(null);
    }

    @Test
    void normalPathRunsJoinAndPositiveArm() throws Exception
    {
        assertEquals("r10|try;join;pos;", run(0), d1);
    }

    @Test
    void swallowedCatchFallsThroughToTheJoin() throws Exception
    {
        assertEquals("r-1|ise;join;", run(1), d1);
        assertEquals("r-2|uoe;join;", run(3), d1);
    }

    @Test
    void conditionalRethrowSkipsTheJoin() throws Exception
    {
        java.lang.reflect.Field log = recompiledClass.getField("log");
        ((StringBuilder) log.get(null)).setLength(0);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> recompiledClass.getMethod("run", int.class).invoke(null, 2));
        assertEquals(IllegalStateException.class, ex.getCause().getClass());
        assertEquals("pass", ex.getCause().getMessage());
        assertEquals("ise;", log.get(null).toString(), "the rethrow path must not run the join:\n" + d1);
    }
}
