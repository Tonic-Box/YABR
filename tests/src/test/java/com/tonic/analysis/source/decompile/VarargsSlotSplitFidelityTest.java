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
 * A varargs call must keep its collapsed argument list across a round trip. javac builds the trailing array
 * through a declaration ({@code Object[] a = new Object[2]}), but a relowered layout can split the slot and
 * build it through an assignment to a separately declared local. Both forms have to fold back, or the second
 * generation of a decompile prints the desugared {@code new Object[] {...}} the first one had already
 * recovered - the same call rendered two different ways depending on where its bytecode came from.
 */
class VarargsSlotSplitFidelityTest {

    private static final String SOURCE =
            "public class VarargsSlotSplit {\n"
                    + "    static String last = \"\";\n"
                    + "    static int failures() {\n"
                    + "        return 2;\n"
                    + "    }\n"
                    + "    static int remaining() {\n"
                    + "        return 3;\n"
                    + "    }\n"
                    + "    static void report(boolean on) {\n"
                    + "        if (on) {\n"
                    + "            last = String.format(\"%d of %d\", failures(), remaining());\n"
                    + "        }\n"
                    + "    }\n"
                    + "    public static String check() {\n"
                    + "        last = \"\";\n"
                    + "        report(true);\n"
                    + "        return last;\n"
                    + "    }\n"
                    + "}\n";

    @Test
    void aVarargsArrayBuiltThroughAnAssignmentStillFolds() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("varargs-split");
        Path src = dir.resolve("VarargsSlotSplit.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        // A JDK-loaded pool, as the round-trip gate uses: resolving String.format's varargs flag
        // needs its ClassFile, and without it the lowering emits the flat argument descriptor.
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("VarargsSlotSplit.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("2 of 3", original, "the fixture itself must format both arguments");

        String d1 = ClassDecompiler.decompile(cf);
        assertFalse(d1.contains("new Object[]"), "the first decompile must fold the varargs array:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "VarargsSlotSplit"),
                "the decompiled source must recompile");
        String d2 = ClassDecompiler.decompile(cf);
        assertFalse(d2.contains("new Object[]"),
                "the array built through an assignment must fold too:\n" + d2);
        assertEquals(d1, d2, "decompiling must be a fixed point");

        assertTrue(TestUtils.recompileSource(cf, pool, d2, "VarargsSlotSplit"),
                "the second-generation source must recompile");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must format the same string");
    }
}
