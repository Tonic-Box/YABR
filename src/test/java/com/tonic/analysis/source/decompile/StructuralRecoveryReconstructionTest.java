package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural recovery of try-based constructs: {@code synchronized} (a monitor enter/exit pair guarded by a
 * catch-all rethrow), multi-catch (one handler reached by several exception-table entries), and a
 * {@code try}/{@code finally} whose protected region returns a value (the finally is inlined on the normal
 * exit path). Each previously decompiled to wrong source (dropped/empty bodies or duplicated clauses).
 */
class StructuralRecoveryReconstructionTest {

    @Test
    void synchronizedBlockReconstructed() throws Exception {
        String src =
            "public class Sync {\n" +
            "  private final Object lock = new Object();\n" +
            "  private int n;\n" +
            "  public int inc() { synchronized (lock) { n++; return n; } }\n" +
            "}\n";
        String out = roundTrip("Sync", src);
        assertTrue(out.contains("synchronized (this.lock)"), "expected a synchronized block:\n" + out);
        assertFalse(out.contains("finally"), "must not leave a residual try/finally:\n" + out);
        assertRecompiles("Sync", out);
    }

    @Test
    void synchronizedOnParameterReconstructed() throws Exception {
        String src =
            "public class SyncP {\n" +
            "  public int f(Object lock, int x) { synchronized (lock) { return x * 2; } }\n" +
            "}\n";
        String out = roundTrip("SyncP", src);
        assertTrue(out.contains("synchronized (arg0)"), "expected synchronized on the parameter:\n" + out);
        assertRecompiles("SyncP", out);
    }

    @Test
    void multiCatchCoalescedIntoOneClause() throws Exception {
        String src =
            "public class Multi {\n" +
            "  public static int f(String s) {\n" +
            "    try { return Integer.parseInt(s); }\n" +
            "    catch (NumberFormatException | NullPointerException e) { return -1; }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("Multi", src);
        assertTrue(out.contains("catch (NumberFormatException | NullPointerException"),
            "expected a single multi-catch clause:\n" + out);
        assertEquals(1, count(out, "catch ("), "multi-catch must not split into several clauses:\n" + out);
        assertEquals(1, count(out, "try {"), "must not wrap the region in a redundant outer try:\n" + out);
        assertRecompiles("Multi", out);
    }

    @Test
    void stringSwitchReconstructedFromHashCodePattern() throws Exception {
        String src =
            "public class StrSw {\n" +
            "  public static int f(String s) {\n" +
            "    switch (s) { case \"a\": return 1; case \"b\": return 2; default: return 0; }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("StrSw", src);
        assertTrue(out.contains("switch (arg0)"), "expected a switch on the string, not its hashCode:\n" + out);
        assertFalse(out.contains("hashCode()"), "the hashCode dispatch must be folded away:\n" + out);
        assertTrue(out.contains("case \"a\"") && out.contains("case \"b\""),
            "string case labels must be reconstructed:\n" + out);
        assertRecompiles("StrSw", out);
    }

    @Test
    void stringSwitchWithBreakAndFallThroughReconstructed() throws Exception {
        // The assign-per-case form is recovered as a switch on the string (the downstream switch-expression
        // pass may render it with arrow arms, which is Java 14+, so structure is asserted rather than
        // recompiled on the Java 11 test toolchain).
        String src =
            "public class StrSw2 {\n" +
            "  public static int f(String s) {\n" +
            "    int r = 0;\n" +
            "    switch (s) { case \"a\": case \"b\": r = 1; break; case \"c\": r = 2; break; default: r = -1; }\n" +
            "    return r * 10;\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("StrSw2", src);
        assertTrue(out.contains("switch (arg0)"), "expected a switch on the string:\n" + out);
        assertTrue(out.contains("\"a\"") && out.contains("\"b\"") && out.contains("\"c\""),
            "all string case labels must be reconstructed:\n" + out);
        assertFalse(out.contains("hashCode()"), "the hashCode dispatch must be folded away:\n" + out);
    }

    @Test
    void stringSwitchWithHashCollisionRecompiles() throws Exception {
        // "Aa" and "BB" share hashCode 2112, so one hashCode case chains two equals() guards.
        String src =
            "public class StrSw3 {\n" +
            "  public static int f(String s) {\n" +
            "    switch (s) { case \"Aa\": return 1; case \"BB\": return 2; default: return 0; }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("StrSw3", src);
        assertTrue(out.contains("case \"Aa\"") && out.contains("case \"BB\""),
            "both colliding string labels must be reconstructed:\n" + out);
        assertRecompiles("StrSw3", out);
    }

    @Test
    void tryFinallyWithValueReturnKeepsBothBodies() throws Exception {
        String src =
            "public class FinVal {\n" +
            "  static int compute() { return 7; }\n" +
            "  public static int f() {\n" +
            "    try { return compute(); } finally { System.out.println(\"done\"); }\n" +
            "  }\n" +
            "}\n";
        String out = roundTrip("FinVal", src);
        assertTrue(out.contains("return FinVal.compute()") || out.contains("return compute()"),
            "the try's value return must be preserved, not dropped:\n" + out);
        assertTrue(out.contains("System.out.println(\"done\")"), "the finally body must be preserved:\n" + out);
        assertRecompiles("FinVal", out);
    }

    @Test
    void tryFinallyWithReturnInFinallyPreservesBehaviour() throws Exception {
        // A finally that itself returns overrides the try's value on the normal path but not on the exception
        // path; the recovered source must round-trip to the same observable behaviour.
        String src =
            "public class FinRet {\n" +
            "  public static int f(int x) { try { return 100 / x; } finally { if (x < 0) return -1; } }\n" +
            "}\n";
        String out = roundTrip("FinRet", src);
        assertTrue(out.contains("100 / arg0"), "the try's computation must be preserved:\n" + out);
        Method f = compileAndLoad("FinRet", out).getMethod("f", int.class);
        assertEquals(20, f.invoke(null, 5), "normal path returns the try value");
        assertEquals(-1, f.invoke(null, -2), "the finally's return overrides on the normal path");
        InvocationTargetException thrown =
            assertThrows(InvocationTargetException.class, () -> f.invoke(null, 0));
        assertTrue(thrown.getCause() instanceof ArithmeticException,
            "the exception must still propagate (finally does not catch it)");
    }

    private Class<?> compileAndLoad(String className, String source) throws Exception {
        JavaCompiler compiler = requireCompiler();
        Path dir = Files.createTempDirectory("yabr-struct-run");
        Path srcFile = dir.resolve(className + ".java");
        Files.writeString(srcFile, source);
        assertEquals(0, compiler.run(null, null, null, "-d", dir.toString(), srcFile.toString()),
            "decompiled output did not recompile:\n" + source);
        URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()}, getClass().getClassLoader());
        return Class.forName(className, true, loader);
    }

    private int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private String roundTrip(String className, String src) throws Exception {
        JavaCompiler compiler = requireCompiler();
        Path dir = Files.createTempDirectory("yabr-struct");
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
        Path dir = Files.createTempDirectory("yabr-struct-recompile");
        try {
            Path srcFile = dir.resolve(className + ".java");
            Files.writeString(srcFile, decompiled);
            assertEquals(0, compiler.run(null, null, null, "-d", dir.toString(), srcFile.toString()),
                "decompiled output did not recompile:\n" + decompiled);
        } finally {
            deleteTree(dir);
        }
    }

    private JavaCompiler requireCompiler() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new TestAbortedException("no system Java compiler available");
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
