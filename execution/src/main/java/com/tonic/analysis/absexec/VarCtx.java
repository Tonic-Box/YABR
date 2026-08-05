package com.tonic.analysis.absexec;

import java.util.ArrayList;
import java.util.List;

/**
 * One local-variable slot's contents during abstract execution: a link to the {@link InsnContext} that stored
 * it and the contexts that read it, plus a parameter flag. Port of RuneLite's {@code VariableContext} minus
 * the value domain.
 */
public final class VarCtx
{

    private final InsnContext storedBy; // the instruction that stored this (null for entry parameters)
    private final boolean wide;
    private final List<InsnContext> reads = new ArrayList<>();
    private boolean parameter;

    /**
     * A value stored into a local by {@code storedBy}.
     *
     * @param storedBy the context of the store instruction
     * @param wide     true if the value occupies two slots
     */
    public VarCtx(InsnContext storedBy, boolean wide)
    {
        this.storedBy = storedBy;
        this.wide = wide;
    }

    /**
     * An entry parameter (no storing instruction).
     *
     * @param wide true if the value occupies two slots
     */
    public VarCtx(boolean wide)
    {
        this.storedBy = null;
        this.wide = wide;
    }

    /**
     * @return the instruction which stored
     */
    public InsnContext getInstructionWhichStored()
    {
        return storedBy;
    }

    /**
     * @return whether wide
     */
    public boolean isWide()
    {
        return wide;
    }

    /**
     * @return whether parameter
     */
    public boolean isParameter()
    {
        return parameter;
    }

    /**
     * Records a context that read this slot, ignoring duplicates.
     * @param ctx the reading instruction-execution
     */
    public void addRead(InsnContext ctx)
    {
        if (!reads.contains(ctx))
        {
            reads.add(ctx);
        }
    }

    /**
     * @return the read
     */
    public List<InsnContext> getRead()
    {
        return reads;
    }

    /**
     * Flags this slot as an entry parameter.
     * @return this context
     */
    public VarCtx markParameter()
    {
        parameter = true;
        return this;
    }
}
