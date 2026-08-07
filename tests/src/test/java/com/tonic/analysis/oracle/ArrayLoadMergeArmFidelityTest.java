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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A value merge one of whose arms contributes an array element read ({@code w[0] < 1 ? 1 : w[0]}). The
 * structured path has no per-edge phi lowering, and the array-load recovery only cached its expression, so
 * that arm owed the merge a copy and emitted none: the clamp recovered as {@code w = 0; if (w[0] < 1) w =
 * 1;} and every above-the-floor value silently became zero. Asserts both arms reach the merge variable and
 * that the recompiled clamp returns the element rather than zero.
 */
class ArrayLoadMergeArmFidelityTest
{

    private static final String SOURCE =
            "public class ArrClamp {\n"
            + "    public static int[] w = new int[1];\n"
            + "    public static int clamp() {\n"
            + "        return w[0] < 1 ? 1 : w[0];\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("arr-clamp");
        Path src = dir.resolve("ArrClamp.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("ArrClamp.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "ArrClamp must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void clampReturnsTheElementAboveTheFloor() throws Exception
    {
        java.lang.reflect.Field w = recompiledClass.getField("w");
        int[] cells = (int[]) w.get(null);

        cells[0] = 42;
        assertEquals(42, recompiledClass.getMethod("clamp").invoke(null),
                "the above-floor arm must reach the merge (was dropped, yielding 0):\n" + d1);

        cells[0] = 0;
        assertEquals(1, recompiledClass.getMethod("clamp").invoke(null),
                "the floor arm must still win below the floor:\n" + d1);
    }
}
