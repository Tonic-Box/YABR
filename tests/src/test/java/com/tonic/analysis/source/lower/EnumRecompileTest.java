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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An enum's method bodies relower like any other class's. Recompilation refused every non-class type
 * outright, leaving all enums outside the round-trip gates; the declared methods carry ordinary bodies,
 * so only the synthetic machinery (constructors with their name/ordinal prefix, {@code <clinit>},
 * {@code $VALUES}) keeps its original bytecode.
 */
class EnumRecompileTest
{

    private static final String[] LINES = {
            "public enum Signal {",
            "    OFF(0), LOW(3), HIGH(9);",
            "    private final int level;",
            "    Signal(int level) { this.level = level; }",
            "    public int strength() {",
            "        int doubled = this.level * 2;",
            "        if (doubled > 10) {",
            "            return 10;",
            "        }",
            "        return doubled;",
            "    }",
            "    public static String check() {",
            "        StringBuilder sb = new StringBuilder();",
            "        for (Signal s : values()) {",
            "            sb.append(s.name()).append(s.strength());",
            "        }",
            "        return sb.toString();",
            "    }",
            "}",
    };

    @Test
    void anEnumsMethodBodiesRelower() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("enum-recompile");
        Path src = dir.resolve("Signal.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("Signal.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("OFF0LOW6HIGH10", original, "the fixture itself must cap the doubled level");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "Signal"), "an enum must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped enum must behave the same");
    }
}
