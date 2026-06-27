package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A chain of {@code if (x == const)} comparisons on one int compiles to an
 * {@code if_icmp} branch chain (javac does not turn plain ifs into a tableswitch).
 * The structural analyzer must reconstruct it as a {@code switch}.
 */
class ComparisonChainSwitchTest {

    @Test
    void ifChainOnIntBecomesSwitch() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return; // no JDK compiler available in this runtime; skip
        }

        Path dir = Files.createTempDirectory("yabr-cc");
        try {
            String src =
                "public class Dispatch {\n" +
                "  static int d(int x) {\n" +
                "    if (x == 10) return 1;\n" +
                "    if (x == 20) return 2;\n" +
                "    if (x == 30) return 3;\n" +
                "    if (x == 40) return 4;\n" +
                "    return -1;\n" +
                "  }\n" +
                "}\n";
            Path srcFile = dir.resolve("Dispatch.java");
            Files.writeString(srcFile, src);

            int rc = compiler.run(null, null, null, "-d", dir.toString(), srcFile.toString());
            assertEquals(0, rc, "javac failed");

            byte[] bytes = Files.readAllBytes(dir.resolve("Dispatch.class"));
            String out = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();

            assertTrue(out.contains("switch ("), "expected a reconstructed switch, got:\n" + out);
            for (String label : new String[]{"case 10:", "case 20:", "case 30:", "case 40:"}) {
                assertTrue(out.contains(label), "missing " + label + " in:\n" + out);
            }
            // The dispatch should no longer be a residual if-chain on x.
            assertFalse(out.contains("if (x == 20)"), "switch not fully reconstructed:\n" + out);
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }
}
