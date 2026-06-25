package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug 1: a fall-through case must not get a spurious break injected. */
public class SwitchFallthroughDecompileTest {

    @Test
    public void fallThroughCaseHasNoInjectedBreak() throws Exception {
        Path cls = Paths.get("stress-test/classes/S02_Switches.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // classicFallthrough: cases 1/2 accumulate +10 then fall through to case 3 (no break between them).
        assertTrue(flat.contains("+ 10; case 3:"),
                "case 1/2 must fall through to case 3 without a break:\n" + src);
    }
}
