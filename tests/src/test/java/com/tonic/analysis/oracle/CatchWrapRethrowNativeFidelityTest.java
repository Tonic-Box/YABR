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
 * Fidelity of a {@code try { body } catch (E e) { throw new X(e); }} - a catch that wraps and rethrows a NEW
 * exception. Because the handler ends in {@code athrow}, it was mistaken for a finally, which forced the try body
 * to the legacy walk instead of the reaching-condition engine. The handler rethrows a fresh exception, not the
 * caught one, so it is a catch clause, not a finally; the body must recover natively and the clause survive.
 * Asserts a round-trip fixed point and that both paths execute (the normal return, and the wrapped rethrow on the
 * caught path).
 */
class CatchWrapRethrowNativeFidelityTest {

    private static final String SOURCE =
            "public class CatchWrap {\n"
            + "    static int events;\n"
            + "    public static int m(int x) {\n"
            + "        events = 0;\n"
            + "        try {\n"
            + "            events = 10 / x;\n"
            + "            return events;\n"
            + "        } catch (ArithmeticException e) {\n"
            + "            throw new IllegalStateException(\"wrapped\");\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static String d2;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("catch-wrap-native");
        Path src = dir.resolve("CatchWrap.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("CatchWrap.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "CatchWrap must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void isRoundTripFixedPointWithACatchNotAFinally() {
        assertTrue(d1.contains("catch (ArithmeticException"), "the wrapping catch must be recovered as a catch:\n" + d1);
        assertTrue(d1.contains("throw new IllegalStateException"), "the rethrow must be preserved:\n" + d1);
        assertEquals(d1, d2, "a catch that wraps and rethrows must be a round-trip fixed point");
    }

    @Test
    void normalAndCaughtPathsExecute() throws Exception {
        assertEquals(5, recompiledClass.getDeclaredMethod("m", int.class).invoke(null, 2),
                "the normal path must return the computed value");
        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> recompiledClass.getDeclaredMethod("m", int.class).invoke(null, 0));
        Throwable thrown = wrapper.getCause();
        assertEquals("wrapped", thrown.getMessage(), "the caught path must throw the wrapping exception");
        assertEquals("IllegalStateException", thrown.getClass().getSimpleName(),
                "the caught path must throw the fresh IllegalStateException, not the caught one");
    }
}
