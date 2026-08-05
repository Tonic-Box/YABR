package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A merge block that emits no statements must not be wrapped in its reaching condition. The wrapper is a
 * no-op {@code if} whose condition contains a call, so no later pass may drop it - a bare comparison is not
 * a legal Java statement and extracting the call would change short-circuit order - and its presence between
 * a materialized boolean's declaration and its use blocks the fold back to a direct {@code return}.
 */
class EmptyReachingGuardDecompileTest
{

    private static final String SOURCE =
            "import java.util.concurrent.locks.ReentrantLock;\n"
                    + "public class EmptyGuardFixture {\n"
                    + "    private final ReentrantLock lock = new ReentrantLock();\n"
                    + "    private Object session;\n"
                    + "    public boolean hasActiveSession() {\n"
                    + "        lock.lock();\n"
                    + "        try {\n"
                    + "            return session != null && !isExpired();\n"
                    + "        } finally {\n"
                    + "            lock.unlock();\n"
                    + "        }\n"
                    + "    }\n"
                    + "    private boolean isExpired() {\n"
                    + "        return false;\n"
                    + "    }\n"
                    + "}\n";

    @Test
    void anEmptyMergeIsNotWrappedInItsReachingCondition() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("empty-guard");
        Path src = dir.resolve("EmptyGuardFixture.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("EmptyGuardFixture.class")));
        String d1 = ClassDecompiler.decompile(cf);
        String flat = d1.replaceAll("\\s+", " ");

        assertFalse(flat.contains("isExpired()) { }"), "an empty merge must not be emitted as a guarded no-op:\n" + d1);
        assertTrue(flat.contains("return this.session != null && !isExpired();"),
                "the condition must fold straight into the return:\n" + d1);

        // The same source decompiled from its own recompiled form: the guarded no-op made this a
        // non-fixed-point, because only one of the two layouts produced it.
        assertTrue(TestUtils.recompileSource(cf, pool, d1, "EmptyGuardFixture"), "fixture recompiles");
        assertEquals(d1, ClassDecompiler.decompile(cf), "decompiling must be a fixed point");
    }
}
