package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.Set;

/**
 * Represents exception handler information for try-catch blocks.
 */
public class ExceptionHandler {

    private IRBlock tryStart;
    private IRBlock tryEnd;
    private IRBlock handlerBlock;
    private ReferenceType catchType;
    private Set<IRBlock> tryBlocks;

    /**
     * Creates an exception handler.
     *
     * @param tryStart the start block of the try region
     * @param tryEnd the end block of the try region
     * @param handlerBlock the handler block for caught exceptions
     * @param catchType the type of exception to catch, or null for catch-all
     */
    public ExceptionHandler(IRBlock tryStart, IRBlock tryEnd, IRBlock handlerBlock, ReferenceType catchType) {
        this.tryStart = tryStart;
        this.tryEnd = tryEnd;
        this.handlerBlock = handlerBlock;
        this.catchType = catchType;
    }

    public IRBlock getTryStart() {
        return tryStart;
    }

    public void setTryStart(IRBlock tryStart) {
        this.tryStart = tryStart;
    }

    public IRBlock getTryEnd() {
        return tryEnd;
    }

    public void setTryEnd(IRBlock tryEnd) {
        this.tryEnd = tryEnd;
    }

    public IRBlock getHandlerBlock() {
        return handlerBlock;
    }

    public void setHandlerBlock(IRBlock handlerBlock) {
        this.handlerBlock = handlerBlock;
    }

    public ReferenceType getCatchType() {
        return catchType;
    }

    public void setCatchType(ReferenceType catchType) {
        this.catchType = catchType;
    }

    /**
     * Returns the full set of blocks making up the protected (try) region, when known. Lets the exception
     * table be regenerated as one entry per maximal contiguous PC run, which correctly handles a nested try
     * whose body is split into non-contiguous ranges by an interleaved handler. Null when unknown (e.g.
     * lifted handlers), in which case the {@code tryStart}/{@code tryEnd} block pair is used instead.
     */
    public Set<IRBlock> getTryBlocks() {
        return tryBlocks;
    }

    public void setTryBlocks(Set<IRBlock> tryBlocks) {
        this.tryBlocks = tryBlocks;
    }

    /**
     * Checks if this is a catch-all handler.
     *
     * @return true if catches all exception types, false otherwise
     */
    public boolean isCatchAll() {
        return catchType == null;
    }

    @Override
    public String toString() {
        String type = catchType != null ? catchType.getInternalName() : "*";
        return "handler(" + type + ") -> " + handlerBlock.getName();
    }
}
