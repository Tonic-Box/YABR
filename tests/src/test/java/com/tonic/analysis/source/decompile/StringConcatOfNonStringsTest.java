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
 * A concatenation whose operands are all non-String has to keep the empty string that makes it one.
 * {@code "" + a() + b()} compiles to an indy whose recipe holds no literal, so the empty string is not in
 * the bytecode - and recovered without it, {@code a() + b()} is arithmetic: two booleans added under a
 * String signature, which does not even verify.
 */
class StringConcatOfNonStringsTest {

    private static final String[] LINES = {
            "public class BoolConcat {",
            "    static boolean a() {",
            "        return true;",
            "    }",
            "    static boolean b() {",
            "        return false;",
            "    }",
            "    static int n() {",
            "        return 7;",
            "    }",
            "    static char c() {",
            "        return 'x';",
            "    }",
            "    public static String check() {",
            "        return \"\" + a() + b() + n() + c() + \"|\" + n() + a();",
            "    }",
            "}",
    };

    @Test
    void aConcatOfNonStringOperandsKeepsItsEmptyString() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("bool-concat");
        Path src = dir.resolve("BoolConcat.java");
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("BoolConcat.class")));
        Object original = TestUtils.loadAndVerify(cf).getMethod("check").invoke(null);
        assertEquals("truefalse7x|7true", original, "the fixture itself must concatenate, not add");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "BoolConcat"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, TestUtils.loadAndVerify(cf).getMethod("check").invoke(null),
                "the re-lowered class must still concatenate:\n" + d1);
        assertEquals(d1, ClassDecompiler.decompile(cf), "decompiling must be a fixed point");
    }
}
