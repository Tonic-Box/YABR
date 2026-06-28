package com.tonic.analysis.absexec;

import java.util.ArrayList;
import java.util.List;

/**
 * One operand-stack slot during abstract execution: a link from the value to the {@link InsnContext} that
 * pushed it and the contexts that popped it. Port of RuneLite's {@code StackContext} minus the abstract-value
 * field (the ModArith port reads constants from the pushing instruction, not a value domain).
 */
public final class StackCtx {

    private final InsnContext pushed;
    private final boolean wide; // long/double occupy a logical wide slot
    private final List<InsnContext> popped = new ArrayList<>();
    boolean removed;

    public StackCtx(InsnContext pushed, boolean wide) {
        this.pushed = pushed;
        this.wide = wide;
    }

    /** Returns the instruction-execution that pushed this value. */
    public InsnContext getPushed() {
        return pushed;
    }

    public boolean isWide() {
        return wide;
    }

    public List<InsnContext> getPopped() {
        return popped;
    }

    public void addPopped(InsnContext ctx) {
        if (!popped.contains(ctx)) {
            popped.add(ctx);
        }
    }
}
