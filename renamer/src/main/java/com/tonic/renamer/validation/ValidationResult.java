package com.tonic.renamer.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains the results of validating rename mappings.
 */
public class ValidationResult
{

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    /**
     * Records an error, which makes the result invalid.
     *
     * @param message error text
     */
    public void addError(String message)
    {
        errors.add(message);
    }

    /**
     * Records a warning, which leaves the result valid.
     *
     * @param message warning text
     */
    public void addWarning(String message)
    {
        warnings.add(message);
    }

    /**
     * @return true if no error was recorded
     */
    public boolean isValid()
    {
        return errors.isEmpty();
    }

    /**
     * @return true if at least one error was recorded
     */
    public boolean hasErrors()
    {
        return !errors.isEmpty();
    }

    /**
     * @return true if at least one warning was recorded
     */
    public boolean hasWarnings()
    {
        return !warnings.isEmpty();
    }

    /**
     * @return an unmodifiable view of the error messages
     */
    public List<String> getErrors()
    {
        return Collections.unmodifiableList(errors);
    }

    /**
     * @return an unmodifiable view of the warning messages
     */
    public List<String> getWarnings()
    {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @return the number of recorded errors
     */
    public int getErrorCount()
    {
        return errors.size();
    }

    /**
     * @return the number of recorded warnings
     */
    public int getWarningCount()
    {
        return warnings.size();
    }

    /**
     * Appends another result's errors and warnings to this one.
     *
     * @param other result to absorb
     */
    public void merge(ValidationResult other)
    {
        errors.addAll(other.errors);
        warnings.addAll(other.warnings);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{");
        if (isValid())
        {
            sb.append("VALID");
        }
        else
        {
            sb.append("INVALID, ").append(errors.size()).append(" error(s)");
        }
        if (hasWarnings())
        {
            sb.append(", ").append(warnings.size()).append(" warning(s)");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Renders every error and warning as an indented, sectioned listing.
     *
     * @return the report, or a "no errors or warnings" line when both lists are empty
     */
    public String getReport()
    {
        StringBuilder sb = new StringBuilder();
        if (!errors.isEmpty())
        {
            sb.append("Errors:\n");
            for (String error : errors)
            {
                sb.append("  - ").append(error).append("\n");
            }
        }
        if (!warnings.isEmpty())
        {
            sb.append("Warnings:\n");
            for (String warning : warnings)
            {
                sb.append("  - ").append(warning).append("\n");
            }
        }
        if (errors.isEmpty() && warnings.isEmpty())
        {
            sb.append("No errors or warnings.\n");
        }
        return sb.toString();
    }
}
