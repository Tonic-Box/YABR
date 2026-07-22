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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A try/catch nested inside an if-arm, reached after a prelude the sequential staging recovers first.
 * The staging stops its handoff at the try start, but a legacy sub-walk inside the prefix (the if-arm
 * containing the try) recovers the try/catch through its own handler branch. The staging then recovered
 * the SAME try a second time over fully-processed blocks, emitting a dead {@code try {} catch (...)}
 * duplicate after the method's return - unreachable code, uncompilable. The staging now returns the
 * prefix's result alone when the prefix consumed the handler.
 */
class PrefixConsumedTryNoDuplicateFidelityTest {

    private static final String SOURCE =
            "import java.io.File;\n"
            + "public class PrefixTry {\n"
            + "    static File folder;\n"
            + "    static void fallback() {\n"
            + "        folder = new File(\"fallback\");\n"
            + "    }\n"
            + "    static File get() {\n"
            + "        if (folder == null) {\n"
            + "            String tmp = System.getProperty(\"test.tmp.dir\");\n"
            + "            if (tmp != null) {\n"
            + "                try {\n"
            + "                    folder = new File(tmp, \"natives_\" + Integer.toHexString(tmp.hashCode()));\n"
            + "                    if (!folder.exists()) {\n"
            + "                        folder.mkdir();\n"
            + "                    }\n"
            + "                } catch (Exception e) {\n"
            + "                    fallback();\n"
            + "                }\n"
            + "            } else {\n"
            + "                fallback();\n"
            + "            }\n"
            + "        }\n"
            + "        return folder;\n"
            + "    }\n"
            + "}\n";

    private static String d1;

    @BeforeAll
    static void compileAndDecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("prefix-try");
        Path src = dir.resolve("PrefixTry.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("PrefixTry.class")));
        d1 = ClassDecompiler.decompile(cf);
    }

    @Test
    void tryIsRecoveredExactlyOnce() {
        int count = 0;
        int at = -1;
        while ((at = d1.indexOf("try {", at + 1)) >= 0) {
            count++;
        }
        assertEquals(1, count, "the try must appear exactly once (no dead duplicate):\n" + d1);
    }

    @Test
    void noEmptyTryOrCodeAfterReturn() {
        assertFalse(d1.contains("try {}"),
                "no empty dead try may be emitted:\n" + d1);
        int returnAt = d1.indexOf("return folder;");
        assertFalse(returnAt >= 0 && d1.indexOf("try", returnAt) >= 0,
                "no code may follow the method's return:\n" + d1);
    }
}
