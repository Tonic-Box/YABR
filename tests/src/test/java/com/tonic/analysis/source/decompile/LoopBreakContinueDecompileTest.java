package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug 2: mid-loop break/continue, including labeled break/continue to an outer loop. */
public class LoopBreakContinueDecompileTest {

    @Test
    public void labeledBreakAndContinueReconstruct() throws Exception {
        Path cls = Paths.get("stress-test/classes/S01_ControlFlow.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // nestedLoops has `continue outer`/`break outer` -> the outer loop must be labeled and a labeled
        // break/continue emitted (previously the break outer was silently dropped).
        assertTrue(flat.matches(".*label\\d+ ?: for .*"), "outer loop must be labeled:\n" + src);
        assertTrue(flat.contains("break label") || flat.contains("continue label"),
                "a labeled break/continue must be emitted:\n" + src);
    }
}
