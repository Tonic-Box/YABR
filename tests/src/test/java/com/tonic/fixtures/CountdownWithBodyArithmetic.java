package com.tonic.fixtures;

public class CountdownWithBodyArithmetic {
    long base;

    /**
     * A pre-decrement countdown ({@code for (int i = length; --i >= 0;)}) whose body computes an
     * address-style offset {@code base + i * stride}. That standalone add/sub in the body must not be
     * mistaken for the loop's induction increment: doing so routes the loop to the for-loop fallback
     * and drops the header's decrement, leaving an infinite {@code while (i >= 0)}.
     */
    void freeAll(long stride, int length) {
        for (int i = length; --i >= 0; ) {
            sink(base + i * stride);
        }
    }

    void sink(long addr) {
    }
}
