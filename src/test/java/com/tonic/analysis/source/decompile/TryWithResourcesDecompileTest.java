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

/** Regression for bug 5: try-with-resources reconstructs (and the throws clause is emitted). */
public class TryWithResourcesDecompileTest {

    @Test
    public void tryWithResourcesReconstructs() throws Exception {
        Path cls = Paths.get("stress-test/classes/S03_Exceptions.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // tryWithResources(byte[]) folds to try (...) and the synthetic close/suppress cleanup is gone.
        assertTrue(flat.contains("try ("), "try-with-resources must reconstruct:\n" + src);
        assertFalse(src.contains("addSuppressed"), "synthetic addSuppressed must be dropped:\n" + src);
        // The method declares IOException; the throws clause must be emitted.
        assertTrue(flat.contains("throws "), "throws clause must be emitted:\n" + src);
    }
}
