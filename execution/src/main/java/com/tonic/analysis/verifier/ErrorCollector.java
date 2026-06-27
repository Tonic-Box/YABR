package com.tonic.analysis.verifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface ErrorCollector {

    void addError(VerificationError error);

    void addWarning(VerificationError warning);

    default void add(VerificationError error) {
        if (error.isError()) {
            addError(error);
        } else {
            addWarning(error);
        }
    }

    List<VerificationError> getErrors();

    List<VerificationError> getWarnings();

    boolean hasErrors();

    boolean hasWarnings();

    boolean shouldStop();

    void reset();

    static ErrorCollector failFast() {
        return new FailFastCollector();
    }

    static ErrorCollector collectAll(int maxErrors) {
        return new CollectAllCollector(maxErrors);
    }

    static ErrorCollector forConfig(VerifierConfig config) {
        if (config.isFailFast()) {
            return failFast();
        } else {
            return collectAll(config.getMaxErrors());
        }
    }

    final class FailFastCollector implements ErrorCollector {
        private VerificationError firstError;
        private final List<VerificationError> warnings = new ArrayList<>();

        @Override
        public void addError(VerificationError error) {
            if (firstError == null) {
                firstError = error;
            }
        }

        @Override
        public void addWarning(VerificationError warning) {
            warnings.add(warning);
        }

        @Override
        public List<VerificationError> getErrors() {
            return firstError != null ? List.of(firstError) : List.of();
        }

        @Override
        public List<VerificationError> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        @Override
        public boolean hasErrors() {
            return firstError != null;
        }

        @Override
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public boolean shouldStop() {
            return firstError != null;
        }

        @Override
        public void reset() {
            firstError = null;
            warnings.clear();
        }
    }

    final class CollectAllCollector implements ErrorCollector {
        private final List<VerificationError> errors = new ArrayList<>();
        private final List<VerificationError> warnings = new ArrayList<>();
        private final int maxErrors;

        public CollectAllCollector(int maxErrors) {
            this.maxErrors = maxErrors;
        }

        @Override
        public void addError(VerificationError error) {
            if (errors.size() < maxErrors) {
                errors.add(error);
            }
        }

        @Override
        public void addWarning(VerificationError warning) {
            warnings.add(warning);
        }

        @Override
        public List<VerificationError> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        @Override
        public List<VerificationError> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        @Override
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        @Override
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public boolean shouldStop() {
            return errors.size() >= maxErrors;
        }

        @Override
        public void reset() {
            errors.clear();
            warnings.clear();
        }
    }
}
