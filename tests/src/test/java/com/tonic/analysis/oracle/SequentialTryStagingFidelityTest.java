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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fidelity of a method staged linearly around its tries: a handler-free prelude, a first try, code between
 * the tries, a second try, and a structured continuation. The region hand-off stages such a method - the
 * handler-free spans are structured by the reaching-condition engine and each try is delegated to the
 * try/catch recovery - instead of sending the whole method to the legacy walk. Asserts a round-trip fixed
 * point, that both tries and all spans survive, and that the recompiled bytecode executes every path.
 */
class SequentialTryStagingFidelityTest {

    private static final String SOURCE =
            "public class SeqTry {\n"
            + "    public static String m(String a, String b, boolean flag) {\n"
            + "        int prefix = a.length() + b.length();\n"
            + "        if (prefix == 0) {\n"
            + "            return \"empty\";\n"
            + "        }\n"
            + "        int first;\n"
            + "        try {\n"
            + "            first = Integer.parseInt(a);\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            return \"badA\";\n"
            + "        }\n"
            + "        int between = first * 2;\n"
            + "        if (flag) {\n"
            + "            between++;\n"
            + "        }\n"
            + "        int second;\n"
            + "        try {\n"
            + "            second = Integer.parseInt(b);\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            return \"badB\";\n"
            + "        }\n"
            + "        int sum = between + second;\n"
            + "        if (sum < 0) {\n"
            + "            return \"neg\";\n"
            + "        }\n"
            + "        return \"\" + sum;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static String d3;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("seq-try-staging");
        Path src = dir.resolve("SeqTry.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("SeqTry.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "SeqTry must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
        ClassFile recovered2 = Recompile.recompiledClone(recovered, TestUtils.emptyPool());
        assertNotNull(recovered2, "the recompiled SeqTry must recompile again");
        d3 = ClassDecompiler.decompile(recovered2);
    }

    @Test
    void everySpanSurvivesBothDecompiles() {
        for (String d : new String[] {d1, d2}) {
            assertEquals(2, countOccurrences(d, "catch (NumberFormatException"),
                    "both tries must survive as separate catch clauses:\n" + d);
            assertTrue(d.contains("\"badA\""), "the first catch body must survive:\n" + d);
            assertTrue(d.contains("\"badB\""), "the second catch body must survive:\n" + d);
            assertTrue(d.contains("\"empty\""), "the prelude guard must survive:\n" + d);
            assertTrue(d.contains("first * 2"), "the span between the tries must survive:\n" + d);
            assertTrue(d.contains("\"neg\""), "the continuation guard must survive:\n" + d);
        }
    }

    @Test
    void recoveredFormIsStable() {
        assertEquals(d2, d3, "the staged recovery must reach a fixed point on its own output");
    }

    @Test
    void everyPathExecutes() throws Exception {
        assertEquals("empty", invoke("", "", false), "the prelude's early return must execute");
        assertEquals("badA", invoke("x", "2", false), "the first catch path must execute");
        assertEquals("badB", invoke("3", "y", false), "the second catch path must execute");
        assertEquals("8", invoke("3", "2", false), "the straight path must compute through all spans");
        assertEquals("9", invoke("3", "2", true), "the between-tries branch must execute");
        assertEquals("neg", invoke("3", "-99", false), "the continuation guard must execute");
    }

    private static String invoke(String a, String b, boolean flag) throws Exception {
        return (String) recompiledClass
                .getDeclaredMethod("m", String.class, String.class, boolean.class)
                .invoke(null, a, b, flag);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
