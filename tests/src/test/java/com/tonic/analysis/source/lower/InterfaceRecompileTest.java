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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * An interface's default and static method bodies relower like a class's. Recompilation refused every
 * non-class type, leaving interfaces outside the round-trip gates entirely; abstract members have no
 * bodies and pass through untouched.
 */
class InterfaceRecompileTest
{

    private static final String[] IFACE_LINES = {
            "public interface Sized {",
            "    int size();",
            "    default boolean isEmpty() {",
            "        return size() == 0;",
            "    }",
            "    default String describe(String label) {",
            "        int s = size();",
            "        if (s > 100) {",
            "            return label + \":big\";",
            "        }",
            "        return label + ':' + s;",
            "    }",
            "    static Sized of(int n) {",
            "        return () -> n;",
            "    }",
            "}",
    };

    private static final String[] LINES = {
            "public class SizedUser {",
            "    public static String check() {",
            "        Sized none = Sized.of(0);",
            "        Sized some = Sized.of(7);",
            "        Sized big = Sized.of(500);",
            "        return \"\" + none.isEmpty() + some.isEmpty() + some.describe(\"s\") + big.describe(\"b\");",
            "    }",
            "}",
    };

    @Test
    void anInterfacesBodiesRelower() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("iface-recompile");
        Path ifaceSrc = dir.resolve("Sized.java");
        Path src = dir.resolve("SizedUser.java");
        Files.writeString(ifaceSrc, String.join(System.lineSeparator(), IFACE_LINES));
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                ifaceSrc.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile iface = null;
        List<byte[]> others = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class"))
        {
            for (Path p : stream)
            {
                byte[] bytes = Files.readAllBytes(p);
                ClassFile cf = pool.loadClass(bytes);
                if (cf.getClassName().equals("Sized"))
                {
                    iface = cf;
                }
                else
                {
                    others.add(bytes);
                    names.add(cf.getClassName().replace('/', '.'));
                }
            }
        }
        assumeTrue(iface != null, "interface loaded");

        Object original = invokeCheck(iface.write(), others, names);
        assertEquals("truefalses:7b:big", original, "the fixture itself must run both default bodies");

        String d1 = ClassDecompiler.decompile(iface);
        assertTrue(TestUtils.recompileSource(iface, pool, d1, "Sized"), "an interface must recompile:\n" + d1);
        assertEquals(original, invokeCheck(iface.write(), others, names),
                "the round-tripped interface must behave the same");
    }

    private static Object invokeCheck(byte[] ifaceBytes, List<byte[]> others, List<String> names)
            throws Exception
            {
        TestClassLoader loader = new TestClassLoader();
        loader.defineClass("Sized", ifaceBytes);
        Class<?> user = null;
        for (int i = 0; i < others.size(); i++)
        {
            Class<?> defined = loader.defineClass(names.get(i), others.get(i));
            if ("SizedUser".equals(names.get(i)))
            {
                user = defined;
            }
        }
        return user.getMethod("check").invoke(null);
    }
}
