package com.tonic.fixtures;

import java.util.Iterator;
import java.util.List;

public class SwitchSharedTail {

    /**
     * Every non-default case falls through to a shared post-switch tail (an early-return guard, a
     * loop, and a final add). The tail must be emitted once after the switch so it runs for every
     * case - not absorbed into the first case (which would drop it for the others, since the
     * throwing default keeps the tail from post-dominating the switch).
     */
    void f(int mode, List<Object> out, List<Object> src) {
        Object t = make();
        switch (mode) {
            case 1: setA(t); break;
            case 2: setB(t); break;
            case 3: setC(t); break;
            default: throw new RuntimeException("bad");
        }
        if (!check(t)) {
            cleanup();
            return;
        }
        Iterator<Object> it = src.iterator();
        while (it.hasNext()) {
            out.add(it.next());
        }
        out.add(t);
    }

    Object make() {
        return null;
    }

    void setA(Object t) {
    }

    void setB(Object t) {
    }

    void setC(Object t) {
    }

    boolean check(Object t) {
        return true;
    }

    void cleanup() {
    }
}
