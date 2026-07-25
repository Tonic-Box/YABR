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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A grow-buffer retry loop whose body is a try-with-resources followed by a result dispatch - the lwjgl
 * {@code getPath}/{@code extract} shape. The try node's join is the inlined finally copy, which the
 * delegate recovery consumes; emitting the consumed join as empty dropped its whole dominator subtree,
 * so the loop lost BOTH its exits and the realloc arm: an infinite loop over just the try, followed by
 * unreachable statements. The consumed join's subtree is now structured even though the join's own
 * statements are spent.
 */
class TwrRetryLoopFidelityTest {

    private static final String SOURCE =
            "public class Retry {\n"
            + "    static int calls = 0;\n"
            + "    static class Stack implements AutoCloseable {\n"
            + "        public void close() {}\n"
            + "    }\n"
            + "    static Stack push() { return new Stack(); }\n"
            + "    static int fill(int[] buffer) {\n"
            + "        calls++;\n"
            + "        return buffer.length >= 24 ? 5 : 0;\n"
            + "    }\n"
            + "    static int err(int len) {\n"
            + "        return len == 0 ? 122 : 0;\n"
            + "    }\n"
            + "    public static String get() {\n"
            + "        int maxLen = 16;\n"
            + "        int[] buffer = new int[maxLen];\n"
            + "        while (true) {\n"
            + "            int len;\n"
            + "            int e;\n"
            + "            try (Stack stack = push()) {\n"
            + "                len = fill(buffer);\n"
            + "                e = err(len);\n"
            + "            }\n"
            + "            if (e == 0) {\n"
            + "                String s = len == 0 ? null : (\"len\" + len);\n"
            + "                return s;\n"
            + "            }\n"
            + "            if (e != 122) {\n"
            + "                return null;\n"
            + "            }\n"
            + "            maxLen = maxLen * 3 / 2;\n"
            + "            buffer = new int[maxLen];\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;

    @BeforeAll
    static void compileAndDecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("twr-retry");
        Path src = dir.resolve("Retry.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("Retry.class")));
        d1 = ClassDecompiler.decompile(cf);
    }

    @Test
    void loopKeepsBothExitsAndTheRetryArm() {
        assertTrue(d1.contains("while ("),
                "the retry loop must be recovered as a loop:\n" + d1);
        assertTrue(d1.contains("return null;"),
                "the fatal-error exit must survive inside the loop:\n" + d1);
        assertTrue(d1.contains("maxLen * 3 / 2"),
                "the grow-and-retry arm must survive:\n" + d1);
        int loopAt = d1.indexOf("while (");
        int growAt = d1.indexOf("maxLen * 3 / 2");
        assertTrue(growAt > loopAt, "the retry arm must be inside the loop:\n" + d1);
    }
}
