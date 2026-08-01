package com.tonic.analysis.source.recovery;

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
 * A parameter keeps its signature name. The lifter forwards a slot load straight to the parameter value
 * it copies, so naming the load after its slot RENAMED the parameter everywhere - {@code float c1 = p1;}
 * then read as an identity store and its declaration was dropped, leaving every {@code c1} in the method
 * referencing a variable that does not exist.
 */
class ParameterCopyNamingTest {

    private static final String[] LINES = {
            "public class ParamCopy {",
            "    static float blend(float u, float t, float p0, float p1) {",
            "        float c1 = p1;",
            "        float c2 = (p0 + p1) * 0.5f;",
            "        return (c1 * u + c2 * t) + c1 * (1.0f - u) + c2;",
            "    }",
            "    public static String check() {",
            "        return String.valueOf(blend(0.25f, 0.5f, 2.0f, 6.0f));",
            "    }",
            "}",
    };

    @Test
    void aParameterCopyKeepsBothVariablesResolvable() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("param-copy");
        Path src = dir.resolve("ParamCopy.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("ParamCopy.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("12.0", original, "the fixture itself must blend through both locals");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "ParamCopy"),
                "every name the body reads must be declared or a parameter:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the round-tripped class must behave the same");
        assertEquals(d1, ClassDecompiler.decompile(cf), "decompiling must be a fixed point");
    }
}
