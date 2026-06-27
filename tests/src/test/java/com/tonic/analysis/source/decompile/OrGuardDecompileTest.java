package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug 3: a short-circuit OR guard around a loop-body assignment must not collapse to unconditional. */
public class OrGuardDecompileTest {

    @Test
    public void shortCircuitOrGuardPreservedInLoop() throws Exception {
        Path cls = Paths.get("stress-test/classes/S06_Generics.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // max(): if (best == null || t.compareTo(best) > 0) best = t; -- the guard must survive.
        assertTrue(flat.contains("== null ||") && flat.contains("compareTo"),
                "short-circuit OR guard must be preserved (not collapsed to unconditional assignment):\n" + src);
    }
}
