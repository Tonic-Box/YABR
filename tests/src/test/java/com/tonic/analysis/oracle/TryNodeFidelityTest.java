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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fidelity of a try placed under structure - inside a loop body, or on one arm of a branch - where no
 * linear staging exists. The reaching-condition engine places such a try as an opaque composite node at
 * its structural position (delegating the try itself to the try/catch recovery) and structures the
 * surrounding loop or branch natively. Each fixture asserts its spans survive both decompiles, that the
 * second decompile is a fixed point of the third, and that the recompiled bytecode executes every path.
 */
class TryNodeFidelityTest {

    private static final Map<String, String> FIXTURES = new LinkedHashMap<>();
    static {
        FIXTURES.put("TryInLoop",
                "public class TryInLoop {\n"
                + "    public static String check() {\n"
                + "        String[] xs = {\"a\", \"7\", \"b\", \"3\"};\n"
                + "        int sum = 0;\n"
                + "        int bad = 0;\n"
                + "        for (int i = 0; i < xs.length; i++) {\n"
                + "            try {\n"
                + "                sum += Integer.parseInt(xs[i]);\n"
                + "            } catch (NumberFormatException e) {\n"
                + "                bad++;\n"
                + "            }\n"
                + "        }\n"
                + "        return sum + \":\" + bad;\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("TryInArm",
                "public class TryInArm {\n"
                + "    public static String check() {\n"
                + "        StringBuilder sb = new StringBuilder();\n"
                + "        for (int mode = 0; mode < 2; mode++) {\n"
                + "            String r;\n"
                + "            if (mode == 0) {\n"
                + "                try {\n"
                + "                    r = \"p\" + Integer.parseInt(\"12\");\n"
                + "                } catch (NumberFormatException e) {\n"
                + "                    r = \"e1\";\n"
                + "                }\n"
                + "            } else {\n"
                + "                try {\n"
                + "                    r = \"q\" + Integer.parseInt(\"xx\");\n"
                + "                } catch (NumberFormatException e) {\n"
                + "                    r = \"e2\";\n"
                + "                }\n"
                + "            }\n"
                + "            sb.append(r).append('|');\n"
                + "        }\n"
                + "        return sb.toString();\n"
                + "    }\n"
                + "}\n");
        FIXTURES.put("TryReturnInLoop",
                "public class TryReturnInLoop {\n"
                + "    public static String check() {\n"
                + "        String[] xs = {\"x\", \"y\", \"5\", \"9\"};\n"
                + "        for (int i = 0; i < xs.length; i++) {\n"
                + "            try {\n"
                + "                return \"first=\" + Integer.parseInt(xs[i]);\n"
                + "            } catch (NumberFormatException e) {\n"
                + "            }\n"
                + "        }\n"
                + "        return \"none\";\n"
                + "    }\n"
                + "}\n");
    }

    private static final Map<String, String[]> DECOMPILES = new LinkedHashMap<>();
    private static final Map<String, Class<?>> RECOMPILED = new LinkedHashMap<>();

    @BeforeAll
    static void compileAndRoundTrip() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("try-node-fidelity");
        for (Map.Entry<String, String> e : FIXTURES.entrySet()) {
            Files.writeString(dir.resolve(e.getKey() + ".java"), e.getValue());
            assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(),
                    dir.resolve(e.getKey() + ".java").toString()) == 0, e.getKey() + " compiled");
            byte[] bytes = Files.readAllBytes(dir.resolve(e.getKey() + ".class"));
            ClassPool pool = TestUtils.emptyPool();
            ClassFile cf = pool.loadClass(bytes);
            String d1 = ClassDecompiler.decompile(cf);
            ClassFile r1 = Recompile.recompiledClone(cf, pool);
            assertNotNull(r1, e.getKey() + " must be recompilable");
            String d2 = ClassDecompiler.decompile(r1);
            RECOMPILED.put(e.getKey(), TestUtils.loadAndVerify(r1));
            ClassFile r2 = Recompile.recompiledClone(r1, TestUtils.emptyPool());
            assertNotNull(r2, e.getKey() + " must recompile again");
            String d3 = ClassDecompiler.decompile(r2);
            DECOMPILES.put(e.getKey(), new String[] {d1, d2, d3});
        }
    }

    @Test
    void tryInLoopKeepsBothPathsAndIsStable() throws Exception {
        String[] d = DECOMPILES.get("TryInLoop");
        for (String s : new String[] {d[0], d[1]}) {
            assertTrue(s.contains("catch (NumberFormatException"), "the catch must survive:\n" + s);
            assertTrue(s.contains("sum"), "the try body sum must survive:\n" + s);
            assertTrue(s.contains("bad"), "the catch counter must survive:\n" + s);
        }
        assertEquals(d[1], d[2], "the recovered form must be a fixed point");
        assertEquals("10:2", RECOMPILED.get("TryInLoop").getDeclaredMethod("check").invoke(null));
    }

    @Test
    void tryPerArmKeepsBothTriesAndIsStable() throws Exception {
        String[] d = DECOMPILES.get("TryInArm");
        for (String s : new String[] {d[0], d[1]}) {
            assertTrue(s.contains("\"e1\""), "the then-arm catch must survive:\n" + s);
            assertTrue(s.contains("\"e2\""), "the else-arm catch must survive:\n" + s);
        }
        assertEquals(d[1], d[2], "the recovered form must be a fixed point");
        assertEquals("p12|e2|", RECOMPILED.get("TryInArm").getDeclaredMethod("check").invoke(null));
    }

    @Test
    void tryReturnInLoopKeepsBothExitsAndIsStable() throws Exception {
        String[] d = DECOMPILES.get("TryReturnInLoop");
        for (String s : new String[] {d[0], d[1]}) {
            assertTrue(s.contains("\"first=\""), "the try's return must survive:\n" + s);
            assertTrue(s.contains("\"none\""), "the fall-out return must survive:\n" + s);
        }
        assertEquals(d[1], d[2], "the recovered form must be a fixed point");
        assertEquals("first=5", RECOMPILED.get("TryReturnInLoop").getDeclaredMethod("check").invoke(null));
    }
}
