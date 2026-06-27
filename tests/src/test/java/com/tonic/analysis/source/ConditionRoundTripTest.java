package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Round-trip regression test for boolean-condition lowering. {@code if(!x)} / {@code &&} / {@code ||} previously
 * survived JVM verification yet decompiled to corrupted source - the condition was materialized into a 0/1 value via
 * a phi over a temp local and branched on, decompiling to {@code if (cond ? local : local)}. They must now lower as
 * direct branches.
 */
class ConditionRoundTripTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    @Test
    void negatedAndShortCircuitConditionsRoundTrip() throws Exception {
        String src = "package test; public class CondT {"
            + " public static int f(boolean a, boolean b){"
            + "   if (!a) { return 1; }"
            + "   if (a && b) { return 2; }"
            + "   if (a || b) { return 3; }"
            + "   return 0; } }";
        ClassFile cf = TestUtils.compileSource(src, "test/CondT");
        TestUtils.linkAndVerify(cf);
        String out = ClassDecompiler.decompile(cf);
        assertFalse(out.contains("? local"),
            "boolean conditions must not materialize into a `cond ? local : local` ternary:\n" + out);
    }
}
