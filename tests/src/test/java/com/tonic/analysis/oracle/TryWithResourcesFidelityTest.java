package com.tonic.analysis.oracle;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end fidelity of a try-with-resources statement through the full pipeline. javac desugars
 * {@code try (r) { body }} into a normal-path {@code r.close()} plus a cleanup handler that closes and chains a
 * close failure into the in-flight exception via {@code Throwable.addSuppressed}; the decompiler folds that back
 * into concise try-with-resources ({@code SomeType r = ...; try (r) { ... }}). Each fixture is compiled with javac,
 * decompiled, and its decompiled source recompiled.
 *
 * <p>The fixture is the resource itself: {@code close()} increments a static counter (and optionally throws). The
 * normal case asserts a round-trip fixed point AND a preserved close; the exception cases let the thrown exception
 * propagate (the test catches it) and assert the resource is closed when the body throws, and that a close failure
 * is suppressed into the body's exception when both throw - the paths a recompiler that drops the resource
 * management, or mis-wires the suppress handler, would break.
 */
class TryWithResourcesFidelityTest {

    private static final String NORMAL_SOURCE =
            "public class TwrNormal implements AutoCloseable {\n"
            + "    static int closes;\n"
            + "    public void close() { closes += 1; }\n"
            + "    public static int f() {\n"
            + "        closes = 0;\n"
            + "        TwrNormal r = new TwrNormal();\n"
            + "        try (r) {\n"
            + "            return 42;\n"
            + "        }\n"
            + "    }\n"
            + "    public static int closeCount() { return closes; }\n"
            + "}\n";

    private static final String THROWING_SOURCE =
            "public class TwrThrowing implements AutoCloseable {\n"
            + "    static int closes;\n"
            + "    boolean throwOnClose;\n"
            + "    public TwrThrowing() {}\n"
            + "    public TwrThrowing(boolean t) { this.throwOnClose = t; }\n"
            + "    public void close() {\n"
            + "        closes += 1;\n"
            + "        if (this.throwOnClose) { throw new IllegalStateException(\"close\"); }\n"
            + "    }\n"
            + "    public static int closeCount() { return closes; }\n"
            + "    public static void bodyThrows(boolean t) {\n"
            + "        closes = 0;\n"
            + "        TwrThrowing r = new TwrThrowing();\n"
            + "        try (r) {\n"
            + "            if (t) { throw new RuntimeException(\"body\"); }\n"
            + "        }\n"
            + "    }\n"
            + "    public static void suppress(boolean t) {\n"
            + "        TwrThrowing r = new TwrThrowing(true);\n"
            + "        try (r) {\n"
            + "            if (t) { throw new RuntimeException(\"primary\"); }\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String normalD1;
    private static String normalD2;
    private static String throwingD1;
    private static String throwingD2;
    private static Class<?> normalClass;
    private static Class<?> throwingClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");

        Recovered normal = recompile(compiler, "TwrNormal", NORMAL_SOURCE);
        normalD1 = normal.d1;
        normalD2 = normal.d2;
        normalClass = normal.recompiled;
        Recovered throwing = recompile(compiler, "TwrThrowing", THROWING_SOURCE);
        throwingD1 = throwing.d1;
        throwingD2 = throwing.d2;
        throwingClass = throwing.recompiled;
    }

    @Test
    void isRoundTripFixedPoint() {
        assertTrue(normalD1.contains("try ("), "decompiler should fold to try-with-resources:\n" + normalD1);
        assertEquals(normalD1, normalD2, "try-with-resources must be a round-trip fixed point");
    }

    @Test
    void conditionallyThrowingBodyIsRoundTripFixedPoint() {
        assertTrue(throwingD1.contains("try ("), "decompiler should fold to try-with-resources:\n" + throwingD1);
        assertEquals(throwingD1, throwingD2,
                "a conditionally-throwing try-with-resources body must be a round-trip fixed point");
    }

    @Test
    void closesOnNormalCompletion() throws Exception {
        assertEquals(42, normalClass.getDeclaredMethod("f").invoke(null),
                "recompiled method must return the same value");
        assertEquals(1, normalClass.getDeclaredMethod("closeCount").invoke(null),
                "the resource must be closed on the normal path");
    }

    @Test
    void closesWhenBodyThrows() throws Exception {
        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> throwingClass.getDeclaredMethod("bodyThrows", boolean.class).invoke(null, true));
        assertEquals("body", wrapper.getCause().getMessage(), "the body's exception must propagate unchanged");
        assertEquals(1, throwingClass.getDeclaredMethod("closeCount").invoke(null),
                "the resource must still be closed when the body throws");
    }

    @Test
    void suppressesCloseFailureIntoBodyException() {
        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> throwingClass.getDeclaredMethod("suppress", boolean.class).invoke(null, true));
        Throwable thrown = wrapper.getCause();
        assertEquals("primary", thrown.getMessage(), "the body's exception must be the primary that propagates");
        Throwable[] suppressed = thrown.getSuppressed();
        assertEquals(1, suppressed.length, "the close's failure must be suppressed into the primary");
        assertEquals("close", suppressed[0].getMessage(), "the suppressed exception must be the close's");
    }

    private static Recovered recompile(JavaCompiler compiler, String className, String source) throws Exception {
        Path dir = Files.createTempDirectory("twr-fidelity");
        Path src = dir.resolve(className + ".java");
        Files.writeString(src, source);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve(className + ".class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        String d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, className + " must be recompilable");
        return new Recovered(d1, ClassDecompiler.decompile(recovered), TestUtils.loadAndVerify(recovered));
    }

    private static final class Recovered {
        final String d1;
        final String d2;
        final Class<?> recompiled;

        Recovered(String d1, String d2, Class<?> recompiled) {
            this.d1 = d1;
            this.d2 = d2;
            this.recompiled = recompiled;
        }
    }
}
