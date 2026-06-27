package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bounds check compiled with a shared exit block — {@code if (i < 0) throw; if (i >= n) throw;}
 * where both guards jump to one throw block — must not lose its second comparison during
 * structural recovery. An exit block (throw / void-return) is terminal and can never be a
 * control-flow merge; treating it as one made the inner branch collapse to empty and vanish.
 * <p>
 * The buggy output ({@code if (i >= 0) return; throw;}) is valid Java that recompiles cleanly, so
 * these tests assert structure and runtime behavior rather than mere recompilation.
 */
class SharedExitBranchTest {

    @Test
    void sharedThrowBoundsCheckKeepsBothComparisons() throws Exception {
        String src =
            "public class Bounds {\n" +
            "  static int[] a = new int[100];\n" +
            "  static void check(int i) {\n" +
            "    if (i < 0) throw new RuntimeException();\n" +
            "    if (i < a.length) return;\n" +
            "    throw new RuntimeException();\n" +
            "  }\n" +
            "}\n";
        String out = decompile("Bounds", src);
        // The upper-bound comparison must survive (the bug dropped it entirely). The two guards
        // may be recovered either nested or OR-combined into one throw — both are faithful — so
        // assert on the surviving comparison, not the throw count.
        assertTrue(out.contains(".length"),
            "the upper-bound comparison (a.length) must survive:\n" + out);
        assertRecompiles("Bounds", out);
    }

    @Test
    void sharedThrowBoundsCheckBehavesIdentically() throws Exception {
        String src =
            "public class Bounds2 {\n" +
            "  static int[] a = new int[100];\n" +
            "  static int check(int i) {\n" +
            "    if (i < 0) throw new RuntimeException();\n" +
            "    if (i < a.length) return i * 2;\n" +
            "    throw new RuntimeException();\n" +
            "  }\n" +
            "}\n";
        assertBehaviorPreserved("Bounds2", src, "check", new int[]{-1, 0, 1, 50, 99, 100, 150});
    }

    @Test
    void valueMergeReturnStaysSingleReturn() throws Exception {
        String src =
            "public class Merge {\n" +
            "  static int f(boolean c) {\n" +
            "    int x;\n" +
            "    if (c) { x = 1; } else { x = 2; }\n" +
            "    return x;\n" +
            "  }\n" +
            "}\n";
        String out = decompile("Merge", src);
        assertEquals(1, countOccurrences(out, "return "),
            "a value-merge return must be emitted exactly once (not duplicated per branch):\n" + out);
        assertRecompiles("Merge", out);
        assertBehaviorPreserved("Merge", src, "f", new boolean[]{true, false});
    }

    // --- helpers ---

    private String decompile(String className, String src) throws Exception {
        JavaCompiler compiler = requireCompiler();
        Path dir = Files.createTempDirectory("yabr-shared");
        try {
            Path srcFile = dir.resolve(className + ".java");
            Files.writeString(srcFile, src);
            assertEquals(0, compiler.run(null, null, null, "-g", "-d", dir.toString(), srcFile.toString()),
                "javac of source fixture failed");
            byte[] bytes = Files.readAllBytes(dir.resolve(className + ".class"));
            return new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        } finally {
            deleteTree(dir);
        }
    }

    private void assertRecompiles(String className, String decompiled) throws Exception {
        JavaCompiler compiler = requireCompiler();
        Path dir = Files.createTempDirectory("yabr-shared-rc");
        try {
            Files.writeString(dir.resolve(className + ".java"), decompiled);
            assertEquals(0, compiler.run(null, null, null, "-d", dir.toString(),
                    dir.resolve(className + ".java").toString()),
                "decompiled output did not recompile:\n" + decompiled);
        } finally {
            deleteTree(dir);
        }
    }

    /**
     * Compiles the original source and the decompiled-then-recompiled output, then invokes the
     * named static method over the given inputs and asserts identical results (return value or
     * thrown exception type) for every input.
     */
    private void assertBehaviorPreserved(String className, String src, String method, Object inputs) throws Exception {
        JavaCompiler compiler = requireCompiler();
        Path origDir = Files.createTempDirectory("yabr-beh-orig");
        Path rcDir = Files.createTempDirectory("yabr-beh-rc");
        try {
            Path srcFile = origDir.resolve(className + ".java");
            Files.writeString(srcFile, src);
            assertEquals(0, compiler.run(null, null, null, "-g", "-d", origDir.toString(), srcFile.toString()),
                "javac of source fixture failed");
            byte[] bytes = Files.readAllBytes(origDir.resolve(className + ".class"));
            String decompiled = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();

            Path rcSrc = rcDir.resolve(className + ".java");
            Files.writeString(rcSrc, decompiled);
            assertEquals(0, compiler.run(null, null, null, "-d", rcDir.toString(), rcSrc.toString()),
                "decompiled output did not recompile:\n" + decompiled);

            int n = java.lang.reflect.Array.getLength(inputs);
            try (URLClassLoader origCl = new URLClassLoader(new URL[]{origDir.toUri().toURL()});
                 URLClassLoader rcCl = new URLClassLoader(new URL[]{rcDir.toUri().toURL()})) {
                Class<?> origClass = origCl.loadClass(className);
                Class<?> rcClass = rcCl.loadClass(className);
                Class<?> paramType = java.lang.reflect.Array.get(inputs, 0) instanceof Boolean ? boolean.class : int.class;
                Method origM = origClass.getDeclaredMethod(method, paramType);
                Method rcM = rcClass.getDeclaredMethod(method, paramType);
                origM.setAccessible(true);
                rcM.setAccessible(true);
                for (int i = 0; i < n; i++) {
                    Object arg = java.lang.reflect.Array.get(inputs, i);
                    String origResult = invokeToString(origM, arg);
                    String rcResult = invokeToString(rcM, arg);
                    assertEquals(origResult, rcResult,
                        "behavior diverged for " + method + "(" + arg + "):\n" + decompiled);
                }
            }
        } finally {
            deleteTree(origDir);
            deleteTree(rcDir);
        }
    }

    private String invokeToString(Method m, Object arg) {
        try {
            return "value:" + m.invoke(null, arg);
        } catch (InvocationTargetException e) {
            return "throw:" + e.getCause().getClass().getName();
        } catch (IllegalAccessException e) {
            return "error:" + e;
        }
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private JavaCompiler requireCompiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new org.opentest4j.TestAbortedException("no system Java compiler available");
        }
        return compiler;
    }

    private void deleteTree(Path dir) throws Exception {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }
}
