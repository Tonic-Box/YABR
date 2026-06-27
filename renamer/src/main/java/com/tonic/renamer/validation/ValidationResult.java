package com.tonic.renamer.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains the results of validating rename mappings.
 */
public class ValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * Adds an error to the result.
     */
    public void addError(String message) {
        errors.add(message);
    }

    /**
     * Adds a warning to the result.
     */
    public void addWarning(String message) {
        warnings.add(message);
    }

    /**
     * Returns true if validation passed (no errors).
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Returns true if there are any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns true if there are any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Returns the list of error messages.
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Returns the list of warning messages.
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Returns the number of errors.
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Returns the number of warnings.
     */
    public int getWarningCount() {
        return warnings.size();
    }

    /**
     * Merges another ValidationResult into this one.
     */
    public void merge(ValidationResult other) {
        errors.addAll(other.errors);
        warnings.addAll(other.warnings);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{");
        if (isValid()) {
            sb.append("VALID");
        } else {
            sb.append("INVALID, ").append(errors.size()).append(" error(s)");
        }
        if (hasWarnings()) {
            sb.append(", ").append(warnings.size()).append(" warning(s)");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Returns a detailed report of all errors and warnings.
     */
    public String getReport() {
        StringBuilder sb = new StringBuilder();
        if (!errors.isEmpty()) {
            sb.append("Errors:\n");
            for (String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
        }
        if (!warnings.isEmpty()) {
            sb.append("Warnings:\n");
            for (String warning : warnings) {
                sb.append("  - ").append(warning).append("\n");
            }
        }
        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("No errors or warnings.\n");
        }
        return sb.toString();
    }
}
