package com.tonic.analysis.verifier;

import java.util.Objects;

/**
 * One immutable verification finding: a typed error or warning with an optional bytecode offset and location.
 */
public final class VerificationError
{
    private final VerificationErrorType type;
    private final int bytecodeOffset;
    private final String message;
    private final Severity severity;
    private final String methodName;
    private final String className;

    /**
     * Severity of a finding: ERROR fails verification, WARNING does not.
     */
    public enum Severity
    {
        /**
         * A finding that fails verification; also the severity applied when a
         * caller passes none.
         */
        ERROR,
        /**
         * A suspicious but tolerable finding; verification still succeeds and
         * the class is usable.
         */
        WARNING
    }

    /**
     * Creates an ERROR-severity finding with no location.
     * @param type the error category
     * @param bytecodeOffset the offending bytecode offset, or -1 if not tied to an offset
     * @param message the detail message, or null for the type's description
     */
    public VerificationError(VerificationErrorType type, int bytecodeOffset, String message)
    {
        this(type, bytecodeOffset, message, Severity.ERROR, null, null);
    }

    /**
     * Creates a finding with an explicit severity and no location.
     * @param type the error category
     * @param bytecodeOffset the offending bytecode offset, or -1 if not tied to an offset
     * @param message the detail message, or null for the type's description
     * @param severity the severity, or null for ERROR
     */
    public VerificationError(VerificationErrorType type, int bytecodeOffset, String message, Severity severity)
    {
        this(type, bytecodeOffset, message, severity, null, null);
    }

    /**
     * Creates a finding with an explicit severity and location; null message and severity fall back to
     * the type's description and ERROR.
     * @param type the error category
     * @param bytecodeOffset the offending bytecode offset, or -1 if not tied to an offset
     * @param message the detail message, or null for the type's description
     * @param severity the severity, or null for ERROR
     * @param className the enclosing class name, or null
     * @param methodName the enclosing method name, or null
     */
    public VerificationError(VerificationErrorType type, int bytecodeOffset, String message, Severity severity, String className, String methodName)
    {
        this.type = Objects.requireNonNull(type, "type");
        this.bytecodeOffset = bytecodeOffset;
        this.message = message != null ? message : type.getDescription();
        this.severity = severity != null ? severity : Severity.ERROR;
        this.className = className;
        this.methodName = methodName;
    }

    /**
     * Copies this error with a class and method location attached.
     * @param className the enclosing class name
     * @param methodName the enclosing method name
     * @return a new error with the location set
     */
    public VerificationError withLocation(String className, String methodName)
    {
        return new VerificationError(type, bytecodeOffset, message, severity, className, methodName);
    }

    /**
     * @return the type
     */
    public VerificationErrorType getType()
    {
        return type;
    }

    /**
     * @return the bytecode offset
     */
    public int getBytecodeOffset()
    {
        return bytecodeOffset;
    }

    /**
     * @return the message
     */
    public String getMessage()
    {
        return message;
    }

    /**
     * @return the severity
     */
    public Severity getSeverity()
    {
        return severity;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return true if severity is ERROR
     */
    public boolean isError()
    {
        return severity == Severity.ERROR;
    }

    /**
     * @return true if severity is WARNING
     */
    public boolean isWarning()
    {
        return severity == Severity.WARNING;
    }

    /**
     * Formats this finding as one line with severity, type, location, offset, and message.
     * @return the formatted line
     */
    public String format()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(severity == Severity.ERROR ? "ERROR" : "WARNING");
        sb.append(" [").append(type.name()).append("]");

        if (className != null)
        {
            sb.append(" in ").append(className);
            if (methodName != null)
            {
                sb.append(".").append(methodName);
            }
        }

        if (bytecodeOffset >= 0)
        {
            sb.append(" at offset ").append(bytecodeOffset);
        }

        sb.append(": ").append(message);
        return sb.toString();
    }

    @Override
    public String toString()
    {
        return format();
    }

    @Override
    public boolean equals(Object o)
    {
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
    public int hashCode()
    {
        return Objects.hash(type, bytecodeOffset, message, severity, className, methodName);
    }
}
