package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a while loop that is the first statement of a method has its condition-testing header
 * as the entry block, whose only predecessor is the back-edge. The do-while classifier keys off
 * "every header predecessor is inside the loop", which is vacuously true here — so the pre-tested
 * while was mis-recovered as a do-while, moving the condition below the body. For {@code drain} that
 * means the guarded {@code pop()} runs before the emptiness check and throws on an empty deque.
 */
public class EntryWhileLoopDecompileTest {

    @Test
    public void entryWhileLoopStaysPreTested() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("EntryWhileLoop");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.contains("while (!this.queue.isEmpty())"),
                "the entry-block loop must recover as a pre-tested while:\n" + src);
        assertFalse(flat.matches(".*\\bdo\\s*\\{.*"),
                "the entry-block loop must not become a do-while:\n" + src);
    }
}
