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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A compound boolean in value position lowers as ONE branch tree into a shared {@code 1}/{@code 0} pair,
 * the way a compiler does. Lowering each {@code &&}/{@code ||} as its own materialized merge instead makes
 * the parent branch on a value the branches just built - dead {@code iconst_0; ifne} pairs in the code -
 * and the next decompile reads that back as nested ifs staging a flag, including the un-Java
 * {@code if (x == null ? 1 : 0)}, rather than the compound the source wrote.
 */
class ShortCircuitValueLoweringTest {

    private static final String[] LINES = {
            "public class ShortValue {",
            "    Object composer;",
            "    boolean simple(Object t) { return t != null; }",
            "    boolean upper(Object t) { return t == composer; }",
            "    public boolean contains(Object target) {",
            "        return simple(target) && (this.composer == null || !upper(target));",
            "    }",
            "    public boolean either(Object a, Object b) {",
            "        boolean found = a != null || b != null;",
            "        return found;",
            "    }",
            "    public static String check() {",
            "        ShortValue v = new ShortValue();",
            "        Object o = new Object();",
            "        v.composer = o;",
            "        return \"\" + v.contains(o) + v.contains(new Object()) + v.contains(null)",
            "                + v.either(null, o) + v.either(null, null);",
            "    }",
            "}",
    };

    @Test
    void aCompoundBooleanValueSurvivesTheRoundTrip() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("short-value");
        Path src = dir.resolve("ShortValue.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ShortValue.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("falsetruefalsetruefalse", original, "the fixture itself must exercise each term");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "ShortValue"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");

        String d2 = ClassDecompiler.decompile(cf);
        assertTrue(d2.contains("&&") && d2.contains("||"),
                "the compounds must read back as compounds:\n" + d2);
        assertFalse(d2.contains("? 1 : 0"),
                "no branch may test an int-materialized boolean:\n" + d2);
        assertFalse(d2.replaceAll("\\s+", " ").contains("= false; if ("),
                "no compound may be staged through a flag:\n" + d2);
        assertEquals(d1, d2,
                "decompiling must be a fixed point - `boolean found = ...` keeps its name only while"
                        + " the variable's store survives relowering");
    }
}
