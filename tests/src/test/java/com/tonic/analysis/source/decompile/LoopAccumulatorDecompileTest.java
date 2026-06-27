package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug B: a loop accumulator seeded from a parameter keeps its entry value and stays in scope. */
public class LoopAccumulatorDecompileTest {

    @Test
    public void accumulatorSeededFromParameter() throws Exception {
        Path cls = Paths.get("stress-test/classes/S05_StringsArrays.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // varargs(int first, int... rest): int s = first; for (r : rest) s += r; return s;
        // The accumulator must not be declared self-referentially inside the loop (`int x = x + ...`).
        assertFalse(flat.matches(".*int (local\\d+) = \\1 [-+].*"),
                "accumulator must not be declared self-referentially inside the loop:\n" + src);
        assertTrue(flat.contains("varargs"), "varargs method present");
    }
}
