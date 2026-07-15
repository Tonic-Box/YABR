package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a pre-decrement countdown whose body contains a standalone add/sub (an address-style
 * {@code base + i * stride}) had that arithmetic mistaken for the loop's induction increment. The
 * loop was then routed to the for-loop fallback, which dropped the header's decrement and produced an
 * infinite {@code while (i >= 0)}. The decrement must survive.
 */
public class CountdownWithBodyArithmeticDecompileTest {

    @Test
    public void bodyArithmeticNotMistakenForCounter() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("CountdownWithBodyArithmetic");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.matches(".*\\b(\\w+) = \\1 \\+ -1;.*") || flat.matches(".*\\b(\\w+) = \\1 - 1;.*")
                        || flat.matches(".*\\b\\w+--.*"),
                "the countdown decrement must survive inside the loop:\n" + src);
        assertFalse(flat.matches(".*int (\\w+) = length; while \\(\\1 >= 0\\) \\{ [^{}]*sink\\([^;]*; \\}.*"),
                "the decrement must not be dropped, leaving an infinite while over length:\n" + src);
    }
}
