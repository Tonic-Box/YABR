package com.tonic.fixtures;

import java.util.Set;

public class OrConditionBodyTail {
    Set<String> caps;

    /**
     * A short-circuit `a || b || c` guarding an if-then with a side-effecting body and a shared tail
     * after it. The compound condition must be recovered as one `if (a || b || c) { body }` with the
     * tail once - not inverted into `if (!a && !b) { c; ...body.. }` (which drops a term, negates the
     * guard, and misplaces the tail).
     */
    void g(boolean a, boolean b, boolean c, StringBuilder sb) {
        sb.append("x");
        if (a || b || c) {
            caps.add("FB");
            sb.append("Y");
        }
        sb.append("after");
    }
}
