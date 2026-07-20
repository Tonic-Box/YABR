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
 * A method with a generic local ({@code java.util.List<Object> l = ...}) whose callee (a JDK interface) is not in
 * the class pool. Recompiling it exercised two pool-miss bugs at once: (1) the regenerated LocalVariableTable
 * dropped the stale LocalVariableTypeTable's offsets out of alignment, so the class loader rejected the class with
 * {@code ClassFormatError: LVTT entry ... does not match any LVT entry}; and (2) not knowing {@code List} is an
 * interface, the call to {@code l.add}/{@code l.size} emitted {@code invokevirtual} instead of
 * {@code invokeinterface}, throwing {@code IncompatibleClassChangeError} at call time. This decompiles, recompiles,
 * LOADS and EXECUTES the method - the gates otherwise never run recompiled code, so neither error is caught.
 */
class GenericLocalRecompileFidelityTest {

    private static final String SOURCE =
            "public class GenericLocal {\n"
            + "    public static int m() {\n"
            + "        java.util.List<Object> l = new java.util.ArrayList<>();\n"
            + "        l.add(\"x\");\n"
            + "        l.add(\"y\");\n"
            + "        return l.size();\n"
            + "    }\n"
            + "}\n";

    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("generic-local");
        Path src = dir.resolve("GenericLocal.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("GenericLocal.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "GenericLocal must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void loadsAndExecutes() throws Exception {
        assertEquals(2, recompiledClass.getDeclaredMethod("m").invoke(null),
                "the generic-local method must load (aligned LVTT) and run (invokeinterface on the List calls)");
    }
}
