package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestClassLoader;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Three declaration-kind tails that failed whole classes:
 * - An enum constant WITH A CLASS BODY compiles to an anonymous subclass that carries ACC_ENUM but
 *     extends the enum - no legal source declares {@code enum X extends Y}, so such a class emits as a
 *     plain class.
 * - A {@code package-info} class declares nothing recompilable; it is accepted as-is rather than
 *     parsed as {@code interface package-info}.
 * - A declared type beats an initializer whose resolved type degraded to Object (an unresolvable
 *     call return): {@code byte[] b = Ext.make(); b.length} must lower as arraylength, not as a field
 *     on Object.
 */
class DeclKindTailsTest
{

    @Test
    void anEnumConstantBodyEmitsAsAClass() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("enum-body");
        Path src = dir.resolve("Op.java");
        Files.writeString(src, String.join(System.lineSeparator(),
                "public enum Op {",
                "    PLUS { public int apply(int a, int b) { return a + b; } },",
                "    TIMES { public int apply(int a, int b) { return a * b; } };",
                "    public abstract int apply(int a, int b);",
                "}"));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile body = null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class"))
        {
            for (Path p : stream)
            {
                ClassFile cf = pool.loadClass(Files.readAllBytes(p));
                if (cf.getClassName().equals("Op$1"))
                {
                    body = cf;
                }
            }
        }
        assumeTrue(body != null, "constant body loaded");

        String d1 = ClassDecompiler.decompile(body);
        assertFalse(d1.contains("enum Op$1"), "a constant body must not claim to be an enum:\n" + d1);
        assertTrue(TestUtils.recompileSource(body, pool, d1, "Op$1"), "the constant body must recompile:\n" + d1);
    }

    @Test
    void aPackageInfoClassIsAcceptedAsIs() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("pkg-info");
        Path pkg = dir.resolve("pkgy");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("package-info.java"), String.join(System.lineSeparator(),
                "@Deprecated",
                "package pkgy;"));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                pkg.resolve("package-info.java").toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("pkgy").resolve("package-info.class")));
        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, cf.getClassName()), "package-info must be accepted:\n" + d1);
    }

    @Test
    void aDeclaredTypeBeatsAnUnresolvableInitializerType() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("decl-type");
        Path ext = dir.resolve("ExtSupplier.java");
        Path src = dir.resolve("UseExt.java");
        Files.writeString(ext, String.join(System.lineSeparator(),
                "public class ExtSupplier {",
                "    public static byte[] make() { return new byte[] {1, 2, 3}; }",
                "}"));
        Files.writeString(src, String.join(System.lineSeparator(),
                "public class UseExt {",
                "    public static int check() {",
                "        byte[] bytes = ExtSupplier.make();",
                "        return bytes.length * 10 + bytes[2];",
                "    }",
                "}"));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                ext.toString(), src.toString()) == 0, "fixture compiled");

        // The pool deliberately holds ONLY the user class: the callee's return type cannot resolve, so
        // the initializer's value would degrade to Object without the declared-type override.
        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("UseExt.class")));
        byte[] extBytes = Files.readAllBytes(dir.resolve("ExtSupplier.class"));

        Object original = invokeCheck(cf.write(), extBytes);
        assertEquals(33, original, "the fixture itself must read the array");

        String d1 = ClassDecompiler.decompile(cf);
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "UseExt"),
                "the declared byte[] must carry - no field lookup on Object:\n" + d1);
        assertEquals(original, invokeCheck(cf.write(), extBytes), "the round-tripped class must behave the same");
    }

    private static Object invokeCheck(byte[] userBytes, byte[] extBytes) throws Exception
    {
        TestClassLoader loader = new TestClassLoader();
        loader.defineClass("ExtSupplier", extBytes);
        Class<?> clazz = loader.defineClass("UseExt", userBytes);
        return clazz.getMethod("check").invoke(null);
    }
}
