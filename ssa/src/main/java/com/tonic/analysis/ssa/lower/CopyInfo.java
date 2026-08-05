package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;

/**
 * A phi copy's created SSA value and the block it was inserted in, used for register coalescing.
 */
public final class CopyInfo
{
    private final SSAValue copyValue;
    private final IRBlock block;

    /**
     * Creates a copy record.
     * @param copyValue the SSA value created for the copy
     * @param block the block the copy was inserted in
     */
    public CopyInfo(SSAValue copyValue, IRBlock block)
    {
        this.copyValue = copyValue;
        this.block = block;
    }

    /**
     * @return the SSA value created for the copy
     */
    public SSAValue copyValue() { return copyValue; }

    /**
     * @return the block the copy was inserted in
     */
    public IRBlock block() { return block; }
}
