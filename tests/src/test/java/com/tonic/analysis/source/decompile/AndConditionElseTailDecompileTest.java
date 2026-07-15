package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a short-circuit `a && b` guarding an if/else with a shared tail, inside a loop, was
 * mis-recovered as `if (a) { if (b) { then } tail } elseBody tail` - the compound condition split
 * into nested ifs, the else hoisted out unconditionally, and the tail duplicated (executing twice
 * per iteration, and running the else even when a && b is true). It must recover as one
 * `if (a && b) { then } else { elseBody }` with the tail emitted once.
 */
public class AndConditionElseTailDecompileTest {

    @Test
    public void compoundAndWithElseAndSharedTail() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("AndConditionElseTail");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        assertTrue(flat.contains("if (a && b)"),
                "the short-circuit condition must be recovered as a single `a && b`:\n" + src);
        assertTrue(flat.matches(".*if \\(a && b\\) \\{ sb\\.append\\(\"T\"\\); \\} else \\{ sb\\.append\\(\"F\"\\); \\}.*"),
                "the else must stay attached to the compound condition:\n" + src);
        assertEquals(1, countOccurrences(flat, "sb.append(\"\\n\")"),
                "the shared tail must be emitted exactly once, not duplicated:\n" + src);
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
