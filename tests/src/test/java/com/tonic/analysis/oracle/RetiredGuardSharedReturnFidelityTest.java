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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A try with catch and finally whose finally guards its close with a private catch, followed by a
 * shared trailing return fed by the normal path, the user catch's fall-through, and an early-return
 * arm inside the try. After the finally de-duplication retires the copies' guard-catch handlers,
 * those handler blocks still flow into the shared return but no longer appear in the live handler
 * list - the sequence cut used to see them as foreign predecessors and decline the whole region to
 * the legacy walk. Retired handler scaffolding is handler code: its text lives in the recovered
 * clauses, which fall through to the join exactly like a live catch.
 *
 *Only the decompiled shape is asserted here. Executing the round-tripped method is blocked on a
 * pre-existing clause-nesting defect for this fixture's SPLIT-RANGE layout (modern javac fragments
 * the catch and finally ranges around the unprotected inlined copies): the user catch is emitted
 * OUTSIDE the finally construct, doubling the finally's effect on the exception path, and the
 * recompiled exception table over-extends the catch range into code where its locals are dead
 * (VerifyError). The full execution reproducer is preserved in the catch+finally double-effect
 * notes; once the clause nesting is fixed, the three execution-path checks belong here.
 */
class RetiredGuardSharedReturnFidelityTest
{

    private static final String SOURCE =
            "import java.io.IOException;\n"
            + "import java.io.Reader;\n"
            + "import java.io.StringReader;\n"
            + "public class RetiredGuard {\n"
            + "    public static int closes = 0;\n"
            + "    public static int notes = 0;\n"
            + "    static Reader open() { return new StringReader(\"payload\"); }\n"
            + "    static String work(Reader in, boolean fail) {\n"
            + "        if (fail) { throw new IllegalStateException(); }\n"
            + "        return \"ok\";\n"
            + "    }\n"
            + "    static void note() { notes++; }\n"
            + "    public static String use(boolean skip, boolean fail) {\n"
            + "        String result = null;\n"
            + "        Reader in = null;\n"
            + "        try {\n"
            + "            if (skip) { return null; }\n"
            + "            in = open();\n"
            + "            result = work(in, fail);\n"
            + "        } catch (IllegalStateException e) {\n"
            + "            note();\n"
            + "        } finally {\n"
            + "            if (in != null) {\n"
            + "                try { in.close(); closes++; } catch (IOException e2) { note(); }\n"
            + "            }\n"
            + "        }\n"
            + "        return result;\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("retired-guard");
        Path src = dir.resolve("RetiredGuard.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("RetiredGuard.class")));
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "RetiredGuard must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void decompiledShapeKeepsClauseAndSharedReturn()
    {
        assertTrue(d1.contains("finally"), "the finally clause must survive:\n" + d1);
        assertTrue(d1.contains("catch (IllegalStateException"), "the user catch must survive:\n" + d1);
        assertTrue(d1.contains("return result") || d1.contains("return local"),
                "the shared trailing return must survive:\n" + d1);
    }

}
