package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a short-circuit `a || b || c` guarding an if-then with a side-effecting body and a
 * shared tail was mis-recovered by negating and inverting the guard, dropping a term, and orphaning
 * it as a bare statement - `if (!a && !b) { c; body } tail` - which skipped the body whenever the
 * true condition was the dropped term. It must recover as `if (a || b || c) { body }` with the tail
 * emitted once.
 */
public class OrConditionBodyTailDecompileTest {

    @Test
    public void compoundOrWithBodyAndSharedTail() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("OrConditionBodyTail");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.contains("if (a || b || c)"),
                "the short-circuit condition must be recovered as a single `a || b || c`:\n" + src);
        assertFalse(flat.matches(".*if \\(!a && !b\\).*"),
                "the guard must not be negated/inverted:\n" + src);
        assertEquals(1, countOccurrences(flat, "sb.append(\"after\")"),
                "the shared tail must be emitted exactly once:\n" + src);
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
