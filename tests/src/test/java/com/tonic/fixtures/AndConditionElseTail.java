package com.tonic.fixtures;

public class AndConditionElseTail {

    /**
     * A short-circuit `a && b` guarding an if/else, followed by a shared tail, inside a loop (so no
     * method-ending return guards the tail). The compound condition must be recovered as one
     * `if (a && b) { .. } else { .. }` with the tail emitted once - not split into nested ifs with the
     * else hoisted out and the tail duplicated (which would run the tail twice per iteration).
     */
    void loop(boolean a, boolean b, int n, StringBuilder sb) {
        for (int i = 0; i < n; i++) {
            sb.append("x");
            if (a && b) {
                sb.append("T");
            } else {
                sb.append("F");
            }
            sb.append("\n");
        }
    }
}
