package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: when a switch's cases converge on a complex post-switch tail (early return, a loop),
 * the throwing default keeps that tail from post-dominating the switch, so the merge was not found
 * and the tail was absorbed into the first case (ending it with a return) while the other cases got
 * a bare break - dropping the tail for every case but the first. The tail must be recovered once
 * after the switch so it runs for all cases.
 */
public class SwitchSharedTailDecompileTest {

    @Test
    public void sharedTailEmittedAfterSwitchNotAbsorbedIntoFirstCase() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("SwitchSharedTail");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.matches(".*default:[^{}]*\\}.*if \\(!check\\(.*"),
                "the shared tail must be emitted after the switch (past the default), not inside a case:\n" + src);
        assertFalse(flat.matches(".*case 1:.*if \\(!check\\(.*case 2:.*"),
                "the first case must not absorb the shared tail:\n" + src);
        assertFalse(flat.matches(".*case 3:\\s*setC\\(\\w+\\);\\s*if \\(!check\\(.*"),
                "the last case must not absorb the shared tail:\n" + src);
    }
}
