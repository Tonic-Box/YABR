package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A cast to an array type has to be lowered, like any other reference cast. Re-lowering only emitted the
 * check for a plain reference target, so {@code (String[]) objectArray} was dropped and the method returned
 * {@code Object[]} under a {@code String[]} signature - which does not verify, so the class fell out of the
 * recompile gates entirely rather than failing loudly.
 */
class ArrayCastLoweringTest {

    private static final String[] LINES = {
            "public class CastReturn {",
            "    Object[] items = new String[] {\"a\", \"b\"};",
            "    Object single = \"s\";",
            "    String[] getArray() {",
            "        return (String[]) items;",
            "    }",
            "    String getOne() {",
            "        return (String) single;",
            "    }",
            "    int[][] nested() {",
            "        int[][] grid = new int[1][2];",
            "        grid[0][1] = 2;",
            "        Object o = grid;",
            "        return (int[][]) o;",
            "    }",
            "    public static String check() {",
            "        CastReturn c = new CastReturn();",
            "        return c.getArray()[0] + c.getOne() + c.nested()[0][1];",
            "    }",
            "}",
    };

    @Test
    void aCastToAnArrayTypeSurvivesRelowering() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("array-cast");
        Path src = dir.resolve("CastReturn.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("CastReturn.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("as2", original, "the fixture itself must read through each cast");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.replaceAll("\\s+", " ").contains("return (String[]) this.items;"),
                "the array cast must be recovered:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "CastReturn"),
                "the decompiled source must recompile");
        assertTrue(TestUtils.verifies(cf, pool),
                "the re-lowered class must verify - without the cast the return type does not match");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
        assertEquals(d1, ClassDecompiler.decompile(cf), "decompiling must be a fixed point");
    }
}
