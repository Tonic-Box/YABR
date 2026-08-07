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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A call whose result stays on the operand stack across another statement must not be rendered at its use
 * site. Doing so prints the two calls in the opposite order to the one the program performs, which is a
 * silent behaviour change - the decompiled source no longer describes the class it came from.
 *
 * The shape arises on the second generation of a round trip: this project's own lowering may leave a
 * declared local's value on the stack across the next statement, which javac never does. So the fixture is
 * compiled, decompiled, relowered, and only then decompiled again - and the trace it builds ({@code r} for
 * the read, {@code f} for the flush) shows the order the calls actually run in, so the check is on observed
 * behaviour rather than on text alone.
 */
class StackResidentCallOrderFidelityTest
{

    private static final String SOURCE =
            "public class StackResidentOrder {\n"
                    + "    static StringBuilder trace = new StringBuilder();\n"
                    + "    static String readToken() {\n"
                    + "        trace.append(\"r\");\n"
                    + "        return \"T\";\n"
                    + "    }\n"
                    + "    static void flush() {\n"
                    + "        trace.append(\"f\");\n"
                    + "    }\n"
                    + "    static String consume(String s) {\n"
                    + "        trace.append(s);\n"
                    + "        return s;\n"
                    + "    }\n"
                    + "    public static String check() {\n"
                    + "        trace = new StringBuilder();\n"
                    + "        String token = readToken();\n"
                    + "        flush();\n"
                    + "        consume(token);\n"
                    + "        return trace.toString();\n"
                    + "    }\n"
                    + "}\n";

    @Test
    void aStackResidentResultKeepsItsPositionInTheStatementSequence() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("stack-resident");
        Path src = dir.resolve("StackResidentOrder.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("StackResidentOrder.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("rfT", original, "the fixture itself must read before it flushes");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "StackResidentOrder"),
                "the decompiled source must recompile");

        String d2 = ClassDecompiler.decompile(cf);
        String flat = d2.replaceAll("\\s+", " ");
        assertFalse(flat.contains("consume(StackResidentOrder.readToken())"),
                "the read must not be folded into the later call:\n" + d2);
        assertTrue(flat.contains("= StackResidentOrder.readToken(); StackResidentOrder.flush();"),
                "the read must keep its position ahead of the flush:\n" + d2);

        assertTrue(TestUtils.recompileSource(cf, pool, d2, "StackResidentOrder"),
                "the second-generation source must recompile");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must perform its calls in the same order");
    }
}
