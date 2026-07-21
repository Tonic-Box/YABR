package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.Collections;
import java.util.Set;

/**
 * A statically decoded try region treated as one opaque composite node by the reaching-condition
 * engine: the blocks the try/catch recovery will consume (the merged protected ranges plus the catch
 * blocks), and the single join the node continues at. The engine places the node under its dominator
 * like any block, models its only outgoing edge as {@code node -> after}, and delegates the node's
 * recovery to the host's try/catch machinery at emit time.
 */
public final class TryNodeDescriptor {

    private final ExceptionHandler handler;
    private final Set<IRBlock> consumed;
    private final IRBlock after;

    public TryNodeDescriptor(ExceptionHandler handler, Set<IRBlock> consumed, IRBlock after) {
        this.handler = handler;
        this.consumed = Collections.unmodifiableSet(consumed);
        this.after = after;
    }

    public ExceptionHandler handler() {
        return handler;
    }

    /** The blocks owned by the try - never part of the surrounding region. */
    public Set<IRBlock> consumed() {
        return consumed;
    }

    /** The join the node continues at, or null when every path through the try exits the method. */
    public IRBlock after() {
        return after;
    }
}
