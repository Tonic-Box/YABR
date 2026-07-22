package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.Collections;
import java.util.Set;

/**
 * A switch region the engine's native decoder does not own (a string switch's hash/index scaffolding, a
 * value-yielding switch expression) treated as one opaque composite node: the blocks the host's switch
 * recovery will consume (the scaffolding and every case body), and the single merge the node continues
 * at. The engine places the node under its dominator like any block, models its only outgoing edge as
 * {@code node -> after}, and delegates the node's recovery to the host's switch machinery at emit time.
 */
public final class SwitchNodeDescriptor {

    private final Set<IRBlock> consumed;
    private final IRBlock after;

    public SwitchNodeDescriptor(Set<IRBlock> consumed, IRBlock after) {
        this.consumed = Collections.unmodifiableSet(consumed);
        this.after = after;
    }

    /** The blocks owned by the switch - never part of the surrounding region. */
    public Set<IRBlock> consumed() {
        return consumed;
    }

    /** The merge the node continues at, or null when every case exits the method. */
    public IRBlock after() {
        return after;
    }
}
