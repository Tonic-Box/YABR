package com.tonic.fixtures;

import java.util.Deque;

public class EntryWhileLoop {
    Deque<Object> queue;

    /**
     * The while loop is the method's first statement, so its condition-testing header is the
     * entry block whose only predecessor is the back-edge. It must still recover as a pre-tested
     * while: an empty queue skips the body rather than popping first.
     */
    public void drain() {
        while (!queue.isEmpty()) {
            Object item = queue.pop();
            consume(item);
        }
    }

    void consume(Object o) {
    }
}
