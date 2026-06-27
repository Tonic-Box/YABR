package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * javac packs varargs arguments into a freshly allocated array
 * ({@code foo(a, b)} -> {@code foo(new T[]{a, b})}); the array is materialized as a local because it
 * is filled element-by-element and then read by the call. Recovery must collapse that array back
 * into individual arguments, and a method declared {@code ACC_VARARGS} must render its trailing
 * parameter as {@code T...} so the collapsed calls recompile.
 */
class VarargsReconstructionTest {

    @Test
    void reflectionChainCollapsesWithoutOrphanArrays() throws Exception {
        String src =
            "public class Reflect {\n" +
            "  static Object m() throws Exception {\n" +
            "    return String.class.getConstructor(String.class).newInstance(\"hello\");\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("Reflect", src);
        assertFalse(out.contains("new Class["), "varargs Class[] must be collapsed:\n" + out);
        assertFalse(out.contains("new Object["), "varargs Object[] must be collapsed:\n" + out);
        assertEquals(1, count(out, "getConstructor("),
            "getConstructor must be emitted exactly once (no double-emission):\n" + out);
        assertTrue(out.contains("getConstructor(String.class)"), out);
        assertTrue(out.contains(".newInstance(\"hello\")"), out);
    }

    @Test
    void jdkVarargsCollapse() throws Exception {
        String src =
            "import java.util.*;\n" +
            "public class Jdk {\n" +
            "  static String fmt(String n, int k) { return String.format(\"%s=%d\", n, k); }\n" +
            "  static List<String> lst() { return Arrays.asList(\"a\", \"b\", \"c\"); }\n" +
            "}\n";
        String out = roundTrip("Jdk", src);
        assertFalse(out.contains("new Object["), "format's Object[] must be collapsed:\n" + out);
        assertFalse(out.contains("new String["), "asList's String[] must be collapsed:\n" + out);
        assertTrue(out.contains("Arrays.asList(\"a\", \"b\", \"c\")"), out);
        assertRecompiles("Jdk", out);
    }

    @Test
    void userDefinedVarargsDeclarationAndCallRecompile() throws Exception {
        String src =
            "public class User {\n" +
            "  static int sum(int... xs) { int s = 0; for (int x : xs) s += x; return s; }\n" +
            "  static int viaConstants() { return sum(1, 2, 3); }\n" +
            "  static int viaArray(int[] a) { return sum(a); }\n" +
            "  static int notVarargs(int[] a) { return a.length; }\n" +
            "}\n";
        String out = roundTrip("User", src);
        assertTrue(out.contains("int... "), "a varargs method must declare its parameter as int...:\n" + out);
        assertFalse(out.contains("new int["), "the constant varargs call must not rebuild an int[]:\n" + out);
        assertTrue(out.contains("sum(1, 2, 3)"), "constants must collapse into the call:\n" + out);
        assertTrue(out.contains("notVarargs(int[] "),
            "a non-varargs array parameter must stay int[], not int...:\n" + out);
        assertRecompiles("User", out);
    }

    @Test
    void emptyVarargsCollapses() throws Exception {
        String src =
            "public class Empty {\n" +
            "  static String m() { return String.format(\"no-args\"); }\n" +
            "}\n";
        String out = roundTrip("Empty", src);
        assertFalse(out.contains("new Object["), "empty varargs must not leave a new Object[0]:\n" + out);
        assertTrue(out.contains("String.format(\"no-args\")"), out);
        assertRecompiles("Empty", out);
    }

    @Test
    void sideEffectingElementsCollapseAndRecompile() throws Exception {
        String src =
            "public class Fx {\n" +
            "  static int c;\n" +
            "  static String s() { c++; return \"x\"; }\n" +
            "  static String m() { return String.format(\"%s%s\", s(), s()); }\n" +
            "}\n";
        String out = roundTrip("Fx", src);
        assertFalse(out.contains("new Object["), "side-effecting varargs elements must still collapse:\n" + out);
        assertTrue(out.contains("String.format(\"%s%s\", "), out);
        assertEquals(2, count(out, "Fx.s()"), "both side-effecting elements must be preserved once each:\n" + out);
        assertRecompiles("Fx", out);
    }

    @Test
    void arrayOfArraysVarargsRecompiles() throws Exception {
        String src =
            "public class Nested {\n" +
            "  static int f(int[]... xs) { int n = 0; for (int[] x : xs) n += x.length; return n; }\n" +
            "  static int call() { return f(new int[]{1, 2}, new int[]{3}); }\n" +
            "}\n";
        String out = roundTrip("Nested", src);
        assertTrue(out.contains("int[]... "), "an int[]... parameter must render with the ellipsis:\n" + out);
        assertRecompiles("Nested", out);
    }

    @Test
    void nonVarargsArrayArgumentIsNotCollapsed() throws Exception {
        String src =
            "public class Plain {\n" +
            "  static int g(int[] a) { return a.length; }\n" +
            "  static int call() { return g(new int[]{1, 2, 3}); }\n" +
            "}\n";
        String out = roundTrip("Plain", src);
        assertTrue(out.contains("new int["), "a non-varargs array argument must be preserved as an array:\n" + out);
        assertTrue(out.contains("g(int[] "), "a non-varargs array parameter must stay int[]:\n" + out);
        assertRecompiles("Plain", out);
    }

    private int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private String roundTrip(String className, String src) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeCompiler(compiler);
        Path dir = Files.createTempDirectory("yabr-varargs");
        try {
            Path srcFile = dir.resolve(className + ".java");
            Files.writeString(srcFile, src);
            int rc = compiler.run(null, null, null, "-g", "-d", dir.toString(), srcFile.toString());
            assertEquals(0, rc, "javac of source fixture failed");
            byte[] bytes = Files.readAllBytes(dir.resolve(className + ".class"));
            return new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        } finally {
            deleteTree(dir);
        }
    }

    private void assertRecompiles(String className, String decompiled) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeCompiler(compiler);
        Path dir = Files.createTempDirectory("yabr-varargs-recompile");
        try {
            Path srcFile = dir.resolve(className + ".java");
            Files.writeString(srcFile, decompiled);
            int rc = compiler.run(null, null, null, "-d", dir.toString(), srcFile.toString());
            assertEquals(0, rc, "decompiled output did not recompile:\n" + decompiled);
        } finally {
            deleteTree(dir);
        }
    }

    private void assumeCompiler(JavaCompiler compiler) {
        if (compiler == null) {
            throw new org.opentest4j.TestAbortedException("no system Java compiler available");
        }
    }

    private void deleteTree(Path dir) throws Exception {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }
}
