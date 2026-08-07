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
 * An enum switch survives the round trip with its dispatch intact. A case label naming an enum constant
 * carried no integer key, so every case silently dropped and the switch lowered to a bare goto - the whole
 * body vanished while the class still verified. Lowering now dispatches on {@code ordinal()} with the
 * constants' ordinals as keys, resolved from the enum's own class, and recovery reads that direct-ordinal
 * form back into constant-name labels; a label that cannot be resolved fails the lowering loudly instead.
 */
class EnumSwitchLoweringTest
{

    private static final String[] ENUM_LINES = {
            "public enum Step { Begin, Render, End }",
    };

    private static final String[] LINES = {
            "public class EnumDispatch {",
            "    static String out = \"\";",
            "    long startTime, renderTime;",
            "    int frameIndex, size = 4;",
            "    void appStep(Step step) {",
            "        long time = 0L;",
            "        switch (step) {",
            "            case Begin:",
            "                this.startTime = 1L;",
            "                out += \"B\";",
            "                break;",
            "            case Render:",
            "                this.renderTime = 2L;",
            "                out += \"R\";",
            "                break;",
            "            case End:",
            "                time = 3L;",
            "                this.frameIndex = this.frameIndex + 1;",
            "                if (this.frameIndex >= this.size) {",
            "                    this.frameIndex = 0;",
            "                }",
            "                out += \"E\" + time;",
            "                break;",
            "            default:",
            "        }",
            "    }",
            "    public static String check() {",
            "        EnumDispatch e = new EnumDispatch();",
            "        out = \"\";",
            "        e.appStep(Step.Begin);",
            "        e.appStep(Step.Render);",
            "        e.appStep(Step.End);",
            "        return out;",
            "    }",
            "}",
    };

    @Test
    void anEnumSwitchKeepsItsDispatch() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("enum-dispatch");
        Path enumSrc = dir.resolve("Step.java");
        Path src = dir.resolve("EnumDispatch.java");
        Files.writeString(enumSrc, String.join(System.lineSeparator(), ENUM_LINES));
        Files.writeString(src, String.join(System.lineSeparator(), LINES));
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                enumSrc.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = new ClassPool();
        ClassFile target = null;
        List<byte[]> siblings = new ArrayList<>();
        List<String> siblingNames = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.class"))
        {
            for (Path p : stream)
            {
                byte[] bytes = Files.readAllBytes(p);
                ClassFile cf = pool.loadClass(bytes);
                if (cf.getClassName().equals("EnumDispatch"))
                {
                    target = cf;
                }
                else
                {
                    siblings.add(bytes);
                    siblingNames.add(cf.getClassName().replace('/', '.'));
                }
            }
        }
        assumeTrue(target != null, "target class loaded");

        Object original = invokeCheck(target.write(), siblings, siblingNames);
        assertEquals("BRE3", original, "the fixture itself must dispatch each case");

        String d1 = ClassDecompiler.decompile(target);
        assertTrue(d1.contains("case Begin:") && d1.contains("case Render:") && d1.contains("case End:"),
                "the cases must be recovered with their constant names:\n" + d1);

        assertTrue(TestUtils.recompileSource(target, pool, d1, "EnumDispatch"),
                "the decompiled source must recompile:\n" + d1);
        assertEquals(original, invokeCheck(target.write(), siblings, siblingNames),
                "the round-tripped class must dispatch the same");

        String d2 = ClassDecompiler.decompile(target);
        assertTrue(d2.contains("case Begin:") && d2.contains("case Render:") && d2.contains("case End:"),
                "the relowered dispatch must read back with its constant names:\n" + d2);
    }

    private static Object invokeCheck(byte[] targetBytes, List<byte[]> siblings, List<String> names)
            throws Exception
            {
        TestClassLoader loader = new TestClassLoader();
        for (int i = 0; i < siblings.size(); i++)
        {
            loader.defineClass(names.get(i), siblings.get(i));
        }
        Class<?> clazz = loader.defineClass("EnumDispatch", targetBytes);
        return clazz.getMethod("check").invoke(null);
    }
}
