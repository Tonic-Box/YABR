package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.Objects;

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

    public SSAValue copyValue() {
        return copyValue;
    }

    public IRBlock block() {
        return block;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CopyInfo)) return false;
        CopyInfo copyInfo = (CopyInfo) o;
        return Objects.equals(copyValue, copyInfo.copyValue) &&
               Objects.equals(block, copyInfo.block);
    }

    @Override
    public int hashCode() {
        return Objects.hash(copyValue, block);
    }

    @Override
    public String toString() {
        return "CopyInfo[copyValue=" + copyValue + ", block=" + block + "]";
    }
}
