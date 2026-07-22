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
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A {@code try { while (true) { ... } } catch (...)} that wraps an infinite loop whose only exit is an
 * exception thrown from the body. The continuation after the try/catch (here the method's implicit return) is
 * reachable ONLY through the catch, so it is absent from the method's normal-flow reverse-post-order. The
 * reaching-condition engine used to decline the whole region on that (a null RPO index would otherwise crash
 * the ordering pass), routing it to the legacy walk; it now appends an index for such exception-reached blocks
 * and structures the region natively. Asserts the shape is preserved, is a round-trip fixed point, and drains
 * the queue then exits via the exception.
 */
class TryWrappingInfiniteLoopFidelityTest {

    private static final String SOURCE =
            "import java.util.Queue;\n"
            + "public class InfLoop {\n"
            + "    int count;\n"
            + "    public int drain(Queue<Integer> q) {\n"
            + "        try {\n"
            + "            while (true) {\n"
            + "                count += q.remove();\n"
            + "            }\n"
            + "        } catch (RuntimeException e) {\n"
            + "            return count;\n"
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
        Path dir = Files.createTempDirectory("inf-loop");
        Path src = dir.resolve("InfLoop.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("InfLoop.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "InfLoop must be recompilable");
        d2 = ClassDecompiler.decompile(recovered);
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void infiniteLoopInTryIsPreservedAndRoundTripsFixed() {
        assertTrue(d1.contains("while (true)"), "the infinite loop must be preserved:\n" + d1);
        assertTrue(d1.contains("try") && d1.contains("catch"), "the try/catch must be preserved:\n" + d1);
        assertEquals(d1, d2, "a try wrapping an infinite loop must be a round-trip fixed point");
    }

    @Test
    void drainsThenExitsViaException() throws Exception {
        Object inst = recompiledClass.getDeclaredConstructor().newInstance();
        Deque<Integer> q = new ArrayDeque<>();
        q.add(1);
        q.add(2);
        q.add(3);
        assertEquals(6, recompiledClass.getMethod("drain", java.util.Queue.class).invoke(inst, q),
                "the loop drains 1+2+3 then remove() throws on empty, caught to return the sum");
    }
}
