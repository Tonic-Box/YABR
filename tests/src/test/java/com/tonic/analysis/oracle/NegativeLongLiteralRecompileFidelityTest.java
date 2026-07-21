package com.tonic.analysis.oracle;

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
 * Recompile fidelity of negative {@code long} literals. The parser folded {@code -<literal>} for a
 * long-suffixed literal by narrowing it to {@code int} whenever the value fit in int range, dropping the
 * {@code long} type - so {@code -1L} became int {@code -1}, lowered as {@code iconst_m1}/{@code istore} into a
 * long slot. That returned a truncated value and corrupted long locals (a {@code VerifyError} when the store
 * fed a wide slot the verifier tracked). An explicit {@code long} literal must stay {@code long} on negation.
 */
class NegativeLongLiteralRecompileFidelityTest {

    private static final String SOURCE =
            "public class NegLong {\n"
            + "    public long minusOne() { return -1L; }\n"
            + "    public long small() { return -5L; }\n"
            + "    public long positive() { return 7L; }\n"
            + "    public long belowIntMin() { return -3000000000L; }\n"
            + "    public long assigned(long s) { long x = s; x = -1L; return x; }\n"
            + "    public long sum() { long a = -1L; long b = -2L; return a + b; }\n"
            + "    public int negInt() { return -1; }\n"
            + "    public long intMin() { return -2147483648L; }\n"
            + "}\n";

    private static Class<?> rc;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("neg-long");
        Path src = dir.resolve("NegLong.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("NegLong.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "NegLong must be recompilable");
        rc = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void negativeLongLiteralsKeepTheirValueAndType() throws Exception {
        Object o = rc.getDeclaredConstructor().newInstance();
        assertEquals(-1L, rc.getMethod("minusOne").invoke(o));
        assertEquals(-5L, rc.getMethod("small").invoke(o));
        assertEquals(7L, rc.getMethod("positive").invoke(o));
        assertEquals(-3000000000L, rc.getMethod("belowIntMin").invoke(o));
        assertEquals(-1L, rc.getMethod("assigned", long.class).invoke(o, 42L));
        assertEquals(-3L, rc.getMethod("sum").invoke(o));
        assertEquals(-1, rc.getMethod("negInt").invoke(o));
        assertEquals((long) Integer.MIN_VALUE, rc.getMethod("intMin").invoke(o));
    }
}
