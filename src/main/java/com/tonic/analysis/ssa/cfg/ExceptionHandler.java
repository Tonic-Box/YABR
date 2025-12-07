package com.tonic.analysis.ssa.cfg;

import com.tonic.analysis.ssa.type.ReferenceType;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents exception handler information for try-catch blocks.
 */
@Getter
@Setter
public class ExceptionHandler {

    private IRBlock tryStart;
    private IRBlock tryEnd;
    private IRBlock handlerBlock;
    private ReferenceType catchType;

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
