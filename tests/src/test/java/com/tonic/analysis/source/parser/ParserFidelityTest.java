package com.tonic.analysis.source.parser;

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
 * Two source shapes every decompile emits and the parser rejected:
 * <ul>
 * <li>Nested type arguments close with {@code >>} (or {@code >>>}), one shift token from the lexer; the
 *     type-argument close now splits it, consuming one angle and leaving the rest for the outer list.</li>
 * <li>{@code (double) -(x)} is a cast beyond doubt - a parenthesized primitive cannot be an operand - but
 *     the cast detector's follow-set had no sign tokens, so the whole expression fell into the primary
 *     parser and died on the primitive keyword.</li>
 * </ul>
 */
class ParserFidelityTest {

    private static final String[] LINES = {
            "import java.util.ArrayList;",
            "import java.util.HashMap;",
            "import java.util.List;",
            "import java.util.Map;",
            "public class ParseShapes {",
            "    Map<String, List<String>> groups = new HashMap<>();",
            "    public String add(String k, String v) {",
            "        List<String> bucket = groups.get(k);",
            "        if (bucket == null) {",
            "            bucket = new ArrayList<>();",
            "            groups.put(k, bucket);",
            "        }",
            "        bucket.add(v);",
            "        return k + \"=\" + bucket.size();",
            "    }",
            "    public static long scale(long a, long b) {",
            "        return (long) (int) ((double) -(a - b) * 1.5d);",
            "    }",
            "    public static String check() {",
            "        ParseShapes p = new ParseShapes();",
            "        p.add(\"g\", \"1\");",
            "        return p.add(\"g\", \"2\") + \":\" + scale(2L, 8L);",
            "    }",
            "}",
    };

    @Test
    void nestedGenericsAndSignedPrimitiveCastsReparse() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("parse-shapes");
        Path src = dir.resolve("ParseShapes.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ParseShapes.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("g=2:9", original, "the fixture itself must group and scale");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "ParseShapes"),
                "the decompiled source must reparse and recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }
}
