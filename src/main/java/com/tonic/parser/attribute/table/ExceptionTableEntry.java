package com.tonic.parser.attribute.table;

/**
 * Represents an entry in the exception table of a Code attribute.
 * Describes a try-catch block with handler location and exception type.
 */
public class ExceptionTableEntry {
    private final int startPc;
    private final int endPc;
    private final int handlerPc;
    private final int catchType;

    /**
     * Constructs an exception table entry.
     *
     * @param startPc start of the try block (inclusive)
     * @param endPc end of the try block (exclusive)
     * @param handlerPc start of the exception handler
     * @param catchType constant pool index of the exception class, or 0 for any
     */
    public ExceptionTableEntry(int startPc, int endPc, int handlerPc, int catchType) {
        this.startPc = startPc;
        this.endPc = endPc;
        this.handlerPc = handlerPc;
        this.catchType = catchType;
    }

    /**
     * Returns the start offset of the try block.
     *
     * @return the start program counter
     */
    public int getStartPc() {
        return startPc;
    }

    /**
     * Returns the end offset of the try block.
     *
     * @return the end program counter
     */
    public int getEndPc() {
        return endPc;
    }

    /**
     * Returns the start offset of the exception handler.
     *
     * @return the handler program counter
     */
    public int getHandlerPc() {
        return handlerPc;
    }

    /**
     * Returns the constant pool index of the exception class.
     *
     * @return the catch type index, or 0 for any exception
     */
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