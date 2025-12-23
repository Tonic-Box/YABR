package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;

import java.util.Objects;

public final class StackFrameInfo {

    private final String methodSignature;
    private final int pc;
    private final int lineNumber;

    public StackFrameInfo(StackFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("Frame cannot be null");
        }

        this.methodSignature = frame.getMethodSignature();
        this.pc = frame.getPC();
        this.lineNumber = frame.getLineNumber();
    }

    public String getMethodSignature() {
        return methodSignature;
    }

    public int getPC() {
        return pc;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StackFrameInfo)) return false;
        StackFrameInfo that = (StackFrameInfo) o;
        return pc == that.pc &&
               lineNumber == that.lineNumber &&
               methodSignature.equals(that.methodSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodSignature, pc, lineNumber);
    }

    @Override
    public String toString() {
        return "StackFrameInfo{" +
                "method='" + methodSignature + '\'' +
                ", pc=" + pc +
                ", line=" + lineNumber +
                '}';
    }
}
