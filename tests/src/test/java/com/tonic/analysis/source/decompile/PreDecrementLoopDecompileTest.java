package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a pre-decrement countdown loop ({@code for (int i = n; --i >= 0;)}) compiles with the
 * induction update in the loop header, before the exit test. A top-tested while recovery drops the
 * header's stores, so the decrement vanished and the loop became {@code int i = n; while (i >= 0) {
 * arr[i] = null; }} — an infinite loop whose very first access reads {@code arr[n]} out of bounds.
 * The header's store must survive as a leading body statement guarded by an early break.
 */
public class PreDecrementLoopDecompileTest {

    @Test
    public void headerResidentDecrementSurvives() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("PreDecrementLoop");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.matches(".*\\b(\\w+) = \\1 \\+ -1;.*") || flat.matches(".*\\b(\\w+) = \\1 - 1;.*"),
                "the header-resident decrement must survive inside the loop:\n" + src);
        assertFalse(flat.matches(".*= this\\.table\\.length; while \\(\\w+ >= 0\\) \\{ this\\.table\\[\\w+\\] = null; \\}.*"),
                "the decrement must not be dropped, leaving a plain while over table.length:\n" + src);
    }
}
