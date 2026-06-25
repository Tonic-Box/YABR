package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug 8: an exhaustive sealed pattern switch reconstructs (MatchException default dropped). */
public class PatternSwitchDecompileTest {

    @Test
    public void exhaustiveSealedSwitchReconstructs() throws Exception {
        Path cls = Paths.get("stress-test/classes/S11_PatternSwitch.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // eval(Expr) is an exhaustive switch over a sealed type; it must fold to pattern-switch arms.
        assertTrue(flat.contains("case S11_PatternSwitch.Num local") && flat.contains("-> local"),
                "exhaustive sealed switch must reconstruct to pattern-switch arms:\n" + src);
        assertTrue(flat.contains("case S11_PatternSwitch.Add") && flat.contains("case S11_PatternSwitch.Neg"),
                "all sealed-permitted arms must be present:\n" + src);
    }
}
