package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.frame.StackFrame;

public class ExecutionException extends RuntimeException {
    private final StackFrame frame;
    private final int pc;
    private final String opcode;

    public ExecutionException(String message, StackFrame frame, int pc, String opcode) {
        super(formatMessage(message, frame, pc, opcode));
        this.frame = frame;
        this.pc = pc;
        this.opcode = opcode;
    }

    public ExecutionException(String message, StackFrame frame, int pc, String opcode, Throwable cause) {
        super(formatMessage(message, frame, pc, opcode), cause);
        this.frame = frame;
        this.pc = pc;
        this.opcode = opcode;
    }

    public StackFrame getFrame() {
        return frame;
    }

    public int getPc() {
        return pc;
    }

    public String getOpcode() {
        return opcode;
    }

    private static String formatMessage(String message, StackFrame frame, int pc, String opcode) {
        StringBuilder sb = new StringBuilder();
        sb.append(message);

        if (frame != null) {
            sb.append(" at ").append(frame.getMethodSignature());
            sb.append(" (pc=").append(pc);

            int lineNumber = frame.getLineNumber();
            if (lineNumber >= 0) {
                sb.append(", line=").append(lineNumber);
            }
            sb.append(")");
        }

        if (opcode != null) {
            sb.append(" [opcode: ").append(opcode).append("]");
        }

        return sb.toString();
    }
}
