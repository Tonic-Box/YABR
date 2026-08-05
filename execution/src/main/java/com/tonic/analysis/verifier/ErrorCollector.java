package com.tonic.analysis.verifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sink for verification errors and warnings, with fail-fast and collect-all (capped) implementations.
 */
public interface ErrorCollector
{

    /**
     * Records an error.
     *
     * @param error the error to record
     */
    void addError(VerificationError error);

    /**
     * Records a warning.
     *
     * @param warning the warning to record
     */
    void addWarning(VerificationError warning);

    /**
     * Routes an entry to {@link #addError} or {@link #addWarning} according to its severity.
     *
     * @param error the entry to record
     */
    default void add(VerificationError error)
    {
        if (error.isError())
        {
            addError(error);
        }
        else
        {
            addWarning(error);
        }
    }

    /**
     * @return the recorded errors
     */
    List<VerificationError> getErrors();

    /**
     * @return the recorded warnings
     */
    List<VerificationError> getWarnings();

    /**
     * @return true when at least one error has been recorded
     */
    boolean hasErrors();

    /**
     * @return true when at least one warning has been recorded
     */
    boolean hasWarnings();

    /**
     * @return true when verification should give up rather than report further errors
     */
    boolean shouldStop();

    /**
     * Discards every recorded error and warning.
     */
    void reset();

    /**
     * @return a collector that keeps only the first error and stops verification there
     */
    static ErrorCollector failFast()
    {
        return new FailFastCollector();
    }

    /**
     * Creates a collector that gathers errors up to a cap.
     *
     * @param maxErrors the number of errors to retain before stopping
     * @return the collector
     */
    static ErrorCollector collectAll(int maxErrors)
    {
        return new CollectAllCollector(maxErrors);
    }

    /**
     * Creates the collector a verifier configuration asks for.
     *
     * @param config the verifier configuration
     * @return a fail-fast collector when the config requests one, otherwise a capped collect-all collector
     */
    static ErrorCollector forConfig(VerifierConfig config)
    {
        if (config.isFailFast())
        {
            return failFast();
        }
        else
        {
            return collectAll(config.getMaxErrors());
        }
    }

    final class FailFastCollector implements ErrorCollector
    {
        private VerificationError firstError;
        private final List<VerificationError> warnings = new ArrayList<>();

        /**
         * Keeps the error only when no error has been recorded yet.
         *
         * @param error the error to record
         */
        @Override
        public void addError(VerificationError error)
        {
            if (firstError == null)
            {
                firstError = error;
            }
        }

        /**
         * Records a warning; warnings are never dropped and never stop verification.
         *
         * @param warning the warning to record
         */
        @Override
        public void addWarning(VerificationError warning)
        {
            warnings.add(warning);
        }

        /**
         * @return the first error as a single-element list, or an empty list
         */
        @Override
        public List<VerificationError> getErrors()
        {
            return firstError != null ? List.of(firstError) : List.of();
        }

        /**
         * @return the recorded warnings, unmodifiable
         */
        @Override
        public List<VerificationError> getWarnings()
        {
            return Collections.unmodifiableList(warnings);
        }

        /**
         * @return true when an error has been recorded
         */
        @Override
        public boolean hasErrors()
        {
            return firstError != null;
        }

        /**
         * @return true when at least one warning has been recorded
         */
        @Override
        public boolean hasWarnings()
        {
            return !warnings.isEmpty();
        }

        /**
         * @return true once an error has been recorded
         */
        @Override
        public boolean shouldStop()
        {
            return firstError != null;
        }

        /**
         * Forgets the first error and every warning.
         */
        @Override
        public void reset()
        {
            firstError = null;
            warnings.clear();
        }
    }

    final class CollectAllCollector implements ErrorCollector
    {
        private final List<VerificationError> errors = new ArrayList<>();
        private final List<VerificationError> warnings = new ArrayList<>();
        private final int maxErrors;

        /**
         * Creates a collector retaining at most the given number of errors.
         *
         * @param maxErrors the error cap
         */
        public CollectAllCollector(int maxErrors)
        {
            this.maxErrors = maxErrors;
        }

        /**
         * Records the error unless the cap has already been reached.
         *
         * @param error the error to record
         */
        @Override
        public void addError(VerificationError error)
        {
            if (errors.size() < maxErrors)
            {
                errors.add(error);
            }
        }

        /**
         * Records a warning; warnings are not capped.
         *
         * @param warning the warning to record
         */
        @Override
        public void addWarning(VerificationError warning)
        {
            warnings.add(warning);
        }

        /**
         * @return the recorded errors, unmodifiable
         */
        @Override
        public List<VerificationError> getErrors()
        {
            return Collections.unmodifiableList(errors);
        }

        /**
         * @return the recorded warnings, unmodifiable
         */
        @Override
        public List<VerificationError> getWarnings()
        {
            return Collections.unmodifiableList(warnings);
        }

        /**
         * @return true when at least one error has been recorded
         */
        @Override
        public boolean hasErrors()
        {
            return !errors.isEmpty();
        }

        /**
         * @return true when at least one warning has been recorded
         */
        @Override
        public boolean hasWarnings()
        {
            return !warnings.isEmpty();
        }

        /**
         * @return true once the error cap has been reached
         */
        @Override
        public boolean shouldStop()
        {
            return errors.size() >= maxErrors;
        }

        /**
         * Clears every recorded error and warning.
         */
        @Override
        public void reset()
        {
            errors.clear();
            warnings.clear();
        }
    }
}
