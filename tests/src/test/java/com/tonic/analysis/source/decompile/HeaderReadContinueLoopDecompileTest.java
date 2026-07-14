package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression: the at-top induction lift must fire only for a genuine loop exit that tests a constant
 * induction step. A read loop whose header conditional is an internal continue (skip {@code '\r'}) —
 * with the real exit being the EOF return deeper in the body — must not have that skip test turned
 * into a break, which would exit on the first ordinary character. Such a loop is left to the faithful
 * dispatch fallback, never mis-structured into {@code while (true) { ... read() ... != 13) break; }}.
 */
public class HeaderReadContinueLoopDecompileTest {

    @Test
    public void internalContinueNotTurnedIntoBreak() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("HeaderReadContinueLoop");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertFalse(flat.matches(".*while \\(true\\) \\{ if \\([^{}]*read\\(\\)[^{}]*!= 13\\) \\{ break;.*"),
                "the '\\r'-skip continue must not be recovered as an early break:\n" + src);
        assertFalse(flat.matches(".*= \\(char\\) [^;]*read\\(\\) != 13\\) \\{ break;.*"),
                "the fused read/skip test must not become a loop-exiting break:\n" + src);
    }
}
