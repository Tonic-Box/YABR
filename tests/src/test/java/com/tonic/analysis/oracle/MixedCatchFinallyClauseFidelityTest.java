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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A try with USER catches alongside a control-flow {@code finally} (the resource-close idiom with
 * {@code catch} clauses in between). The rethrowing catch-all's clause used to be recovered twice - once
 * completely, then again by the outer scaffolding over the already-consumed walk state - and the second,
 * truncated recovery won: the output showed an empty {@code finally {}} with the cleanup dangling after
 * the try/catch as normal-path code, silently dropping the exception-path cleanup. Clause recovery is now
 * cached per handler block, the handler's own split entries are consumed before its structured walk (they
 * otherwise re-enter try recovery inside the clause), and the finally template is folded out of the user
 * catch bodies as well as the try body.
 */
class MixedCatchFinallyClauseFidelityTest {

    private static final String SOURCE =
            "import java.io.DataOutputStream;\n"
            + "import java.io.FileNotFoundException;\n"
            + "import java.io.FileOutputStream;\n"
            + "import java.io.IOException;\n"
            + "public class MixedFin {\n"
            + "    public boolean save(String fn) throws Exception {\n"
            + "        FileOutputStream fos = null;\n"
            + "        DataOutputStream dos = null;\n"
            + "        try {\n"
            + "            fos = new FileOutputStream(fn);\n"
            + "            dos = new DataOutputStream(fos);\n"
            + "            dos.write(1);\n"
            + "        } catch (FileNotFoundException e) {\n"
            + "            return false;\n"
            + "        } catch (IOException e) {\n"
            + "            return false;\n"
            + "        } finally {\n"
            + "            if (fos != null) {\n"
            + "                fos.close();\n"
            + "            }\n"
            + "            if (dos != null) {\n"
            + "                dos.close();\n"
            + "            }\n"
            + "        }\n"
            + "        return true;\n"
            + "    }\n"
            + "}\n";

    private static String d1;

    @BeforeAll
    static void compileAndDecompile() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("mixed-fin");
        Path src = dir.resolve("MixedFin.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0,
                "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("MixedFin.class")));
        d1 = ClassDecompiler.decompile(cf);
    }

    @Test
    void finallyClauseKeepsItsCleanup() {
        assertFalse(d1.contains("finally {}"),
                "the finally clause must not collapse to an empty block:\n" + d1);
        int finallyAt = d1.indexOf("finally");
        assertTrue(finallyAt > 0, "the finally clause must be present:\n" + d1);
        assertTrue(d1.indexOf("fos.close", finallyAt) > 0 && d1.indexOf("dos.close", finallyAt) > 0,
                "both guarded closes must live in the finally clause:\n" + d1);
    }

    @Test
    void userCatchesStayFlatClauses() {
        assertTrue(d1.contains("catch (FileNotFoundException"),
                "the user catches must remain flat clauses of the same try:\n" + d1);
        assertTrue(d1.contains("catch (IOException"),
                "the user catches must remain flat clauses of the same try:\n" + d1);
        int tryAt = d1.indexOf("try {");
        assertTrue(tryAt >= 0 && d1.indexOf("try {", tryAt + 1) < 0,
                "no nested try may be synthesized around the user catches:\n" + d1);
    }
}
