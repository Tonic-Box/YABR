package com.tonic.analysis.verifier;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public final class VerificationResult {
    private final boolean valid;
    private final List<VerificationError> errors;
    private final List<VerificationError> warnings;
    private final String className;
    private final String methodName;

    private VerificationResult(boolean valid, List<VerificationError> errors,
                               List<VerificationError> warnings, String className, String methodName) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.className = className;
        this.methodName = methodName;
    }

    public static VerificationResult success() {
        return new VerificationResult(true, List.of(), List.of(), null, null);
    }

    public static VerificationResult success(String className) {
        return new VerificationResult(true, List.of(), List.of(), className, null);
    }

    public static VerificationResult success(String className, String methodName) {
        return new VerificationResult(true, List.of(), List.of(), className, methodName);
    }

    public static VerificationResult failure(List<VerificationError> errors) {
        List<VerificationError> actualErrors = errors.stream()
                .filter(VerificationError::isError)
                .collect(Collectors.toList());
        List<VerificationError> actualWarnings = errors.stream()
                .filter(VerificationError::isWarning)
                .collect(Collectors.toList());
        return new VerificationResult(actualErrors.isEmpty(), actualErrors, actualWarnings, null, null);
    }

    public static VerificationResult failure(List<VerificationError> errors, String className) {
        List<VerificationError> actualErrors = errors.stream()
                .filter(VerificationError::isError)
                .collect(Collectors.toList());
        List<VerificationError> actualWarnings = errors.stream()
                .filter(VerificationError::isWarning)
                .collect(Collectors.toList());
        return new VerificationResult(actualErrors.isEmpty(), actualErrors, actualWarnings, className, null);
    }

    public static VerificationResult failure(List<VerificationError> errors, String className, String methodName) {
        List<VerificationError> actualErrors = errors.stream()
                .filter(VerificationError::isError)
                .collect(Collectors.toList());
        List<VerificationError> actualWarnings = errors.stream()
                .filter(VerificationError::isWarning)
                .collect(Collectors.toList());
        return new VerificationResult(actualErrors.isEmpty(), actualErrors, actualWarnings, className, methodName);
    }

    public List<VerificationError> getAllIssues() {
        return Stream.concat(errors.stream(), warnings.stream())
                .collect(Collectors.toList());
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }

    public int getTotalIssueCount() {
        return errors.size() + warnings.size();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public VerificationResult merge(VerificationResult other) {
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

    public String formatReport() {
        StringBuilder sb = new StringBuilder();

        if (className != null) {
            sb.append("Verification Report for ");
            sb.append(className);
            if (methodName != null) {
                sb.append(".").append(methodName);
            }
            sb.append("\n");
            sb.append("=".repeat(60)).append("\n\n");
        }

        if (valid && warnings.isEmpty()) {
            sb.append("Verification PASSED - No issues found.\n");
            return sb.toString();
        }

        if (valid && !warnings.isEmpty()) {
            sb.append("Verification PASSED with ").append(warnings.size()).append(" warning(s).\n\n");
        } else {
            sb.append("Verification FAILED with ").append(errors.size()).append(" error(s)");
            if (!warnings.isEmpty()) {
                sb.append(" and ").append(warnings.size()).append(" warning(s)");
            }
            sb.append(".\n\n");
        }

        if (!errors.isEmpty()) {
            sb.append("ERRORS:\n");
            sb.append("-".repeat(40)).append("\n");
            for (VerificationError error : errors) {
                sb.append("  ").append(error.format()).append("\n");
            }
            sb.append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("WARNINGS:\n");
            sb.append("-".repeat(40)).append("\n");
            for (VerificationError warning : warnings) {
                sb.append("  ").append(warning.format()).append("\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        if (valid) {
            return "VerificationResult[PASS" +
                   (warnings.isEmpty() ? "" : ", " + warnings.size() + " warnings") + "]";
        } else {
            return "VerificationResult[FAIL, " + errors.size() + " errors" +
                   (warnings.isEmpty() ? "" : ", " + warnings.size() + " warnings") + "]";
        }
    }
}
