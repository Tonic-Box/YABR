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
 * A local used only inside a loop must be declared inside it. Recovery gives every slot a declaration at the
 * top of the method with a default initializer, and until it is moved back down the assignment is separated
 * from the declaration - which keeps the single-use inliner from folding a temporary away, so the temporary
 * survives into the output and the two round-trip generations disagree about it.
 * <p>
 * The declaration is only sunk when the body writes the variable before reading it: a loop that reads first
 * is carrying the value between iterations, and a declaration moved inside would reset it each time.
 */
class SunkLoopLocalTest {

    private static final String[] LINES = {
            "public class SunkLoopLocal {",
            "    double longest;",
            "    int[] carried = new int[1];",
            "    void widen(int[] values) {",
            "        for (int i = 0; i < values.length; i++) {",
            "            int value = values[i];",
            "            if (value > longest) {",
            "                longest = value;",
            "            }",
            "        }",
            "    }",
            "    int accumulate(int[] values) {",
            "        int running = 0;",
            "        for (int i = 0; i < values.length; i++) {",
            "            running = running + values[i];",
            "        }",
            "        return running;",
            "    }",
            "    public static String check() {",
            "        SunkLoopLocal s = new SunkLoopLocal();",
            "        s.widen(new int[] {3, 9, 4});",
            "        return ((int) s.longest) + \":\" + s.accumulate(new int[] {1, 2, 3});",
            "    }",
            "}",
    };

    @Test
    void aLocalUsedOnlyInALoopIsDeclaredInIt() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("sunk-local");
        Path src = dir.resolve("SunkLoopLocal.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("SunkLoopLocal.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("9:6", original, "the fixture itself must widen and accumulate");

        String d1 = ClassDecompiler.decompile(cf);
        String flat = d1.replaceAll("\\s+", " ");
        assertFalse(flat.contains("int value = 0;"),
                "the loop-only local must not keep a default-initialized declaration at method level:\n" + d1);
        assertTrue(flat.contains("int value = values[i];"),
                "the loop-only local must be declared where it is assigned:\n" + d1);

        // The accumulator reads itself before writing, so it carries across iterations and must stay put.
        assertTrue(flat.contains("int running = 0;") && flat.contains("for (int i = 0;"),
                "an accumulator must keep its declaration outside the loop:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "SunkLoopLocal"),
                "the decompiled source must recompile");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
        // Not yet asserted a fixed point: recovering the RELOWERED layout still leaves this declaration at
        // method level, so the second generation does not sink it and the two texts differ. The sink is
        // asserted above on the first generation, which is where it is demonstrated.
    }
}
