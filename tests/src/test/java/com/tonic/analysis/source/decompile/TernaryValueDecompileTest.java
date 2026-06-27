package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug C: a ternary with variable/parameter arms reconstructs as ?: (not a phi var defaulted to 0). */
public class TernaryValueDecompileTest {

    @Test
    public void variableArmTernaryReconstructs() throws Exception {
        Path cls = Paths.get("stress-test/classes/S08_Conditionals.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // e.g. ternaryInExpr: (a > b ? a : b) — a ternary whose arms are parameters must survive as ?:.
        assertTrue(flat.matches(".*\\? arg\\d+ : arg\\d+.*"),
                "a ternary with parameter arms must reconstruct as ?: :\n" + src);
    }
}
