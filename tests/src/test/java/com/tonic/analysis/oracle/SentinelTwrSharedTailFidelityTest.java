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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A hand-written sentinel-style try-with-resources desugar (the pre-fused javac scheme: a typed
 * Throwable catch storing the primary exception plus a separate finally with the sentinel-guarded
 * close) whose construct falls through to a continuation SHARED with a path outside it. The
 * finally node's all-exits-terminal escape used to classify that call-carrying continuation as a
 * delegate-owned per-exit tail: the node was accepted, the delegate recovery owned "everything",
 * and the construct's conditional logic was silently gutted (the comparison dropped, the shared
 * continuation duplicated into one arm and lost from the other). The escape now requires each exit
 * tail to be exclusively the construct's, strictly bare, or an inlined copy of the finally
 * template - a shared chain carrying foreign calls is a genuine join and declines the node.
 */
class SentinelTwrSharedTailFidelityTest
{

    private static final String SOURCE =
            "import java.io.IOException;\n"
            + "import java.io.InputStream;\n"
            + "import java.io.ByteArrayInputStream;\n"
            + "public class SentinelTwr {\n"
            + "    static InputStream open() { return new ByteArrayInputStream(new byte[]{1}); }\n"
            + "    static long crc(InputStream s) throws IOException { return s.read(); }\n"
            + "    static int lock() { return 7; }\n"
            + "    static void extractStep() { }\n"
            + "    public static int use(boolean present, boolean same) throws IOException {\n"
            + "        if (present) {\n"
            + "            InputStream source = open();\n"
            + "            Throwable t1 = null;\n"
            + "            try {\n"
            + "                InputStream target = open();\n"
            + "                Throwable t2 = null;\n"
            + "                try {\n"
            + "                    if (crc(source) == (same ? crc(target) : -1L)) {\n"
            + "                        int r = lock();\n"
            + "                        return r;\n"
            + "                    }\n"
            + "                } catch (Throwable t) {\n"
            + "                    t2 = t;\n"
            + "                    throw t;\n"
            + "                } finally {\n"
            + "                    if (target != null) {\n"
            + "                        if (t2 == null) {\n"
            + "                            target.close();\n"
            + "                        } else {\n"
            + "                            try { target.close(); } catch (Throwable ts) { t2.addSuppressed(ts); }\n"
            + "                        }\n"
            + "                    }\n"
            + "                }\n"
            + "            } catch (Throwable t) {\n"
            + "                t1 = t;\n"
            + "                throw t;\n"
            + "            } finally {\n"
            + "                if (source != null) {\n"
            + "                    if (t1 == null) {\n"
            + "                        source.close();\n"
            + "                    } else {\n"
            + "                        try { source.close(); } catch (Throwable ts) { t1.addSuppressed(ts); }\n"
            + "                    }\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "        extractStep();\n"
            + "        return lock();\n"
            + "    }\n"
            + "}\n";

    private static String d1;

    @BeforeAll
    static void compileAndDecompile() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("sentinel-twr");
        Path src = dir.resolve("SentinelTwr.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.loadClass(Files.readAllBytes(dir.resolve("SentinelTwr.class")));
        d1 = ClassDecompiler.decompile(cf);
    }

    @Test
    void comparisonSurvivesDecompilation()
    {
        assertTrue(d1.contains("crc(source)"), "the crc comparison must not be gutted from the construct:\n" + d1);
    }

    @Test
    void sharedContinuationSurvivesOutsideTheConstruct()
    {
        String afterConstruct = d1.substring(d1.lastIndexOf("finally"));
        assertTrue(d1.contains("extractStep"), "the shared continuation must survive:\n" + d1);
        assertTrue(afterConstruct.contains("extractStep") || countOccurrences(d1, "extractStep(") >= 2,
                "the shared continuation must be reachable from the fall-through path, not absorbed "
                + "exclusively into one arm of the construct:\n" + d1);
    }

    private static int countOccurrences(String s, String needle)
    {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1))
        {
            n++;
        }
        return n;
    }
}
