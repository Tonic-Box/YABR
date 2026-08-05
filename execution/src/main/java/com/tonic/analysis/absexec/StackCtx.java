package com.tonic.analysis.absexec;

import java.util.ArrayList;
import java.util.List;

/**
 * One operand-stack slot during abstract execution: a link from the value to the {@link InsnContext} that
 * pushed it and the contexts that popped it. Port of RuneLite's {@code StackContext} minus the abstract-value
 * field (the ModArith port reads constants from the pushing instruction, not a value domain).
 */
public final class StackCtx
{

    private final InsnContext pushed;
    private final boolean wide; // long/double occupy a logical wide slot
    private final List<InsnContext> popped = new ArrayList<>();
    boolean removed;

    /**
     * Creates a slot for a pushed value.
     * @param pushed the instruction-execution that pushed the value
     * @param wide true for a long/double entry
     */
    public StackCtx(InsnContext pushed, boolean wide)
    {
        this.pushed = pushed;
        this.wide = wide;
    }

    /**
     * @return the instruction-execution that pushed this value
     */
    public InsnContext getPushed()
    {
        return pushed;
    }

    /**
     * @return whether wide
     */
    public boolean isWide()
    {
        return wide;
    }

    /**
     * @return the popped
     */
    public List<InsnContext> getPopped()
    {
        return popped;
    }

    /**
     * Records a context that popped this value, ignoring duplicates.
     * @param ctx the popping instruction-execution
     */
    public void addPopped(InsnContext ctx)
    {
        if (!popped.contains(ctx))
        {
            popped.add(ctx);
        }
    }
}
