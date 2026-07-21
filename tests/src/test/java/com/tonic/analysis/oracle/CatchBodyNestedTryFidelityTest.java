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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fidelity of a {@code catch} clause whose body contains its own {@code try/finally}. Recovery used to
 * hoist the inner try/finally out of the catch to method level, which left the caught exception variable
 * used out of its scope - invalid, non-recompilable Java - e.g. {@code catch (Exception e) { lock.lock();
 * } try { x = e.toString(); } finally { lock.unlock(); }}. The nested try is now recovered inside the
 * catch. Asserts the exception variable stays in scope (the class recompiles and runs), the finally body
 * appears exactly once (no inlined copy or hoisted duplicate leaked out), and both paths execute.
 */
class CatchBodyNestedTryFidelityTest {

    private static final String SOURCE =
            "import java.util.concurrent.locks.ReentrantLock;\n"
            + "public class CatchNest {\n"
            + "    private final ReentrantLock lock = new ReentrantLock();\n"
            + "    String log;\n"
            + "    public int run(String a) {\n"
            + "        try {\n"
            + "            return Integer.parseInt(a);\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            lock.lock();\n"
            + "            try {\n"
            + "                log = e.getClass().getSimpleName();\n"
            + "                return -1;\n"
            + "            } finally {\n"
            + "                lock.unlock();\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("catch-nested");
        Path src = dir.resolve("CatchNest.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("CatchNest.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile r1 = Recompile.recompiledClone(cf, pool);
        assertNotNull(r1, "CatchNest must be recompilable (the exception variable must stay in scope)");
        recompiledClass = TestUtils.loadAndVerify(r1);
    }

    @Test
    void nestedTryStaysInsideTheCatch() {
        int catchIdx = d1.indexOf("catch (NumberFormatException");
        assertTrue(catchIdx >= 0, "the catch must be recovered:\n" + d1);
        int catchClose = d1.indexOf("\n\t\t}", catchIdx);
        String catchBody = d1.substring(catchIdx, catchClose < 0 ? d1.length() : catchClose);
        assertTrue(catchBody.contains("finally"),
                "the finally must be recovered inside the catch, not hoisted out:\n" + d1);
        assertTrue(catchBody.contains("e.getClass()"),
                "the caught exception variable must be used inside the catch, in scope:\n" + d1);
        assertEquals(1, countOccurrences(d1, "unlock"),
                "the unlock (finally body) must appear exactly once:\n" + d1);
    }

    @Test
    void bothPathsExecute() throws Exception {
        Object o = recompiledClass.getDeclaredConstructor().newInstance();
        assertEquals(42, recompiledClass.getDeclaredMethod("run", String.class).invoke(o, "42"),
                "the normal path returns the parsed value");
        assertEquals(-1, recompiledClass.getDeclaredMethod("run", String.class).invoke(o, "x"),
                "the caught path runs the nested try/finally and keeps the exception variable in scope");
        assertEquals("NumberFormatException", getLog(o),
                "the caught exception variable's use ran (log set inside the nested try)");
    }

    private static String getLog(Object o) throws Exception {
        recompiledClass.getDeclaredMethod("run", String.class).invoke(o, "x");
        java.lang.reflect.Field f = recompiledClass.getDeclaredField("log");
        f.setAccessible(true);
        return (String) f.get(o);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
