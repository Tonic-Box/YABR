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
 * A value computed in a try, protected by a user catch AND a {@code finally} whose body carries its own
 * nested try (the guarded-close idiom), returned after the construct. The finally's protective entries
 * shadow the user catch's ranges, so the node decode declined on "nested handlers past the window" and the
 * loose legacy fallback lost the method's trailing return entirely: the inlined copy's own catch rejoins
 * the continuation, putting the return block in the handler-reachable stop set where no walk could reach
 * it. The node now blanket-consumes the same-start sibling scaffolding for a finally that carries its own
 * try, and recovers the whole construct with the return in place.
 */
class FinallyWithNestedTryReturnFidelityTest {

    private static final String SOURCE =
            "import java.io.IOException;\n"
            + "import java.net.URL;\n"
            + "import java.net.URLConnection;\n"
            + "public class FinNestedTry {\n"
            + "    static int m(String cp, URL url) {\n"
            + "        URLConnection conn = null;\n"
            + "        int hash;\n"
            + "        try {\n"
            + "            conn = url.openConnection();\n"
            + "            hash = cp.hashCode() ^ (int) conn.getLastModified();\n"
            + "        } catch (IOException ex) {\n"
            + "            throw new RuntimeException(ex);\n"
            + "        } finally {\n"
            + "            if (conn != null) {\n"
            + "                try {\n"
            + "                    conn.getInputStream().close();\n"
            + "                } catch (IOException e) {}\n"
            + "            }\n"
            + "        }\n"
            + "        return hash;\n"
            + "    }\n"
            + "}\n";

    private static String d1;

    @BeforeAll
    static void compileAndDecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("fin-nested-try");
        Path src = dir.resolve("FinNestedTry.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("FinNestedTry.class")));
        d1 = ClassDecompiler.decompile(cf);
    }

    @Test
    void trailingReturnSurvives() {
        assertTrue(d1.contains("return hash"),
                "the method's trailing return must not be dropped:\n" + d1);
    }

    @Test
    void finallyKeepsItsGuardedClose() {
        int finallyAt = d1.indexOf("finally");
        assertTrue(finallyAt > 0, "the finally clause must be present:\n" + d1);
        assertTrue(d1.indexOf("close", finallyAt) > 0,
                "the guarded close must live in the finally clause:\n" + d1);
    }
}
