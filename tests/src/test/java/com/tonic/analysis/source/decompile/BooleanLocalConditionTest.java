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
 * A branch on a boolean local reads as the variable itself, not as a comparison against an int. A boolean
 * is an int once it is in a register, so nothing in the value's own type says otherwise - the declared type
 * has to be consulted, and the condition came out as {@code if (flag == 0)}, which javac rejects.
 *
 * The variable is carried across a loop on purpose. Such a variable is read through its merge phi, which
 * carries no bytecode offset of its own, so its recorded range collapsed to the single store instruction -
 * covering neither the branch that reads it nor anything else. That makes the second generation the sharper
 * assertion here: the first can lean on javac's range, the second only works once the range emitted by the
 * round trip spans the variable too.
 */
class BooleanLocalConditionTest
{

    private static final String[] LINES = {
            "import java.util.ArrayList;",
            "import java.util.List;",
            "public class BoolCond {",
            "    static String join(List<String> parts) {",
            "        StringBuilder out = new StringBuilder();",
            "        boolean separate = false;",
            "        for (String part : parts) {",
            "            if (separate) {",
            "                out.append(',');",
            "            } else {",
            "                separate = true;",
            "            }",
            "            out.append(part);",
            "        }",
            "        return out.toString();",
            "    }",
            "    static int countUntilFlag(int[] values) {",
            "        boolean seen = false;",
            "        int count = 0;",
            "        for (int i = 0; i < values.length; i++) {",
            "            if (!seen) {",
            "                count++;",
            "            }",
            "            if (values[i] < 0) {",
            "                seen = true;",
            "            }",
            "        }",
            "        return count;",
            "    }",
            "    public static String check() {",
            "        List<String> parts = new ArrayList<>();",
            "        parts.add(\"a\");",
            "        parts.add(\"b\");",
            "        parts.add(\"c\");",
            "        return join(parts) + ':' + countUntilFlag(new int[] {1, 2, -1, 3});",
            "    }",
            "}",
    };

    @Test
    void aBranchOnABooleanLocalTestsTheVariable() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("bool-cond");
        Path src = dir.resolve("BoolCond.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("BoolCond.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("a,b,c:3", original, "the fixture itself must separate and stop counting at the flag");

        String d1 = ClassDecompiler.decompile(cf);
        assertFalse(d1.contains("separate ==") || d1.contains("seen =="),
                "a boolean local must not be compared against an int literal:\n" + d1);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "BoolCond"), "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");

        String d2 = ClassDecompiler.decompile(cf);
        assertFalse(d2.contains("separate ==") || d2.contains("seen =="),
                "the emitted debug range must span the variable, so generation 2 reads it as a boolean too:\n" + d2);
        assertEquals(d1, d2, "decompiling must be a fixed point");
    }
}
