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
 * A {@code synchronized} block guarded by an early return, with the method's only other return after it.
 * Both paths end at the same return block; the guard arm emitted it first, so the region's continuation
 * walk found it already processed and emitted nothing - the synchronized path then ran off the end of the
 * method. Asserts the value computed inside the region still reaches the caller.
 */
class SharedTailAfterRegionFidelityTest
{

    private static final String SOURCE =
            "import java.util.HashMap;\n"
            + "import java.util.Map;\n"
            + "public class SyncTail {\n"
            + "    private final Map<String, String> map = new HashMap<>();\n"
            + "    public String get(String key) {\n"
            + "        String cache = map.get(key);\n"
            + "        if (cache == null) {\n"
            + "            synchronized (map) {\n"
            + "                cache = \"made:\" + key;\n"
            + "                map.put(key, cache);\n"
            + "            }\n"
            + "        }\n"
            + "        return cache;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("sync-tail");
        Path src = dir.resolve("SyncTail.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("SyncTail.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "SyncTail must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void theSynchronizedPathStillReturns() throws Exception
    {
        Object instance = recompiledClass.getDeclaredConstructor().newInstance();
        Object first = recompiledClass.getMethod("get", String.class).invoke(instance, "k");
        assertEquals("made:k", first,
                "the value built inside the region must be returned (the path fell off the method end):\n" + d1);
        Object cached = recompiledClass.getMethod("get", String.class).invoke(instance, "k");
        assertEquals("made:k", cached, "the early-return path must still return the cached value:\n" + d1);
    }
}
