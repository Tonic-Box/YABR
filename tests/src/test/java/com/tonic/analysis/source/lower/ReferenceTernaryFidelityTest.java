package com.tonic.analysis.source.lower;

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
 * A conditional over references survives a round trip as a conditional. References merge on the operand
 * stack exactly like ints, but reference-typed phis were excluded from stack residency - so relowering a
 * ternary spilled each arm to a slot, and the next decompile read the staging back as a declaration whose
 * type is the JOIN of the arms ({@code Object local1 = null; if (...) { local1 = ...; } return local1;})
 * rather than the conditional that was written.
 */
class ReferenceTernaryFidelityTest {

    private static final String[] LINES = {
            "import java.util.ArrayList;",
            "import java.util.List;",
            "public class RefTern {",
            "    List<String> rotations = new ArrayList<>();",
            "    List<String> empty;",
            "    RefTern() { rotations.add(\"x\"); rotations.add(\"y\"); }",
            "    Object[] getRotations() {",
            "        return this.rotations != null ? this.rotations.toArray() : null;",
            "    }",
            "    Object[] getEmpty() {",
            "        return this.empty != null ? this.empty.toArray() : null;",
            "    }",
            "    String pick(boolean first, String a, String b) {",
            "        return first ? a : b;",
            "    }",
            "    public static String check() {",
            "        RefTern r = new RefTern();",
            "        return r.getRotations()[1] + \"\" + r.getEmpty() + r.pick(true, \"L\", \"R\") + r.pick(false, \"L\", \"R\");",
            "    }",
            "}",
    };

    @Test
    void aReferenceTernaryStaysATernary() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("ref-tern");
        Path src = dir.resolve("RefTern.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("RefTern.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("ynullLR", original, "the fixture itself must take both arms");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.contains("?"), "each conditional must be recovered as one:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "RefTern"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");

        String d2 = ClassDecompiler.decompile(cf);
        assertFalse(d2.contains("Object local"),
                "no arm may be staged through a join-typed local:\n" + d2);
        assertEquals(d1, d2, "decompiling must be a fixed point");
    }
}
