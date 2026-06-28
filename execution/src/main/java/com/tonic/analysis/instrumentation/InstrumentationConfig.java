package com.tonic.analysis.instrumentation;

/**
 * Configuration holder for instrumentation settings.
 */
public class InstrumentationConfig {

    private boolean skipAbstract;
    private boolean skipNative;
    private boolean skipSynthetic;
    private boolean skipConstructors;
    private boolean skipStaticInitializers;
    private boolean skipBridge;
    private boolean verbose;
    private boolean failOnError;

    private InstrumentationConfig(Builder builder) {
        this.skipAbstract = builder.skipAbstract;
        this.skipNative = builder.skipNative;
        this.skipSynthetic = builder.skipSynthetic;
        this.skipConstructors = builder.skipConstructors;
        this.skipStaticInitializers = builder.skipStaticInitializers;
        this.skipBridge = builder.skipBridge;
        this.verbose = builder.verbose;
        this.failOnError = builder.failOnError;
    }

    /** Returns whether abstract methods are skipped. */
    public boolean isSkipAbstract() {
        return skipAbstract;
    }

    public void setSkipAbstract(boolean skipAbstract) {
        this.skipAbstract = skipAbstract;
    }

    /** Returns whether native methods are skipped. */
    public boolean isSkipNative() {
        return skipNative;
    }

    public void setSkipNative(boolean skipNative) {
        this.skipNative = skipNative;
    }

    /** Returns whether synthetic methods are skipped. */
    public boolean isSkipSynthetic() {
        return skipSynthetic;
    }

    public void setSkipSynthetic(boolean skipSynthetic) {
        this.skipSynthetic = skipSynthetic;
    }

    /** Returns whether constructors are skipped. */
    public boolean isSkipConstructors() {
        return skipConstructors;
    }

    public void setSkipConstructors(boolean skipConstructors) {
        this.skipConstructors = skipConstructors;
    }

    /** Returns whether static initializers are skipped. */
    public boolean isSkipStaticInitializers() {
        return skipStaticInitializers;
    }

    public void setSkipStaticInitializers(boolean skipStaticInitializers) {
        this.skipStaticInitializers = skipStaticInitializers;
    }

    /** Returns whether bridge methods are skipped. */
    public boolean isSkipBridge() {
        return skipBridge;
    }

    public void setSkipBridge(boolean skipBridge) {
        this.skipBridge = skipBridge;
    }

    /** Returns whether instrumentation progress is logged. */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /** Returns whether instrumentation fails on the first error rather than continuing. */
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    /**
     * Creates a default configuration.
     */
    public static InstrumentationConfig defaults() {
        return InstrumentationConfig.builder().build();
    }

    /**
     * Creates a configuration that instruments everything.
     */
    public static InstrumentationConfig instrumentAll() {
        return InstrumentationConfig.builder()
                .skipAbstract(false)
                .skipNative(false)
                .skipSynthetic(false)
                .skipConstructors(false)
                .skipStaticInitializers(false)
                .skipBridge(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean skipAbstract = true;
        private boolean skipNative = true;
        private boolean skipSynthetic = true;
        private boolean skipConstructors = false;
        private boolean skipStaticInitializers = false;
        private boolean skipBridge = true;
        private boolean verbose = false;
        private boolean failOnError = false;

        public Builder skipAbstract(boolean skipAbstract) {
            this.skipAbstract = skipAbstract;
            return this;
        }

        public Builder skipNative(boolean skipNative) {
            this.skipNative = skipNative;
            return this;
        }

        public Builder skipSynthetic(boolean skipSynthetic) {
            this.skipSynthetic = skipSynthetic;
            return this;
        }

        public Builder skipConstructors(boolean skipConstructors) {
            this.skipConstructors = skipConstructors;
            return this;
        }

        public Builder skipStaticInitializers(boolean skipStaticInitializers) {
            this.skipStaticInitializers = skipStaticInitializers;
            return this;
        }

        public Builder skipBridge(boolean skipBridge) {
            this.skipBridge = skipBridge;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder failOnError(boolean failOnError) {
            this.failOnError = failOnError;
            return this;
        }

        public InstrumentationConfig build() {
            return new InstrumentationConfig(this);
        }
    }
}
