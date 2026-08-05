package com.tonic.analysis.verifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable outcome of a verification run: validity plus the collected errors and warnings.
 */
public final class VerificationResult
{
    private final boolean valid;
    private final List<VerificationError> errors;
    private final List<VerificationError> warnings;
    private final String className;
    private final String methodName;

    private VerificationResult(boolean valid, List<VerificationError> errors, List<VerificationError> warnings, String className, String methodName)
    {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.className = className;
        this.methodName = methodName;
    }

    /**
     * @return whether valid
     */
    public boolean isValid()
    {
        return valid;
    }

    /**
     * @return the errors
     */
    public List<VerificationError> getErrors()
    {
        return errors;
    }

    /**
     * @return the warnings
     */
    public List<VerificationError> getWarnings()
    {
        return warnings;
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * Creates an unlocated passing result.
     * @return a valid result with no findings
     */
    public static VerificationResult success()
    {
        return new VerificationResult(true, List.of(), List.of(), null, null);
    }

    /**
     * Creates a passing result for a class.
     * @param className the verified class name
     * @return a valid result with no findings
     */
    public static VerificationResult success(String className)
    {
        return new VerificationResult(true, List.of(), List.of(), className, null);
    }

    /**
     * Creates a passing result for a method.
     * @param className the verified class name
     * @param methodName the verified method name
     * @return a valid result with no findings
     */
    public static VerificationResult success(String className, String methodName)
    {
        return new VerificationResult(true, List.of(), List.of(), className, methodName);
    }

    /**
     * Creates an unlocated result from mixed findings; it is still valid if none are ERROR severity.
     * @param errors the findings, errors and warnings mixed
     * @return the result
     */
    public static VerificationResult failure(List<VerificationError> errors)
    {
        List<VerificationError> actualErrors = errors.stream()
                .filter(VerificationError::isError)
                .collect(Collectors.toList());
        List<VerificationError> actualWarnings = errors.stream()
                .filter(VerificationError::isWarning)
                .collect(Collectors.toList());
        return new VerificationResult(actualErrors.isEmpty(), actualErrors, actualWarnings, null, null);
    }

    /**
     * Creates a result for a class from mixed findings; it is still valid if none are ERROR severity.
     * @param errors the findings, errors and warnings mixed
     * @param className the verified class name
     * @return the result
     */
    public static VerificationResult failure(List<VerificationError> errors, String className)
    {
        List<VerificationError> actualErrors = errors.stream()
                .filter(VerificationError::isError)
                .collect(Collectors.toList());
        List<VerificationError> actualWarnings = errors.stream()
                .filter(VerificationError::isWarning)
                .collect(Collectors.toList());
        return new VerificationResult(actualErrors.isEmpty(), actualErrors, actualWarnings, className, null);
    }

    /**
     * Creates a result for a method from mixed findings; it is still valid if none are ERROR severity.
     * @param errors the findings, errors and warnings mixed
     * @param className the verified class name
     * @param methodName the verified method name
     * @return the result
     */
    public static VerificationResult failure(List<VerificationError> errors, String className, String methodName)
    {
        List<VerificationError> actualErrors = errors.stream()
                .filter(VerificationError::isError)
                .collect(Collectors.toList());
        List<VerificationError> actualWarnings = errors.stream()
                .filter(VerificationError::isWarning)
                .collect(Collectors.toList());
        return new VerificationResult(actualErrors.isEmpty(), actualErrors, actualWarnings, className, methodName);
    }

    /**
     * @return errors followed by warnings in one list
     */
    public List<VerificationError> getAllIssues()
    {
        return Stream.concat(errors.stream(), warnings.stream())
                .collect(Collectors.toList());
    }

    /**
     * @return the number of errors
     */
    public int getErrorCount()
    {
        return errors.size();
    }

    /**
     * @return the number of warnings
     */
    public int getWarningCount()
    {
        return warnings.size();
    }

    /**
     * @return the number of errors plus warnings
     */
    public int getTotalIssueCount()
    {
        return errors.size() + warnings.size();
    }

    /**
     * @return true if at least one warning was recorded
     */
    public boolean hasWarnings()
    {
        return !warnings.isEmpty();
    }

    /**
     * @return true if at least one error was recorded
     */
    public boolean hasErrors()
    {
        return !errors.isEmpty();
    }

    /**
     * Combines this result with another, keeping this result's location and unioning the findings.
     * @param other the result to merge in
     * @return a new result valid only if both inputs were valid
     */
    public VerificationResult merge(VerificationResult other)
    {
        List<VerificationError> mergedErrors = new ArrayList<>(this.errors);
        mergedErrors.addAll(other.errors);
        List<VerificationError> mergedWarnings = new ArrayList<>(this.warnings);
        mergedWarnings.addAll(other.warnings);
        return new VerificationResult(
                this.valid && other.valid,
                mergedErrors,
                mergedWarnings,
                this.className,
                this.methodName
        );
    }

    /**
     * Formats a multi-line pass/fail report listing every error and warning.
     * @return the formatted report
     */
    public String formatReport()
    {
        StringBuilder sb = new StringBuilder();

        if (className != null)
        {
            sb.append("Verification Report for ");
            sb.append(className);
            if (methodName != null)
            {
                sb.append(".").append(methodName);
            }
            sb.append("\n");
            sb.append("=".repeat(60)).append("\n\n");
        }

        if (valid && warnings.isEmpty())
        {
            sb.append("Verification PASSED - No issues found.\n");
            return sb.toString();
        }

        if (valid)
        {
            sb.append("Verification PASSED with ").append(warnings.size()).append(" warning(s).\n\n");
        }
        else
        {
            sb.append("Verification FAILED with ").append(errors.size()).append(" error(s)");
            if (!warnings.isEmpty())
            {
                sb.append(" and ").append(warnings.size()).append(" warning(s)");
            }
            sb.append(".\n\n");
        }

        if (!errors.isEmpty())
        {
            sb.append("ERRORS:\n");
            sb.append("-".repeat(40)).append("\n");
            for (VerificationError error : errors)
            {
                sb.append("  ").append(error.format()).append("\n");
            }
            sb.append("\n");
        }

        if (!warnings.isEmpty())
        {
            sb.append("WARNINGS:\n");
            sb.append("-".repeat(40)).append("\n");
            for (VerificationError warning : warnings)
            {
                sb.append("  ").append(warning.format()).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString()
    {
        if (valid)
        {
            return "VerificationResult[PASS" +
                   (warnings.isEmpty() ? "" : ", " + warnings.size() + " warnings") + "]";
        }
        else
        {
            return "VerificationResult[FAIL, " + errors.size() + " errors" +
                   (warnings.isEmpty() ? "" : ", " + warnings.size() + " warnings") + "]";
        }
    }
}
