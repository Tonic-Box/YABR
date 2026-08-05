package com.tonic.analysis.source.decompile;

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
 * A floating-point comparison and its negation are not interchangeable. {@code t < 0.0} is false for NaN,
 * while {@code !(t >= 0.0)} is true, and the two are compiled to different comparison opcodes ({@code dcmpg}
 * versus {@code dcmpl}) precisely to keep them apart. A round trip that renders one as the other, or lowers
 * both to the same opcode, silently changes what the method answers for NaN.
 */
class FloatComparisonNanFidelityTest
{

    private static final String[] LINES = {
            "public class NanCompare {",
            "    static boolean lt(double t) {",
            "        return t < 0.0d;",
            "    }",
            "    static boolean notGe(double t) {",
            "        return !(t >= 0.0d);",
            "    }",
            "    static boolean gt(double t) {",
            "        return t > 0.0d;",
            "    }",
            "    static boolean notLe(double t) {",
            "        return !(t <= 0.0d);",
            "    }",
            "    static boolean ltF(float t) {",
            "        return t < 0.0f;",
            "    }",
            "    static boolean notGeF(float t) {",
            "        return !(t >= 0.0f);",
            "    }",
            "    public static int check() {",
            "        double n = Double.NaN;",
            "        float f = Float.NaN;",
            "        int r = 0;",
            "        if (lt(n)) { r = r + 1; }",
            "        if (notGe(n)) { r = r + 2; }",
            "        if (gt(n)) { r = r + 4; }",
            "        if (notLe(n)) { r = r + 8; }",
            "        if (ltF(f)) { r = r + 16; }",
            "        if (notGeF(f)) { r = r + 32; }",
            "        return r;",
            "    }",
            "}",
    };

    @Test
    void aNegatedFloatComparisonKeepsItsNanAnswer() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("nan-compare");
        Path src = dir.resolve("NanCompare.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("NanCompare.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals(42, original, "the fixture itself must distinguish each comparison from its negation");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "NanCompare"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "re-lowering must keep every comparison's NaN answer:\n" + d1);

        String d2 = ClassDecompiler.decompile(cf);
        assertEquals(d1, d2, "decompiling must be a fixed point");
    }
}
