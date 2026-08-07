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
 * A validation guard reads as a guard clause from either bytecode layout. {@code if (!ok) throw; body} and
 * {@code if (ok) { body; return; } throw} are the same shape, and which one the structurer produces depends
 * on how the branch was laid out - so a round trip that normalizes only the already-negated form flips the
 * whole constructor inside out on its second generation, burying the body one level deeper and moving the
 * rejection to the end.
 *
 * The conditions are float comparisons on purpose: their negation cannot be written by flipping the
 * operator (that would change the answer for NaN), so each guard has to be recovered as {@code !(d > 0)}.
 * Two of them, because the second only becomes a guard clause once the first has been flattened - which
 * needs the simplifier to run again after the eliminators, as it does for a method body.
 */
class ValidationGuardOrientationTest
{

    private static final String[] LINES = {
            "public class ValidateGuard {",
            "    final float duration;",
            "    final String name;",
            "    public ValidateGuard(float duration, String name, float fps) {",
            "        if (!(duration > 0.0f)) {",
            "            throw new IllegalArgumentException(\"duration must be positive\");",
            "        }",
            "        if (!(fps > 0.0f)) {",
            "            throw new IllegalArgumentException(\"fps must be positive\");",
            "        }",
            "        this.duration = duration;",
            "        this.name = name;",
            "    }",
            "    public static String check() {",
            "        ValidateGuard v = new ValidateGuard(2.5f, \"ok\", 30.0f);",
            "        String caught = \"none\";",
            "        try {",
            "            new ValidateGuard(0.0f, \"bad\", 30.0f);",
            "        } catch (IllegalArgumentException e) {",
            "            caught = e.getMessage();",
            "        }",
            "        return v.name + ':' + caught;",
            "    }",
            "}",
    };

    @Test
    void aValidationGuardStaysAGuardClause() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("validate-guard");
        Path src = dir.resolve("ValidateGuard.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ValidateGuard.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("ok:duration must be positive", original,
                "the fixture itself must accept a positive duration and reject zero");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(d1.replaceAll("\\s+", " ").contains("if (!(duration > 0.0f)) { throw"),
                "the guard must lead, with the body flat after it:\n" + d1);

        assertTrue(TestUtils.recompileSource(cf, pool, d1, "ValidateGuard"), "the decompiled source must recompile");
        assertEquals(d1, ClassDecompiler.decompile(cf),
                "the guard's shape must survive relowering, keeping the decompile a fixed point");
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
    }
}
