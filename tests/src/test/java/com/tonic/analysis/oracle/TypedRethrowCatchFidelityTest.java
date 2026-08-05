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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A NARROWER typed user catch that rethrows its exception - {@code catch (IllegalArgumentException
 * ex) { throw ex; }} - alongside ordinary typed catches. The rethrow shape matches a finally's
 * synthetic handler, and treating it as one drove the region down the finally scaffolding: boundary
 * terminal absorption was suppressed (the "finally" writes its exception local), the try body's
 * {@code return past-the-range} declined, and the whole method fell to the legacy walk. The finally
 * classification now also requires the handler's declared type to be one a finally can carry
 * (catch-any or {@code Throwable}), so this shape structures natively as a flat multi-catch.
 */
class TypedRethrowCatchFidelityTest
{

    private static final String SOURCE =
            "import java.io.IOException;\n"
            + "import java.lang.reflect.Field;\n"
            + "public class TypedRethrow {\n"
            + "    public static int VERSION = 3;\n"
            + "    public static int get(Class<?> clazz) throws IOException {\n"
            + "        try {\n"
            + "            Field field = clazz.getField(\"VERSION\");\n"
            + "            if (field.getDeclaringClass() == clazz) {\n"
            + "                return field.getInt(null);\n"
            + "            }\n"
            + "            return 0;\n"
            + "        } catch (IllegalAccessException ex) {\n"
            + "            IOException ioEx = new IOException();\n"
            + "            ioEx.initCause(ex);\n"
            + "            throw ioEx;\n"
            + "        } catch (IllegalArgumentException ex) {\n"
            + "            throw ex;\n"
            + "        } catch (NoSuchFieldException ex) {\n"
            + "            return 0;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("typed-rethrow");
        Path src = dir.resolve("TypedRethrow.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("TypedRethrow.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "TypedRethrow must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void decompiledShapeIsAFlatMultiCatchNotAFinally()
    {
        assertFalse(d1.contains("finally"), "a typed rethrowing user catch is not finally scaffolding:\n" + d1);
        assertTrue(d1.contains("catch (IllegalArgumentException"),
                "the rethrowing clause must survive as a catch:\n" + d1);
        assertTrue(d1.contains("throw ex"), "the rethrow must survive inside its clause:\n" + d1);
    }

    @Test
    void fieldFoundReturnsItsValue() throws Exception
    {
        Object r = recompiledClass.getMethod("get", Class.class)
                .invoke(null, recompiledClass);
        assertEquals(3, r, "declared field's value must be returned:\n" + d1);
    }

    @Test
    void missingFieldReturnsZero() throws Exception
    {
        Object r = recompiledClass.getMethod("get", Class.class).invoke(null, String.class);
        assertEquals(0, r, "NoSuchFieldException path must return 0:\n" + d1);
    }

    @Test
    void nullClassPropagatesThroughTheRethrowClause()
    {
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> recompiledClass.getMethod("get", Class.class).invoke(null, (Object) null));
        assertTrue(thrown.getCause() instanceof NullPointerException,
                "a fault inside the try must propagate unchanged:\n" + d1);
    }
}
