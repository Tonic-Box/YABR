package com.tonic.analysis.oracle;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A recompiled call to a method or constructor whose callee is not in the class pool (any JDK method) must emit the
 * callee's DECLARED descriptor, not one built from the argument's static type. The pool never loads JDK classes, so
 * a subtype argument - a String to {@code List.add(Object)}, an IOException to {@code RuntimeException(String,
 * Throwable)}, an Integer to {@code Objects.requireNonNull(Object)} - previously emitted a descriptor naming the
 * argument type ({@code add(String)}), a method that does not exist, and the recompiled code threw
 * {@code NoSuchMethodError} at call time. No decompile-level gate catches this (the wrong descriptor re-decompiles
 * to the same source, and verification resolves invokes lazily), so this test EXECUTES the recompiled methods.
 */
class SubtypeArgDescriptorFidelityTest {

    private static final String SOURCE =
            "public class SubtypeArgs {\n"
            + "    public static int listAddObject() {\n"
            + "        java.util.ArrayList l = new java.util.ArrayList();\n"
            + "        l.add(\"x\");\n"
            + "        return l.size();\n"
            + "    }\n"
            + "    public static String requireNonNullSubtype() {\n"
            + "        Integer i = 7;\n"
            + "        return java.util.Objects.requireNonNull(i).toString();\n"
            + "    }\n"
            + "    public static String wrapSubtypeThrowable() {\n"
            + "        java.io.IOException cause = new java.io.IOException(\"io\");\n"
            + "        RuntimeException r = new RuntimeException(\"wrapped\", cause);\n"
            + "        return r.getMessage() + \"/\" + r.getCause().getClass().getSimpleName();\n"
            + "    }\n"
            + "}\n";

    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("subtype-arg");
        Path src = dir.resolve("SubtypeArgs.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("SubtypeArgs.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "SubtypeArgs must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void subtypeArgumentToObjectParameterResolves() throws Exception {
        assertEquals(1, recompiledClass.getDeclaredMethod("listAddObject").invoke(null),
                "List.add(Object) called with a String must resolve, not throw NoSuchMethodError");
    }

    @Test
    void subtypeArgumentToStaticObjectParameterResolves() throws Exception {
        assertEquals("7", recompiledClass.getDeclaredMethod("requireNonNullSubtype").invoke(null),
                "Objects.requireNonNull(Object) called with an Integer must resolve");
    }

    @Test
    void subtypeArgumentToThrowableConstructorResolves() throws Exception {
        assertEquals("wrapped/IOException", recompiledClass.getDeclaredMethod("wrapSubtypeThrowable").invoke(null),
                "RuntimeException(String, Throwable) called with an IOException must resolve");
    }
}
