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

/** Regression for bug 7: references to named nested classes use the source '.' form, not binary '$'. */
public class NestedClassNameDecompileTest {

    @Test
    public void nestedTypeReferencesUseDotNotDollar() throws Exception {
        Path cls = Paths.get("stress-test/classes/S10_EnumsRecords.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();

        assertFalse(src.contains("S10_EnumsRecords$Point"),
                "nested reference must use '.' not '$':\n" + src);
        assertTrue(src.contains("S10_EnumsRecords.Point") || src.contains("new Point"),
                "expected a dotted/simple nested reference:\n" + src);
    }
}
