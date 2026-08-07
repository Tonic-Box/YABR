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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A guard of the shape {@code A && (B || C)} whose body throws. The throw is the last terminal in the
 * subtree below A, and the rule that leaves such a tail unguarded only looked for paths exiting to a region
 * STOP block - here the surviving path hands back to a sibling region block instead, so the rule fired and
 * the whole {@code (B || C)} test was dropped: every A threw. Asserts the accepted range still passes.
 */
class NestedDisjunctionGuardFidelityTest
{

    private static final String SOURCE =
            "public class RangeGuard {\n"
            + "    public boolean skipCheck;\n"
            + "    public int accepted;\n"
            + "    public void setup(int components) {\n"
            + "        if (!skipCheck) {\n"
            + "            if (components < 1 || components > 4) {\n"
            + "                throw new IllegalArgumentException(\"components must be between 1 and 4\");\n"
            + "            }\n"
            + "        }\n"
            + "        this.accepted = components;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("range-guard");
        Path src = dir.resolve("RangeGuard.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("RangeGuard.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "RangeGuard must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void theRangeTestSurvivesInsideTheOuterGuard() throws Exception
    {
        Object instance = recompiledClass.getDeclaredConstructor().newInstance();
        recompiledClass.getMethod("setup", int.class).invoke(instance, 3);
        assertEquals(3, recompiledClass.getField("accepted").getInt(instance),
                "an in-range value must be accepted (the range test was dropped):\n" + d1);

        try
        {
            recompiledClass.getMethod("setup", int.class).invoke(instance, 7);
            throw new AssertionError("an out-of-range value must still be rejected:\n" + d1);
        }
        catch (InvocationTargetException expected)
        {
            assertTrue(expected.getCause() instanceof IllegalArgumentException, "wrong rejection:\n" + d1);
        }

        recompiledClass.getField("skipCheck").setBoolean(instance, true);
        recompiledClass.getMethod("setup", int.class).invoke(instance, 99);
        assertEquals(99, recompiledClass.getField("accepted").getInt(instance),
                "the outer guard must still skip the range test:\n" + d1);
    }
}
