package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;

/**
 * Information about a phi copy for register coalescing.
 * Stores the SSAValue created for the copy and the block where it was inserted.
 */
public final class CopyInfo {
    private final SSAValue copyValue;
    private final IRBlock block;

    public CopyInfo(SSAValue copyValue, IRBlock block) {
        this.copyValue = copyValue;
        this.block = block;
    }

    public SSAValue copyValue() { return copyValue; }
    public IRBlock block() { return block; }
}
