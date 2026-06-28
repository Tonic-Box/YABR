package com.tonic.analysis.absexec;

import java.util.ArrayList;
import java.util.List;

/**
 * One local-variable slot's contents during abstract execution: a link to the {@link InsnContext} that stored
 * it and the contexts that read it, plus a parameter flag. Port of RuneLite's {@code VariableContext} minus
 * the value domain.
 */
public final class VarCtx {

    private final InsnContext storedBy; // the instruction that stored this (null for entry parameters)
    private final boolean wide;
    private final List<InsnContext> reads = new ArrayList<>();
    private boolean parameter;

    /** A value stored into a local by {@code storedBy}. */
    public VarCtx(InsnContext storedBy, boolean wide) {
        this.storedBy = storedBy;
        this.wide = wide;
    }

    /** An entry parameter (no storing instruction). */
    public VarCtx(boolean wide) {
        this.storedBy = null;
        this.wide = wide;
    }

    public InsnContext getInstructionWhichStored() {
        return storedBy;
    }

    public boolean isWide() {
        return wide;
    }

    public boolean isParameter() {
        return parameter;
    }

    public void addRead(InsnContext ctx) {
        if (!reads.contains(ctx)) {
            reads.add(ctx);
        }
    }

    public List<InsnContext> getRead() {
        return reads;
    }

    public VarCtx markParameter() {
        parameter = true;
        return this;
    }
}
