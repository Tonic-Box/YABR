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
 * A merge whose dominating operand is a loop counter. Declaring the merge bound that operand into the
 * merge's own variable to give it a home - but the counter already had one, so the binding renamed it,
 * severed its store/load web and left the loop stepping the wrong variable. Asserts the counter still
 * walks its whole range and the derived value still tracks it.
 */
class DominatingOperandStealFidelityTest
{

    private static final String SOURCE =
            "public class PlaneWalk {\n"
            + "    public int checkPlane = -1;\n"
            + "    public String visit(int planes) {\n"
            + "        StringBuilder sb = new StringBuilder();\n"
            + "        for (int counter = planes; counter >= 0; counter--) {\n"
            + "            if (counter == checkPlane) {\n"
            + "                continue;\n"
            + "            }\n"
            + "            int id = (counter == planes) ? checkPlane : counter;\n"
            + "            sb.append(id).append(',');\n"
            + "        }\n"
            + "        return sb.toString();\n"
            + "    }\n"
            + "}\n";

    private static String d1;
    private static Class<?> recompiledClass;

    @BeforeAll
    static void compileAndRecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("plane-walk");
        Path src = dir.resolve("PlaneWalk.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        byte[] bytes = Files.readAllBytes(dir.resolve("PlaneWalk.class"));
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(bytes);
        d1 = ClassDecompiler.decompile(cf);
        ClassFile recovered = Recompile.recompiledClone(cf, pool);
        assertNotNull(recovered, "PlaneWalk must be recompilable");
        recompiledClass = TestUtils.loadAndVerify(recovered);
    }

    @Test
    void theCounterKeepsItsOwnVariable() throws Exception
    {
        Object instance = recompiledClass.getDeclaredConstructor().newInstance();
        assertEquals("-1,4,3,2,1,0,", recompiledClass.getMethod("visit", int.class).invoke(instance, 5),
                "the counter must walk its whole range (the merge stole it):\n" + d1);

        recompiledClass.getField("checkPlane").setInt(instance, 2);
        assertEquals("2,4,3,1,0,", recompiledClass.getMethod("visit", int.class).invoke(instance, 5),
                "the skipped plane must still be skipped and the counter still advance:\n" + d1);
    }
}
