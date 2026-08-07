package com.tonic.analysis.source.recovery;

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
 * A field read keeps its place relative to the calls around it, and a floating-point guard keeps its
 * written polarity. Two round-trip defects, one fixture:
 * - {@code double a = g.time; g.setTime(x); use(a)} re-read the field at the use - a single-use load
 *     is only inlinable while nothing effectful sits between; a field read is order-sensitive even though
 *     it has no effect of its own.
 * - {@code if (t >= 0.0) A else B} oscillated with its negated twin every simplifier pass: negating a
 *     float relational cannot flip the operator (NaN), so it WRAPS a {@code !} - orienting on such a
 *     condition turns the positive form negative and the next pass swaps straight back.
 */
class FieldReadOrderFidelityTest
{

    private static final String[] LINES = {
            "public class FieldOrder {",
            "    double time;",
            "    double length = 8.0d;",
            "    public void setTime(double animationTime) {",
            "        double len = this.length;",
            "        if (animationTime >= 0.0d) {",
            "            this.time = animationTime % len;",
            "        } else {",
            "            this.time = animationTime % len + len;",
            "        }",
            "    }",
            "    public static String check() {",
            "        FieldOrder g = new FieldOrder();",
            "        g.setTime(10.0d);",
            "        double a = g.time;",
            "        g.setTime(-3.0d);",
            "        return a + \":\" + g.time;",
            "    }",
            "}",
    };

    @Test
    void aFieldReadKeepsItsPlaceAndAFloatGuardItsPolarity() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("field-order");
        Path src = dir.resolve("FieldOrder.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("FieldOrder.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("2.0:5.0", original, "the fixture itself must read the field before mutating it");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "FieldOrder"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must read the OLD value - the load must not cross the call");

        String d2 = ClassDecompiler.decompile(cf);
        String flat = d2.replaceAll("\\s+", " ");
        assertTrue(flat.indexOf("= g.time;") < flat.indexOf("setTime(-3.0d)"),
                "the read must be captured BEFORE the mutating call:\n" + d2);
        assertTrue(d2.contains("if (animationTime >= 0.0d)"),
                "the float guard must keep its positive polarity:\n" + d2);

        assertTrue(TestUtils.recompileSource(cf, pool, d2, "FieldOrder"), "d2 must recompile");
        assertEquals(d2, ClassDecompiler.decompile(cf),
                "the second generation must be the fixed point - names included");
    }
}
