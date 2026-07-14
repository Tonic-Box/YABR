package com.tonic.fixtures;

public class PreDecrementLoop {
    Object[] table;

    /**
     * A pre-decrement countdown loop: the induction update lives in the loop header, before the
     * exit test. It must recover with the decrement preserved and guarded, never as a plain while
     * whose first access reads {@code table[table.length]} out of bounds.
     */
    void clear() {
        for (int i = table.length; --i >= 0; ) {
            table[i] = null;
        }
    }
}
