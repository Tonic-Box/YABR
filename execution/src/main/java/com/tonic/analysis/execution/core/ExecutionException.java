package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.frame.StackFrame;

/**
 * Runtime failure during bytecode execution, carrying the frame, pc, and opcode where it occurred.
 */
public class ExecutionException extends RuntimeException
{
    private final StackFrame frame;
    private final int pc;
    private final String opcode;

    /**
     * Creates an execution exception locating the failure in the executing frame.
     * @param message the error description
     * @param frame the frame that was executing, may be null
     * @param pc the program counter at the failure
     * @param opcode mnemonic of the failing instruction, may be null
     */
    public ExecutionException(String message, StackFrame frame, int pc, String opcode)
    {
        super(formatMessage(message, frame, pc, opcode));
        this.frame = frame;
        this.pc = pc;
        this.opcode = opcode;
    }

    /**
     * Creates an execution exception with a cause, locating the failure in the executing frame.
     * @param message the error description
     * @param frame the frame that was executing, may be null
     * @param pc the program counter at the failure
     * @param opcode mnemonic of the failing instruction, may be null
     * @param cause the underlying exception
     */
    public ExecutionException(String message, StackFrame frame, int pc, String opcode, Throwable cause)
    {
        super(formatMessage(message, frame, pc, opcode), cause);
        this.frame = frame;
        this.pc = pc;
        this.opcode = opcode;
    }

    /**
     * @return the frame
     */
    public StackFrame getFrame()
    {
        return frame;
    }

    /**
     * @return the pc
     */
    public int getPc()
    {
        return pc;
    }

    /**
     * @return the opcode
     */
    public String getOpcode()
    {
        return opcode;
    }

    private static String formatMessage(String message, StackFrame frame, int pc, String opcode)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(message);

        if (frame != null)
        {
            sb.append(" at ").append(frame.getMethodSignature());
            sb.append(" (pc=").append(pc);

            int lineNumber = frame.getLineNumber();
            if (lineNumber >= 0)
            {
                sb.append(", line=").append(lineNumber);
            }
            sb.append(")");
        }

        if (opcode != null)
        {
            sb.append(" [opcode: ").append(opcode).append("]");
        }

        return sb.toString();
    }
}
