package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a loop-carried boolean seeded false before the loop and set true inside must keep its
 * false entry value. Two ways it could flip to true: the loop-header phi's entry value is materialized
 * under the phi's own slot name and becomes a {@code T v = v} self-initializer, and a latch/merge phi
 * inside the loop body offers its mid-loop {@code = true} store as a bogus "entry" seed. Either flips
 * the flag to true at declaration; for this recursion guard that means it never terminates.
 */
public class LoopCarriedBooleanDecompileTest {

    @Test
    public void loopCarriedBooleanKeepsFalseEntry() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("LoopCarriedBooleanFlag");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.contains("forMacro"), "expected the recovered method:\n" + src);
        // The flag's declaration (type-prefixed) must not carry a true/1 initializer, and must not be a
        // self-reference. The in-loop `flag = true` is a bare assignment (no type prefix), so it is exempt.
        assertFalse(flat.matches(".*\\b(?:boolean|int) (\\w+) = \\1;.*"),
                "loop-carried flag must not be declared self-referentially:\n" + src);
        assertFalse(flat.matches(".*\\b(?:boolean|int) \\w+ = (?:true|1);.*"),
                "loop-carried flag must be initialized false, not true:\n" + src);
    }
}
