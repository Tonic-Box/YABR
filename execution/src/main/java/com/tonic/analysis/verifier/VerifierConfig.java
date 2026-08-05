package com.tonic.analysis.verifier;

/**
 * Immutable settings for the {@link Verifier}: which passes run, the error mode, and the error cap.
 */
public final class VerifierConfig
{

    /**
     * How the verifier reacts to errors: stop at the first or collect up to the cap.
     */
    public enum ErrorMode
    {
        /**
         * Abandon verification at the first error, reporting only that one.
         */
        FAIL_FAST,
        /**
         * Keep verifying after an error, gathering findings until the configured cap is reached.
         */
        COLLECT_ALL
    }

    private final ErrorMode errorMode;
    private final boolean verifyStackMapTable;
    private final boolean strictTypeChecking;
    private final boolean verifyControlFlow;
    private final boolean verifyStructure;
    private final int maxErrors;
    private final boolean treatWarningsAsErrors;

    private VerifierConfig(Builder builder)
    {
        this.errorMode = builder.errorMode;
        this.verifyStackMapTable = builder.verifyStackMapTable;
        this.strictTypeChecking = builder.strictTypeChecking;
        this.verifyControlFlow = builder.verifyControlFlow;
        this.verifyStructure = builder.verifyStructure;
        this.maxErrors = builder.maxErrors;
        this.treatWarningsAsErrors = builder.treatWarningsAsErrors;
    }

    /**
     * @return the error mode
     */
    public ErrorMode getErrorMode()
    {
        return errorMode;
    }

    /**
     * @return whether verify stack map table
     */
    public boolean isVerifyStackMapTable()
    {
        return verifyStackMapTable;
    }

    /**
     * @return whether strict type checking
     */
    public boolean isStrictTypeChecking()
    {
        return strictTypeChecking;
    }

    /**
     * @return whether verify control flow
     */
    public boolean isVerifyControlFlow()
    {
        return verifyControlFlow;
    }

    /**
     * @return whether verify structure
     */
    public boolean isVerifyStructure()
    {
        return verifyStructure;
    }

    /**
     * @return the max errors
     */
    public int getMaxErrors()
    {
        return maxErrors;
    }

    /**
     * @return whether treat warnings as errors
     */
    public boolean isTreatWarningsAsErrors()
    {
        return treatWarningsAsErrors;
    }

    /**
     * Creates the default configuration: all passes on, COLLECT_ALL, up to 100 errors.
     * @return the default configuration
     */
    public static VerifierConfig defaults()
    {
        return new Builder().build();
    }

    /**
     * Creates a builder for a configuration.
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * @return true if the error mode is FAIL_FAST
     */
    public boolean isFailFast()
    {
        return errorMode == ErrorMode.FAIL_FAST;
    }

    /**
     * @return true if the error mode is COLLECT_ALL
     */
    public boolean isCollectAll()
    {
        return errorMode == ErrorMode.COLLECT_ALL;
    }

    /**
     * Builder for a {@link VerifierConfig}; all passes default to enabled with COLLECT_ALL and 100 errors.
     */
    public static final class Builder
    {
        private ErrorMode errorMode = ErrorMode.COLLECT_ALL;
        private boolean verifyStackMapTable = true;
        private boolean strictTypeChecking = true;
        private boolean verifyControlFlow = true;
        private boolean verifyStructure = true;
        private int maxErrors = 100;
        private boolean treatWarningsAsErrors = false;

        /**
         * Sets the error mode, defaulting null to COLLECT_ALL.
         * @param errorMode the mode, or null
         * @return this builder
         */
        public Builder errorMode(ErrorMode errorMode)
        {
            this.errorMode = errorMode != null ? errorMode : ErrorMode.COLLECT_ALL;
            return this;
        }

        /**
         * Selects FAIL_FAST error mode.
         * @return this builder
         */
        public Builder failFast()
        {
            this.errorMode = ErrorMode.FAIL_FAST;
            return this;
        }

        /**
         * Selects COLLECT_ALL error mode.
         * @return this builder
         */
        public Builder collectAll()
        {
            this.errorMode = ErrorMode.COLLECT_ALL;
            return this;
        }

        /**
         * Enables or disables the StackMapTable comparison pass.
         * @param verify true to run the pass
         * @return this builder
         */
        public Builder verifyStackMapTable(boolean verify)
        {
            this.verifyStackMapTable = verify;
            return this;
        }

        /**
         * Enables or disables the type-checking pass.
         * @param strict true to run the pass
         * @return this builder
         */
        public Builder strictTypeChecking(boolean strict)
        {
            this.strictTypeChecking = strict;
            return this;
        }

        /**
         * Enables or disables the control-flow and exception-table pass.
         * @param verify true to run the pass
         * @return this builder
         */
        public Builder verifyControlFlow(boolean verify)
        {
            this.verifyControlFlow = verify;
            return this;
        }

        /**
         * Enables or disables the structural (opcode/operand) pass.
         * @param verify true to run the pass
         * @return this builder
         */
        public Builder verifyStructure(boolean verify)
        {
            this.verifyStructure = verify;
            return this;
        }

        /**
         * Caps how many errors are collected, clamped to at least 1.
         * @param max the error cap
         * @return this builder
         */
        public Builder maxErrors(int max)
        {
            this.maxErrors = Math.max(1, max);
            return this;
        }

        /**
         * Controls whether warnings should be treated as verification errors.
         * @param treat true to escalate warnings
         * @return this builder
         */
        public Builder treatWarningsAsErrors(boolean treat)
        {
            this.treatWarningsAsErrors = treat;
            return this;
        }

        /**
         * Builds the immutable configuration.
         * @return the configuration
         */
        public VerifierConfig build()
        {
            return new VerifierConfig(this);
        }
    }

    @Override
    public String toString()
    {
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
