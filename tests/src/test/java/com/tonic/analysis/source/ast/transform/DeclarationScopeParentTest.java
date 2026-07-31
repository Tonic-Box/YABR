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
 * A variable used only inside a loop or a branch stays declared there. Whether a variable escapes its block
 * is answered by walking parent links up from each use, so a use whose parent link was never set - or was
 * cleared when a transform rebuilt the node around it - reads as living outside every block it is actually
 * inside. The repair then hoists a perfectly well scoped declaration to the top of the method and leaves a
 * default initializer behind, which is how the second generation of a round trip stopped matching the first.
 * <p>
 * The loop variable is read only from inside an {@code if} condition, which is the position that goes wrong:
 * a condition replaced by one built around the old one leaves the old one - and everything under it - looking
 * detached, so the variable read there appears to be used outside the loop.
 */
class DeclarationScopeParentTest {

    private static final String[] LINES = {
            "public class ScopedDecl {",
            "    static int countUntilFlag(int[] values) {",
            "        boolean seen = false;",
            "        int count = 0;",
            "        for (int v : values) {",
            "            if (!seen) {",
            "                count++;",
            "            }",
            "            if (v < 0) {",
            "                seen = true;",
            "            }",
            "        }",
            "        return count;",
            "    }",
            "    static String classify(int n) {",
            "        if (n > 100) {",
            "            String big = \"big\";",
            "            return big + n;",
            "        }",
            "        int doubled = n * 2;",
            "        if (doubled < 10) {",
            "            return \"small\";",
            "        }",
            "        return \"mid\";",
            "    }",
            "    public static String check() {",
            "        return countUntilFlag(new int[] {1, 2, -1, 3}) + classify(200) + classify(3) + classify(50);",
            "    }",
            "}",
    };

    @Test
    void aDeclarationUsedOnlyInABlockStaysInIt() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("scoped-decl");
        Path src = dir.resolve("ScopedDecl.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ScopedDecl.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("3big200smallmid", original, "the fixture itself must take each branch");

        String d1 = ClassDecompiler.decompile(cf);
        String flat = d1.replaceAll("\\s+", " ");
        assertFalse(flat.contains("int v = 0;") || flat.contains("String big = null;"),
                "no variable may be hoisted out of the block that owns it:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "ScopedDecl"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");

        String d2 = ClassDecompiler.decompile(cf);
        String flat2 = d2.replaceAll("\\s+", " ");
        assertFalse(flat2.contains("int v = 0;") || flat2.contains("String big = null;"),
                "nor may the second generation hoist one:\n" + d2);
        assertEquals(d1, d2, "decompiling must be a fixed point");
    }
}
