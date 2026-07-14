package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: javac splits a synchronized region into several exception-table entries broken at each
 * monitorexit-return. Code after the first region — a second conditional-return loop and the trailing
 * return — must survive: the continuation is found past the whole region (not the first sub-range), and
 * a monitorexit-carrying return block is still an indirect return, so the second loop keeps its guard
 * instead of collapsing to an unconditional return.
 */
public class SynchronizedRegionDecompileTest {

    @Test
    public void secondLoopAndTailSurviveInSynchronizedBlock() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("SynchronizedTwoLoop");
        String src = ClassDecompiler.decompile(cf);

        assertTrue(src.contains("synchronized"), "expected a synchronized block:\n" + src);

        // Both loops must keep their isInstance guard — the second loop losing it drops to one.
        int guards = countOccurrences(src, "isInstance(");
        assertEquals(2, guards, "both loops must keep their conditional guard:\n" + src);

        // The trailing return after the synchronized block must not be dropped.
        assertTrue(src.replaceAll("\\s+", " ").contains("return null"),
                "the trailing return after the synchronized block must survive:\n" + src);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
