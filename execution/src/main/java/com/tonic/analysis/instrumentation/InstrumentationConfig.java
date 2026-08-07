package com.tonic.analysis.instrumentation;

/**
 * Configuration holder for instrumentation settings.
 */
public class InstrumentationConfig
{

    private boolean skipAbstract;
    private boolean skipNative;
    private boolean skipSynthetic;
    private boolean skipConstructors;
    private boolean skipStaticInitializers;
    private boolean skipBridge;
    private boolean verbose;
    private boolean failOnError;

    private InstrumentationConfig(Builder builder)
    {
        this.skipAbstract = builder.skipAbstract;
        this.skipNative = builder.skipNative;
        this.skipSynthetic = builder.skipSynthetic;
        this.skipConstructors = builder.skipConstructors;
        this.skipStaticInitializers = builder.skipStaticInitializers;
        this.skipBridge = builder.skipBridge;
        this.verbose = builder.verbose;
        this.failOnError = builder.failOnError;
    }

    /**
     * @return whether abstract methods are skipped
     */
    public boolean isSkipAbstract()
    {
        return skipAbstract;
    }

    /**
     * Sets whether abstract methods are skipped.
     * @param skipAbstract true to skip abstract methods
     */
    public void setSkipAbstract(boolean skipAbstract)
    {
        this.skipAbstract = skipAbstract;
    }

    /**
     * @return whether native methods are skipped
     */
    public boolean isSkipNative()
    {
        return skipNative;
    }

    /**
     * Sets whether native methods are skipped.
     * @param skipNative true to skip native methods
     */
    public void setSkipNative(boolean skipNative)
    {
        this.skipNative = skipNative;
    }

    /**
     * @return whether synthetic methods are skipped
     */
    public boolean isSkipSynthetic()
    {
        return skipSynthetic;
    }

    /**
     * Sets whether synthetic methods are skipped.
     * @param skipSynthetic true to skip synthetic methods
     */
    public void setSkipSynthetic(boolean skipSynthetic)
    {
        this.skipSynthetic = skipSynthetic;
    }

    /**
     * @return whether constructors are skipped
     */
    public boolean isSkipConstructors()
    {
        return skipConstructors;
    }

    /**
     * Sets whether constructors are skipped.
     * @param skipConstructors true to skip constructors
     */
    public void setSkipConstructors(boolean skipConstructors)
    {
        this.skipConstructors = skipConstructors;
    }

    /**
     * @return whether static initializers are skipped
     */
    public boolean isSkipStaticInitializers()
    {
        return skipStaticInitializers;
    }

    /**
     * Sets whether static initializers are skipped.
     * @param skipStaticInitializers true to skip static initializers
     */
    public void setSkipStaticInitializers(boolean skipStaticInitializers)
    {
        this.skipStaticInitializers = skipStaticInitializers;
    }

    /**
     * @return whether bridge methods are skipped
     */
    public boolean isSkipBridge()
    {
        return skipBridge;
    }

    /**
     * Sets whether bridge methods are skipped.
     * @param skipBridge true to skip bridge methods
     */
    public void setSkipBridge(boolean skipBridge)
    {
        this.skipBridge = skipBridge;
    }

    /**
     * @return whether instrumentation progress is logged
     */
    public boolean isVerbose()
    {
        return verbose;
    }

    /**
     * Sets whether instrumentation progress is logged.
     * @param verbose true to log progress
     */
    public void setVerbose(boolean verbose)
    {
        this.verbose = verbose;
    }

    /**
     * @return whether instrumentation fails on the first error rather than continuing
     */
    public boolean isFailOnError()
    {
        return failOnError;
    }

    /**
     * Sets whether instrumentation fails on the first error rather than continuing.
     * @param failOnError true to fail on the first error
     */
    public void setFailOnError(boolean failOnError)
    {
        this.failOnError = failOnError;
    }

    /**
     * Creates a default configuration.
     * @return a configuration with default settings
     */
    public static InstrumentationConfig defaults()
    {
        return InstrumentationConfig.builder().build();
    }

    /**
     * Creates a configuration that instruments everything.
     * @return a configuration with all skip flags disabled
     */
    public static InstrumentationConfig instrumentAll()
    {
        return InstrumentationConfig.builder()
                .skipAbstract(false)
                .skipNative(false)
                .skipSynthetic(false)
                .skipConstructors(false)
                .skipStaticInitializers(false)
                .skipBridge(false)
                .build();
    }

    /**
     * Creates a new builder.
     * @return a new Builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for InstrumentationConfig instances.
     */
    public static class Builder
    {
        private boolean skipAbstract = true;
        private boolean skipNative = true;
        private boolean skipSynthetic = true;
        private boolean skipConstructors = false;
        private boolean skipStaticInitializers = false;
        private boolean skipBridge = true;
        private boolean verbose = false;
        private boolean failOnError = false;

        /**
         * Sets whether abstract methods are skipped.
         * @param skipAbstract true to skip abstract methods
         * @return this builder
         */
        public Builder skipAbstract(boolean skipAbstract)
        {
            this.skipAbstract = skipAbstract;
            return this;
        }

        /**
         * Sets whether native methods are skipped.
         * @param skipNative true to skip native methods
         * @return this builder
         */
        public Builder skipNative(boolean skipNative)
        {
            this.skipNative = skipNative;
            return this;
        }

        /**
         * Sets whether synthetic methods are skipped.
         * @param skipSynthetic true to skip synthetic methods
         * @return this builder
         */
        public Builder skipSynthetic(boolean skipSynthetic)
        {
            this.skipSynthetic = skipSynthetic;
            return this;
        }

        /**
         * Sets whether constructors are skipped.
         * @param skipConstructors true to skip constructors
         * @return this builder
         */
        public Builder skipConstructors(boolean skipConstructors)
        {
            this.skipConstructors = skipConstructors;
            return this;
        }

        /**
         * Sets whether static initializers are skipped.
         * @param skipStaticInitializers true to skip static initializers
         * @return this builder
         */
        public Builder skipStaticInitializers(boolean skipStaticInitializers)
        {
            this.skipStaticInitializers = skipStaticInitializers;
            return this;
        }

        /**
         * Sets whether bridge methods are skipped.
         * @param skipBridge true to skip bridge methods
         * @return this builder
         */
        public Builder skipBridge(boolean skipBridge)
        {
            this.skipBridge = skipBridge;
            return this;
        }

        /**
         * Sets whether instrumentation progress is logged.
         * @param verbose true to log progress
         * @return this builder
         */
        public Builder verbose(boolean verbose)
        {
            this.verbose = verbose;
            return this;
        }

        /**
         * Sets whether instrumentation fails on the first error rather than continuing.
         * @param failOnError true to fail on the first error
         * @return this builder
         */
        public Builder failOnError(boolean failOnError)
        {
            this.failOnError = failOnError;
            return this;
        }

        /**
         * Builds the configuration.
         * @return the built InstrumentationConfig
         */
        public InstrumentationConfig build()
        {
            return new InstrumentationConfig(this);
        }
    }
}
