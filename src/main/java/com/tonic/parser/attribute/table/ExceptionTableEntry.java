package com.tonic.parser.attribute.table;

/**
 * Represents an entry in the exception table.
 */
public class ExceptionTableEntry {
    private final int startPc;
    private final int endPc;
    private final int handlerPc;
    private final int catchType;

    public ExceptionTableEntry(int startPc, int endPc, int handlerPc, int catchType) {
        this.startPc = startPc;
        this.endPc = endPc;
        this.handlerPc = handlerPc;
        this.catchType = catchType;
    }

    public int getStartPc() {
        return startPc;
    }

    public int getEndPc() {
        return endPc;
    }

    public int getHandlerPc() {
        return handlerPc;
    }

    public int getCatchType() {
        return catchType;
    }

    @Override
    public String toString() {
        return "ExceptionTableEntry{" +
                "startPc=" + startPc +
                ", endPc=" + endPc +
                ", handlerPc=" + handlerPc +
                ", catchType=" + catchType +
                '}';
    }
}