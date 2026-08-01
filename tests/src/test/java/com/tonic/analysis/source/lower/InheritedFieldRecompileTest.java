package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestClassLoader;
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
 * A field inherited from a superclass resolves when the subclass relowers. The current-class resolution
 * stopped at the parsed declaration's own fields and gave up, so any class touching {@code this.inherited}
 * failed to recompile at all; not declared here does not mean not a field - the superclass chain owns it.
 */
class InheritedFieldRecompileTest {

    private static final String[] BASE_LINES = {
            "public class BaseHolder {",
            "    protected String label = \"base\";",
            "    protected int count;",
            "}",
    };

    private static final String[] LINES = {
            "public class SubUser extends BaseHolder {",
            "    public String bump() {",
            "        this.count = this.count + 2;",
            "        this.label = this.label + this.count;",
            "        return this.label;",
            "    }",
            "    public static String check() {",
            "        SubUser s = new SubUser();",
            "        s.bump();",
            "        return s.bump();",
            "    }",
            "}",
    };

    @Test
    void anInheritedFieldResolvesOnRelowering() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("inherited-field");
        Path baseSrc = dir.resolve("BaseHolder.java");
        Path src = dir.resolve("SubUser.java");
        Files.writeString(baseSrc, String.join(System.lineSeparator(), BASE_LINES));
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                baseSrc.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        byte[] baseBytes = Files.readAllBytes(dir.resolve("BaseHolder.class"));
        pool.loadClass(baseBytes);
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("SubUser.class")));

        Object original = invokeCheck(cf.write(), baseBytes);
        assertEquals("base24", original, "the fixture itself must accumulate through the inherited fields");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "SubUser"),
                "a class using inherited fields must recompile:\n" + d1);
        assertEquals(original, invokeCheck(cf.write(), baseBytes),
                "the round-tripped class must behave the same");
    }

    private static Object invokeCheck(byte[] subBytes, byte[] baseBytes) throws Exception {
        TestClassLoader loader = new TestClassLoader();
        loader.defineClass("BaseHolder", baseBytes);
        Class<?> clazz = loader.defineClass("SubUser", subBytes);
        return clazz.getMethod("check").invoke(null);
    }
}
