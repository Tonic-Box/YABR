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
 * An array initializer of arrays allocates an array OF ARRAYS. {@code new int[][] {{1, 2}}} written with an
 * initializer and no explicit lengths carried no dimension count into the expression, which then assumed
 * one - so the outer array was allocated as {@code int[]} and the inner arrays stored into it, which does
 * not verify. The type has to travel from the parser, and the allocation has to take the component type
 * (one index down) rather than the base element type.
 */
class NestedArrayInitializerTest
{

    private static final String[] LINES = {
            "public class NestedInit {",
            "    static int[][] grid() {",
            "        return new int[][] {{1, 2}, {3, 4}};",
            "    }",
            "    static String[][] names() {",
            "        return new String[][] {{\"a\"}, {\"b\", \"c\"}};",
            "    }",
            "    static int[][][] deep() {",
            "        return new int[][][] {{{5}}};",
            "    }",
            "    static int[] flat() {",
            "        return new int[] {9, 8};",
            "    }",
            "    public static String check() {",
            "        return \"\" + grid()[0][1] + grid()[1][0] + names()[1][1] + deep()[0][0][0] + flat()[1];",
            "    }",
            "}",
    };

    @Test
    void anInitializerOfArraysAllocatesAnArrayOfArrays() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("nested-init");
        Path src = dir.resolve("NestedInit.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("NestedInit.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("23c58", original, "the fixture itself must read through every dimension");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "NestedInit"),
                "the decompiled source must recompile:\n" + d1);
        assertTrue(TestUtils.verifies(cf, pool),
                "the re-lowered class must verify - an outer array of the base type does not:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must read the same values:\n" + d1);
        assertEquals(d1, ClassDecompiler.decompile(cf), "decompiling must be a fixed point");
    }
}
