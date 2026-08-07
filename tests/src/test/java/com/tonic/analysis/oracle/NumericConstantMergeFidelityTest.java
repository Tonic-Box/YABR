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
 * A loop counter seeded by {@code M == 1 ? 1 : 0}. The merge carries the same 0 and 1 a boolean merge does,
 * so the counter was typed boolean, its int-valued arms collapsed to the bare condition, and the zero arm
 * was folded into a declaration hoisted out of the enclosing loop - the counter then kept the previous
 * iteration's value. Asserts the recompiled loop enumerates every version pair.
 */
class NumericConstantMergeFidelityTest
{

    private static final String SOURCE =
            "import java.util.ArrayList;\n"
            + "import java.util.List;\n"
            + "public class VerSet {\n"
            + "    public static List<String> build(int maxMajor, int[] versions) {\n"
            + "        List<String> out = new ArrayList<>();\n"
            + "        for (int M = 1; M <= maxMajor; M++) {\n"
            + "            int maxMinor = versions[M - 1];\n"
            + "            for (int m = M == 1 ? 1 : 0; m <= maxMinor; m++) {\n"
            + "                out.add(\"GL\" + M + m);\n"
            + "            }\n"
            + "        }\n"
            + "        return out;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("ver-set");
        Path src = dir.resolve("VerSet.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("VerSet.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "VerSet must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void everyVersionPairIsEnumerated() throws Exception
    {
        Object built = recompiledClass.getMethod("build", int.class, int[].class)
                .invoke(null, 3, new int[] {5, 1, 3});
        assertEquals("[GL11, GL12, GL13, GL14, GL15, GL20, GL21, GL30, GL31, GL32, GL33]", String.valueOf(built),
                "the counter must restart at 0 on each major version (was carried over, or typed boolean):\n" + d1);
    }
}
