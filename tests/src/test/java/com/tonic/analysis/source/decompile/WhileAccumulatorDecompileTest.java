package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression for bug D: a while-loop accumulator (no constant-step counter) must not be promoted into a
 * for-loop header, which would declare it self-referentially and leave the return value out of scope.
 */
public class WhileAccumulatorDecompileTest {

    @Test
    public void whileAccumulatorStaysInScope() throws Exception {
        Path cls = Paths.get("stress-test/classes/S01_ControlFlow.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // sumWhile(seed): total += x; x /= 2 under while (x > 0). The accumulator `total` has a non-constant
        // step's worth of company; it must not become `for (int t = 0; ...; t = t + ...)`.
        assertFalse(flat.matches(".*for \\(int (local\\d+) = 0; [^)]*\\1 = \\1 [-+].*"),
                "while-loop accumulator must not be hoisted into a for-header:\n" + src);
    }
}
