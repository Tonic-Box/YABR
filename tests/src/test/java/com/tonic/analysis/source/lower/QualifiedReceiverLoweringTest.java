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
 * Two receiver misclassifications that failed whole classes:
 * - A STATIC ARRAY FIELD used as a receiver ({@code axisNames.length}, {@code axisNames[i]}) was taken
 *     for a class name - the class-name test required the field to be reference-typed, so an array-typed
 *     field failed it and {@code length} resolved as a field on a class that does not exist.
 * - A fully qualified NESTED-CLASS chain ({@code pkg.Outer.Inner.create(...)}) parses as nested field
 *     accesses; the call lowered it as a value receiver and died resolving {@code Inner} as a field of
 *     {@code Outer}. Such a chain is a static call on {@code Outer$Inner}.
 */
class QualifiedReceiverLoweringTest
{

    private static final String[] HELPER_LINES = {
            "public class OuterHost {",
            "    public static class Maker {",
            "        public static String create(String tag) { return \"<\" + tag + \">\"; }",
            "    }",
            "}",
    };

    private static final String[] LINES = {
            "public class QualifiedUse {",
            "    private static final String[] axisNames = {\"X\", \"Y\", \"Z\"};",
            "    static int indexOf(String name) {",
            "        for (int i = 0; i < axisNames.length; i++) {",
            "            if (axisNames[i].equals(name)) {",
            "                return i;",
            "            }",
            "        }",
            "        return -1;",
            "    }",
            "    public static String check() {",
            "        String made = OuterHost.Maker.create(\"tag\");",
            "        return made + indexOf(\"Y\") + indexOf(\"Q\") + axisNames.length;",
            "    }",
            "}",
    };

    @Test
    void arrayFieldsAndNestedClassChainsLowerAsWritten() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("qualified-recv");
        Path helper = dir.resolve("OuterHost.java");
        Path src = dir.resolve("QualifiedUse.java");
        Files.writeString(helper, String.join(System.lineSeparator(), HELPER_LINES));
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                helper.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile target = null;
        List<byte[]> siblings = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class"))
        {
            for (Path p : stream)
            {
                byte[] bytes = Files.readAllBytes(p);
                ClassFile cf = pool.loadClass(bytes);
                if (cf.getClassName().equals("QualifiedUse"))
                {
                    target = cf;
                }
                else
                {
                    siblings.add(bytes);
                    names.add(cf.getClassName().replace('/', '.'));
                }
            }
        }
        assumeTrue(target != null, "target loaded");

        Object original = invokeCheck(target.write(), siblings, names);
        assertEquals("<tag>1-13", original, "the fixture itself must search the array and call the nested class");

        String d1 = ClassDecompiler.decompile(target);
        assertTrue(TestUtils.recompileSource(target, pool, d1, "QualifiedUse"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, invokeCheck(target.write(), siblings, names),
                "the round-tripped class must behave the same");
    }

    private static Object invokeCheck(byte[] targetBytes, List<byte[]> siblings, List<String> names)
            throws Exception
            {
        TestClassLoader loader = new TestClassLoader();
        for (int i = 0; i < siblings.size(); i++)
        {
            loader.defineClass(names.get(i), siblings.get(i));
        }
        Class<?> clazz = loader.defineClass("QualifiedUse", targetBytes);
        return clazz.getMethod("check").invoke(null);
    }
}
