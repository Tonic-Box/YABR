package com.tonic.analysis.ssa.lower;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;

/**
 * Information about a phi copy for register coalescing.
 * Stores the SSAValue created for the copy and the block where it was inserted.
 */
public record CopyInfo(SSAValue copyValue, IRBlock block) {}
