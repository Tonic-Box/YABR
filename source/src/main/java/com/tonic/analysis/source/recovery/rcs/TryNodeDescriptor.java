package com.tonic.analysis.source.recovery.rcs;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;

import java.util.Collections;
import java.util.Set;

/**
 * A statically decoded try region treated as one opaque composite node by the reaching-condition engine.
 */
public final class TryNodeDescriptor
{

    private final ExceptionHandler handler;
    private final Set<IRBlock> consumed;
    private final IRBlock after;

    /**
     * Creates a descriptor over an unmodifiable view of the consumed set.
     *
     * @param handler handler the node stands for
     * @param consumed blocks the try/catch recovery will take over
     * @param after join the node continues at, or null when every path exits the method
     */
    public TryNodeDescriptor(ExceptionHandler handler, Set<IRBlock> consumed, IRBlock after)
    {
        this.handler = handler;
        this.consumed = Collections.unmodifiableSet(consumed);
        this.after = after;
    }

    /**
     * @return the handler whose protected range and catch blocks this node stands for
     */
    public ExceptionHandler handler()
    {
        return handler;
    }

    /**
     * @return the blocks owned by the try, never part of the surrounding region
     */
    public Set<IRBlock> consumed()
    {
        return consumed;
    }

    /**
     * @return the join the node continues at, or null when every path through the try exits the method
     */
    public IRBlock after()
    {
        return after;
    }
}
