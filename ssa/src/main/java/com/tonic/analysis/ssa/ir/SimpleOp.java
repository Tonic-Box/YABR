package com.tonic.analysis.ssa.ir;

/**
 * The operation kinds a {@link SimpleInstruction} can perform.
 */
public enum SimpleOp
{
    /**
     * Reads the length of the array operand into the instruction's result.
     */
    ARRAYLENGTH,
    /**
     * Acquires the monitor of the operand object; produces no result.
     */
    MONITORENTER,
    /**
     * Releases the monitor of the operand object; produces no result.
     */
    MONITOREXIT,
    /**
     * Throws the operand, terminating its block with no successor edge.
     */
    ATHROW,
    /**
     * Branches unconditionally to the target block; terminates its block and
     * takes no operand.
     */
    GOTO,
    /**
     * Captures the caught exception that the JVM places on the stack at an exception-handler entry into the
     * instruction's result.
     */
    CATCH
}
