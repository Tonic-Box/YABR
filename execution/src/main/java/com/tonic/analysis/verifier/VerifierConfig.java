package com.tonic.analysis.verifier;

import lombok.Getter;

@Getter
public final class VerifierConfig {

    public enum ErrorMode {
        FAIL_FAST,
        COLLECT_ALL
    }

    private final ErrorMode errorMode;
    private final boolean verifyStackMapTable;
    private final boolean strictTypeChecking;
    private final boolean verifyControlFlow;
    private final boolean verifyStructure;
    private final int maxErrors;
    private final boolean treatWarningsAsErrors;

    private VerifierConfig(Builder builder) {
        this.errorMode = builder.errorMode;
        this.verifyStackMapTable = builder.verifyStackMapTable;
        this.strictTypeChecking = builder.strictTypeChecking;
        this.verifyControlFlow = builder.verifyControlFlow;
        this.verifyStructure = builder.verifyStructure;
        this.maxErrors = builder.maxErrors;
        this.treatWarningsAsErrors = builder.treatWarningsAsErrors;
    }

    public static VerifierConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isFailFast() {
        return errorMode == ErrorMode.FAIL_FAST;
    }

    public boolean isCollectAll() {
        return errorMode == ErrorMode.COLLECT_ALL;
    }

    public static final class Builder {
        private ErrorMode errorMode = ErrorMode.COLLECT_ALL;
        private boolean verifyStackMapTable = true;
        private boolean strictTypeChecking = true;
        private boolean verifyControlFlow = true;
        private boolean verifyStructure = true;
        private int maxErrors = 100;
        private boolean treatWarningsAsErrors = false;

        public Builder errorMode(ErrorMode errorMode) {
            this.errorMode = errorMode != null ? errorMode : ErrorMode.COLLECT_ALL;
            return this;
        }

        public Builder failFast() {
            this.errorMode = ErrorMode.FAIL_FAST;
            return this;
        }

        public Builder collectAll() {
            this.errorMode = ErrorMode.COLLECT_ALL;
            return this;
        }

        public Builder verifyStackMapTable(boolean verify) {
            this.verifyStackMapTable = verify;
            return this;
        }

        public Builder strictTypeChecking(boolean strict) {
            this.strictTypeChecking = strict;
            return this;
        }

        public Builder verifyControlFlow(boolean verify) {
            this.verifyControlFlow = verify;
            return this;
        }

        public Builder verifyStructure(boolean verify) {
            this.verifyStructure = verify;
            return this;
        }

        public Builder maxErrors(int max) {
            this.maxErrors = Math.max(1, max);
            return this;
        }

        public Builder treatWarningsAsErrors(boolean treat) {
            this.treatWarningsAsErrors = treat;
            return this;
        }

        public VerifierConfig build() {
            return new VerifierConfig(this);
        }
    }

    @Override
    public String toString() {
        return "VerifierConfig{" +
               "errorMode=" + errorMode +
               ", verifyStackMapTable=" + verifyStackMapTable +
               ", strictTypeChecking=" + strictTypeChecking +
               ", verifyControlFlow=" + verifyControlFlow +
               ", verifyStructure=" + verifyStructure +
               ", maxErrors=" + maxErrors +
               ", treatWarningsAsErrors=" + treatWarningsAsErrors +
               '}';
    }
}
