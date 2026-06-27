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

/** Regression for bug 4: a multi-argument lambda keeps both parameters (not 1 param + a garbage capture). */
public class MultiArgLambdaDecompileTest {

    @Test
    public void biFunctionLambdaHasTwoParameters() throws Exception {
        Path cls = Paths.get("stress-test/classes/S07_Lambdas.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        // add() is (a, b) -> a + b; the recovered lambda must take two named params and add them.
        assertTrue(flat.matches(".*\\(arg0, arg1\\) ->.*arg0.*\\+.*arg1.*"),
                "BiFunction lambda must have two parameters added together:\n" + src);
        assertFalse(src.matches("(?s).*-> [^()]*v\\d{4,}.*"),
                "lambda body must not reference a raw SSA value id:\n" + src);
    }
}
