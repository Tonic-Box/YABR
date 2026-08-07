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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A try-with-resources behind a validation guard survives when the guard's arm declares a local. That arm
 * recovers as a full if/else diamond - the continuation buried in the else - so the resource fold saw a
 * two-statement method and gave up, and the generic fallback emitted an EMPTY try with handler scaffolding
 * referencing a variable that was never declared. With the arm terminal, guard-plus-continuation is the
 * same program, so the trailing else is flattened back to the top level before folding.
 *
 * The synthesized {@code close()} calls carried the resource's SIMPLE type name as their invoke owner
 * (an unresolvable class at link time); the owner now resolves through the imports.
 */
class GuardedTwrRecoveryTest
{

    private static final String[] LINES = {
            "import java.io.ByteArrayInputStream;",
            "import java.io.IOException;",
            "import java.io.InputStream;",
            "import java.util.Scanner;",
            "public class GuardedTwr {",
            "    static String quote(CharSequence s) { return \"'\" + s + \"'\"; }",
            "    public static String load(String text) {",
            "        InputStream stream = new ByteArrayInputStream(text.getBytes());",
            "        if (stream == null) {",
            "            String q = quote(text);",
            "            throw new RuntimeException(\"resource not found: \" + q);",
            "        }",
            "        try (Scanner scanner = new Scanner(stream, \"UTF-8\")) {",
            "            scanner.useDelimiter(\"ZZZ\");",
            "            return scanner.next();",
            "        }",
            "    }",
            "    static int closes = 0;",
            "    static InputStream open(int v) { closes++; return new ByteArrayInputStream(new byte[] {(byte) v}); }",
            "    static int use(boolean deep) throws IOException {",
            "        try (InputStream a = open(5); InputStream b = open(9)) {",
            "            int v = a.read() + b.read();",
            "            if (deep) {",
            "                try (InputStream c = open(11)) {",
            "                    return v + c.read();",
            "                }",
            "            }",
            "            return v;",
            "        }",
            "    }",
            "    public static String check() throws IOException {",
            "        closes = 0;",
            "        return load(\"payload\") + ':' + (use(false) * 13 + use(true)) + ':' + closes;",
            "    }",
            "}",
    };

    @Test
    void aGuardedTryWithResourcesKeepsItsBody() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("guarded-twr");
        Path src = dir.resolve("GuardedTwr.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("GuardedTwr.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("payload:207:5", original, "the fixture itself must read through each resource");

        String d1 = ClassDecompiler.decompile(cf);
        assertFalse(d1.replaceAll("\\s+", " ").contains("try {}"),
                "no try body may be emptied by the guard's absorbed continuation:\n" + d1);
        assertTrue(d1.contains("scanner.next()"), "the resource body must survive:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "GuardedTwr"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same - synthesized closes must link");
    }
}
