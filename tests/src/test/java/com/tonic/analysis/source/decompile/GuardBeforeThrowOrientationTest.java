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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A rejection guard followed by the normal path must keep the guard leading, from either layout. Which of a
 * {@code return} and a {@code throw} the structurer leaves as the branch's fall-through depends on the
 * bytecode it recovered from, so a decompile that only normalizes one of the two inverts the guard on the
 * second generation of a round trip: {@code if (opened) throw; ...} becomes {@code if (!opened) { ... }} with
 * the throw pushed to the end. Same meaning, but no longer a fixed point, and the rejection case stops being
 * the thing the reader sees first.
 */
class GuardBeforeThrowOrientationTest
{

    private static final String SOURCE = String.join("\n",
            "public class GuardBeforeThrow {",
            "    boolean opened;",
            "    String stream = \"s\";",
            "    String open() {",
            "        if (opened) {",
            "            throw new IllegalStateException(\"already\");",
            "        }",
            "        opened = true;",
            "        return stream;",
            "    }",
            "    public static String check() {",
            "        return new GuardBeforeThrow().open();",
            "    }",
            "}",
            "");

    @Test
    void theRejectionGuardStaysLeadingAcrossGenerations() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("guard-throw");
        Path src = dir.resolve("GuardBeforeThrow.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("GuardBeforeThrow.class")));
        assertEquals("s", TestUtils.loadAndVerify(cf).getMethod("check").invoke(null));

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.replaceAll("\\s+", " ").contains("if (this.opened) { throw"),
                "the guard must lead in the first decompile:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "GuardBeforeThrow"), "the decompiled source must recompile");
        assertEquals(d1, ClassDecompiler.decompile(cf),
                "the guard's orientation must survive relowering, keeping the decompile a fixed point");
        assertEquals("s", TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }
}
