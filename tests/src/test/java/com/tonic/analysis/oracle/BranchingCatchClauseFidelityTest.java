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
 * A non-catch-all catch clause whose body branches on a compound condition -
 * {@code if (m != null && m.contains(...)) { effects } else { throw e; }} - converging on the clause
 * boundary. The flat successor walk dropped the branch entirely (unconditional rethrow, guarded arm
 * lost); routing the clause through structured recovery then exposed two reaching-condition bugs: the
 * final terminal tail was unguarded even though the swallow arm falls through past it, and an
 * exception-reached region's blocks were indexed in collection order, so the join's guard silently lost
 * the second condition's disjunct. All three are fixed; this locks the behavior by execution.
 */
class BranchingCatchClauseFidelityTest
{

    private static final String SOURCE =
            "public class CatchCond {\n"
            + "    static int reports = 0;\n"
            + "    static Object adapter = null;\n"
            + "    static Object bind(int mode) {\n"
            + "        if (mode == 1) {\n"
            + "            throw new NoClassDefFoundError(\"missing StaticBinder here\");\n"
            + "        }\n"
            + "        if (mode == 2) {\n"
            + "            throw new NoClassDefFoundError(\"unrelated failure\");\n"
            + "        }\n"
            + "        return \"bound\";\n"
            + "    }\n"
            + "    public static void init(int mode) {\n"
            + "        try {\n"
            + "            adapter = bind(mode);\n"
            + "        } catch (NoClassDefFoundError ncde) {\n"
            + "            adapter = \"nop\";\n"
            + "            String msg = ncde.getMessage();\n"
            + "            if (msg != null && msg.contains(\"StaticBinder\")) {\n"
            + "                reports++;\n"
            + "                reports++;\n"
            + "                reports++;\n"
            + "            } else {\n"
            + "                throw ncde;\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("catch-cond");
        Path src = dir.resolve("CatchCond.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("CatchCond.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "CatchCond must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    private static void reset() throws Exception
    {
        java.lang.reflect.Field reports = recompiledClass.getDeclaredField("reports");
        reports.setAccessible(true);
        reports.setInt(null, 0);
    }

    private static int reports() throws Exception
    {
        java.lang.reflect.Field reports = recompiledClass.getDeclaredField("reports");
        reports.setAccessible(true);
        return reports.getInt(null);
    }

    @Test
    void matchingMessageRunsTheGuardedArmAndSwallows() throws Exception
    {
        reset();
        recompiledClass.getMethod("init", int.class).invoke(null, 1);
        assertEquals(3, reports(), "the guarded report arm must run when the message matches - not be dropped:\n" + d1);
    }

    @Test
    void nonMatchingMessageRethrows() throws Exception
    {
        reset();
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> recompiledClass.getMethod("init", int.class).invoke(null, 2),
                "the non-matching message must take the else arm and rethrow:\n" + d1);
        assertEquals("unrelated failure", ex.getCause().getMessage());
        assertEquals(0, reports(), "the guarded arm must not run on the rethrow path:\n" + d1);
    }

    @Test
    void normalPathTakesNoCatch() throws Exception
    {
        reset();
        recompiledClass.getMethod("init", int.class).invoke(null, 0);
        assertEquals(0, reports(), "the normal path must not enter the catch:\n" + d1);
    }
}
