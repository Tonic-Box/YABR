package com.tonic.analysis.absexec;

import java.util.ArrayList;
import java.util.List;

/**
 * One operand-stack slot during abstract execution.
 */
public final class StackCtx
{

    private final InsnContext pushed;
    private final boolean wide;
    private final List<InsnContext> popped = new ArrayList<>();

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
