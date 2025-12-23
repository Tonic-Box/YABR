package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;

import java.util.Objects;

public final class Breakpoint {

    private final String className;
    private final String methodName;
    private final String methodDesc;
    private final int pc;
    private final int lineNumber;
    private boolean enabled;
    private String condition;
    private int hitCount;

    public Breakpoint(String className, String methodName, String methodDesc, int pc) {
        this(className, methodName, methodDesc, pc, -1);
    }

    public Breakpoint(String className, String methodName, String methodDesc, int pc, int lineNumber) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("Method name cannot be null or empty");
        }
        if (methodDesc == null || methodDesc.isEmpty()) {
            throw new IllegalArgumentException("Method descriptor cannot be null or empty");
        }

        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.pc = pc;
        this.lineNumber = lineNumber;
        this.enabled = true;
        this.condition = null;
        this.hitCount = 0;
    }

    public static Breakpoint methodEntry(String className, String methodName, String methodDesc) {
        return new Breakpoint(className, methodName, methodDesc, -1);
    }

    public static Breakpoint atLine(String className, String methodName, String methodDesc, int line) {
        if (line < 0) {
            throw new IllegalArgumentException("Line number cannot be negative");
        }
        return new Breakpoint(className, methodName, methodDesc, -1, line);
    }

    public static Breakpoint atPC(String className, String methodName, String methodDesc, int pc) {
        if (pc < -1) {
            throw new IllegalArgumentException("PC cannot be less than -1");
        }
        return new Breakpoint(className, methodName, methodDesc, pc);
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public int getPC() {
        return pc;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    public void resetHitCount() {
        this.hitCount = 0;
    }

    public boolean matches(StackFrame frame) {
        if (frame == null) {
            return false;
        }

        String frameClassName = frame.getMethod().getOwnerName();
        String frameMethodName = frame.getMethod().getName();
        String frameMethodDesc = frame.getMethod().getDesc();
        int framePC = frame.getPC();

        return matches(frameClassName, frameMethodName, frameMethodDesc, framePC);
    }

    public boolean matches(String className, String methodName, String methodDesc, int pc) {
        if (!this.className.equals(className)) {
            return false;
        }
        if (!this.methodName.equals(methodName)) {
            return false;
        }
        if (!this.methodDesc.equals(methodDesc)) {
            return false;
        }

        if (this.pc == -1) {
            return pc == 0;
        }

        return this.pc == pc;
    }

    public String getKey() {
        return className + "." + methodName + "+" + methodDesc + "@" + pc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Breakpoint that = (Breakpoint) o;
        return pc == that.pc &&
                lineNumber == that.lineNumber &&
                className.equals(that.className) &&
                methodName.equals(that.methodName) &&
                methodDesc.equals(that.methodDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, methodDesc, pc, lineNumber);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Breakpoint{");
        sb.append(className).append(".").append(methodName).append(methodDesc);
        sb.append(" @pc=").append(pc);
        if (lineNumber >= 0) {
            sb.append(", line=").append(lineNumber);
        }
        sb.append(", enabled=").append(enabled);
        if (condition != null) {
            sb.append(", condition='").append(condition).append("'");
        }
        sb.append(", hits=").append(hitCount);
        sb.append("}");
        return sb.toString();
    }
}
