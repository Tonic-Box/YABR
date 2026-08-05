package com.tonic.parser.attribute.table;

/**
 * Represents an entry in the exception table of a Code attribute.
 * Describes a try-catch block with handler location and exception type.
 */
public class ExceptionTableEntry
{
    private int startPc;
    private int endPc;
    private int handlerPc;
    private final int catchType;

    /**
     * Constructs an exception table entry.
     * @param startPc start of the try block (inclusive)
     * @param endPc end of the try block (exclusive)
     * @param handlerPc start of the exception handler
     * @param catchType constant pool index of the exception class, or 0 for any
     */
    public ExceptionTableEntry(int startPc, int endPc, int handlerPc, int catchType)
    {
        this.startPc = startPc;
        this.endPc = endPc;
        this.handlerPc = handlerPc;
        this.catchType = catchType;
    }

    /**
     * @return the inclusive start program counter of the try block
     */
    public int getStartPc()
    {
        return startPc;
    }

    /**
     * @param startPc the new inclusive start program counter
     */
    public void setStartPc(int startPc)
    {
        this.startPc = startPc;
    }

    /**
     * @return the exclusive end program counter of the try block
     */
    public int getEndPc()
    {
        return endPc;
    }

    /**
     * @param endPc the new exclusive end program counter
     */
    public void setEndPc(int endPc)
    {
        this.endPc = endPc;
    }

    /**
     * @return the handler program counter
     */
    public int getHandlerPc()
    {
        return handlerPc;
    }

    /**
     * @param handlerPc the new handler program counter
     */
    public void setHandlerPc(int handlerPc)
    {
        this.handlerPc = handlerPc;
    }

    /**
     * @return the catch type index, or 0 for any exception
     */
    public int getCatchType()
    {
        return catchType;
    }

    @Override
    public String toString()
    {
        return "ExceptionTableEntry{" +
                "startPc=" + startPc +
                ", endPc=" + endPc +
                ", handlerPc=" + handlerPc +
                ", catchType=" + catchType +
                '}';
    }
}