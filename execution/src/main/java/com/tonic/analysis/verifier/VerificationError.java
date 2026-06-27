package com.tonic.analysis.verifier;

import lombok.Getter;

import java.util.Objects;

@Getter
public final class VerificationError {
    private final VerificationErrorType type;
    private final int bytecodeOffset;
    private final String message;
    private final Severity severity;
    private final String methodName;
    private final String className;

    public enum Severity {
        ERROR,
        WARNING
    }

    public VerificationError(VerificationErrorType type, int bytecodeOffset, String message) {
        this(type, bytecodeOffset, message, Severity.ERROR, null, null);
    }

    public VerificationError(VerificationErrorType type, int bytecodeOffset, String message, Severity severity) {
        this(type, bytecodeOffset, message, severity, null, null);
    }

    public VerificationError(VerificationErrorType type, int bytecodeOffset, String message,
                             Severity severity, String className, String methodName) {
        this.type = Objects.requireNonNull(type, "type");
        this.bytecodeOffset = bytecodeOffset;
        this.message = message != null ? message : type.getDescription();
        this.severity = severity != null ? severity : Severity.ERROR;
        this.className = className;
        this.methodName = methodName;
    }

    public VerificationError withLocation(String className, String methodName) {
        return new VerificationError(type, bytecodeOffset, message, severity, className, methodName);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity == Severity.ERROR ? "ERROR" : "WARNING");
        sb.append(" [").append(type.name()).append("]");

        if (className != null) {
            sb.append(" in ").append(className);
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
        }

        if (bytecodeOffset >= 0) {
            sb.append(" at offset ").append(bytecodeOffset);
        }

        sb.append(": ").append(message);
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VerificationError)) return false;
        VerificationError that = (VerificationError) o;
        return bytecodeOffset == that.bytecodeOffset &&
               type == that.type &&
               Objects.equals(message, that.message) &&
               severity == that.severity &&
               Objects.equals(className, that.className) &&
               Objects.equals(methodName, that.methodName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, bytecodeOffset, message, severity, className, methodName);
    }
}
