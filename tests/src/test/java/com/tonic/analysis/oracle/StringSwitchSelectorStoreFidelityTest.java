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
 * A string switch whose selector variable is assigned in the same block as the hashCode dispatch. The
 * recovery wiped the whole header as scaffolding, dropping the selector's own store - in a loop the
 * per-iteration {@code a = args[i]} vanished (the switch read a stale null forever), and in straight
 * line the store's side-effecting right-hand side disappeared entirely, leaving the selector undefined.
 * The header's leading statements up to the hashCode dispatch are user code and are now emitted before
 * the switch.
 */
class StringSwitchSelectorStoreFidelityTest {

    private static final String SOURCE =
            "public class StrSel {\n"
            + "    static int calls = 0;\n"
            + "    static String next() {\n"
            + "        calls++;\n"
            + "        return \"-y\";\n"
            + "    }\n"
            + "    public static int direct() {\n"
            + "        int hits = 0;\n"
            + "        String a = next();\n"
            + "        switch (a) {\n"
            + "            case \"-x\": hits = 1; break;\n"
            + "            case \"-y\": hits = 2; break;\n"
            + "            default: hits = 3; break;\n"
            + "        }\n"
            + "        return hits;\n"
            + "    }\n"
            + "    public static int scan(String[] args) {\n"
            + "        int hits = 0;\n"
            + "        for (int i = 0; i < args.length; i++) {\n"
            + "            String a = args[i];\n"
            + "            switch (a) {\n"
            + "                case \"-x\": hits++; continue;\n"
            + "                case \"-y\": hits += 2; continue;\n"
            + "                default: hits = 100; break;\n"
            + "            }\n"
            + "        }\n"
            + "        return hits;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("str-sel");
        Path src = dir.resolve("StrSel.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("StrSel.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "StrSel must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void loopSelectorAssignmentSurvives() {
        assertTrue(d1.contains("args[") ,
                "the per-iteration selector assignment must be recovered:\n" + d1);
    }

    @Test
    void bothShapesComputeTheRightValues() throws Exception {
        assertEquals(2, recompiledClass.getMethod("direct").invoke(null),
                "the straight-line selector's initializer must run and match its case");
        java.lang.reflect.Field calls = recompiledClass.getDeclaredField("calls");
        calls.setAccessible(true);
        assertEquals(1, calls.getInt(null),
                "the selector's side-effecting initializer must run exactly once");
        assertEquals(3, recompiledClass.getMethod("scan", String[].class)
                .invoke(null, (Object) new String[]{"-x", "-y"}),
                "the loop must re-read the selector each iteration");
    }
}
