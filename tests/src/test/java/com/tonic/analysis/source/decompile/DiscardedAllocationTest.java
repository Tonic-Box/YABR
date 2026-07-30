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
 * Constructing an object whose reference is then discarded is still a statement: the constructor runs, and
 * whatever it writes or throws happens. Recovery cached the allocation's expression against a use that did
 * not exist, so it never became a statement and the allocation vanished - leaving a method empty and, where
 * the constructor was the thing that threw, a {@code try} block with nothing in it.
 */
class DiscardedAllocationTest {

    private static final String[] LINES = {
            "public class DiscardNew {",
            "    static StringBuilder trace = new StringBuilder();",
            "    DiscardNew(int n) {",
            "        trace.append(n);",
            "        if (n < 0) {",
            "            throw new IllegalStateException(\"neg\");",
            "        }",
            "    }",
            "    static void plain() {",
            "        new DiscardNew(1);",
            "    }",
            "    public static String check() {",
            "        trace = new StringBuilder();",
            "        plain();",
            "        String caught = \"none\";",
            "        try {",
            "            new DiscardNew(-1);",
            "        } catch (IllegalStateException e) {",
            "            caught = e.getMessage();",
            "        }",
            "        return trace.toString() + ':' + caught;",
            "    }",
            "}",
    };

    @Test
    void anAllocationWhoseResultIsDiscardedStillRuns() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("discard-new");
        Path src = dir.resolve("DiscardNew.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("DiscardNew.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("1-1:neg", original, "the fixture itself must run both constructors");

        String d1 = ClassDecompiler.decompile(cf);
        String flat = d1.replaceAll("\\s+", " ");
        assertTrue(flat.contains("static void plain() { new DiscardNew(1); }"),
                "a discarded allocation must survive as a statement:\n" + d1);
        assertTrue(flat.contains("try { new DiscardNew(-1); }"),
                "the allocation inside the try must survive too:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "DiscardNew"),
                "the decompiled source must recompile");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must still run both constructors:\n" + d1);
    }
}
