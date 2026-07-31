package com.tonic.analysis.source.ast.transform;

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
 * A boolean expression handed to a call reads back as a boolean. The branches materialize it as an
 * int-carrying ternary, and nothing else descended into argument lists, so the call kept
 * {@code req(n % 3 != 0 ? 0 : 1, ...)} - an int where the callee declares a boolean, which javac rejects.
 * A staged literal reaching the argument as a bare {@code 0}/{@code 1} is likewise rewritten to the boolean
 * the descriptor declares.
 */
class BooleanArgumentFoldTest {

    private static final String[] LINES = {
            "public class BoolParam {",
            "    static String out = \"\";",
            "    static void req(boolean ok, String label) { out += label + '=' + ok + ';'; }",
            "    static void f(int n) {",
            "        req(n % 3 == 0, \"m3\");",
            "        req(n > 10, \"gt\");",
            "    }",
            "    public static String check() {",
            "        out = \"\";",
            "        f(9);",
            "        f(4);",
            "        return out;",
            "    }",
            "}",
    };

    @Test
    void aBooleanArgumentStaysABoolean() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("bool-param");
        Path src = dir.resolve("BoolParam.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("BoolParam.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("m3=true;gt=false;m3=false;gt=false;", original,
                "the fixture itself must take both truth values");

        String d1 = ClassDecompiler.decompile(cf);
        assertFalse(d1.contains("? 0 : 1") || d1.contains("? 1 : 0"),
                "the argument must be recovered as the boolean expression itself:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "BoolParam"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
        assertEquals(d1, ClassDecompiler.decompile(cf), "decompiling must be a fixed point");
    }
}
