package com.tonic.analysis.source.emit;

/**
 * Configuration options for source code emission. Instances are created via {@link #builder()} or one
 * of the presets ({@link #defaults()}, {@link #debug()}, {@link #compact()}).
 */
public class SourceEmitterConfig {

    private final boolean useVarKeyword;
    private final boolean includeIRComments;
    private final boolean includeLineNumbers;
    private final String indentString;
    private final boolean alwaysUseBraces;
    private final boolean blankLinesBetweenMethods;
    private final int maxLineLength;
    private final boolean useFullyQualifiedNames;
    private final IdentifierMode identifierMode;
    private final boolean resolveBootstrapMethods;

    private SourceEmitterConfig(Builder b) {
        this.useVarKeyword = b.useVarKeyword;
        this.includeIRComments = b.includeIRComments;
        this.includeLineNumbers = b.includeLineNumbers;
        this.indentString = b.indentString;
        this.alwaysUseBraces = b.alwaysUseBraces;
        this.blankLinesBetweenMethods = b.blankLinesBetweenMethods;
        this.maxLineLength = b.maxLineLength;
        this.useFullyQualifiedNames = b.useFullyQualifiedNames;
        this.identifierMode = b.identifierMode;
        this.resolveBootstrapMethods = b.resolveBootstrapMethods;
    }

    /** Use 'var' keyword for local variables when possible (Java 10+). */
    public boolean isUseVarKeyword() {
        return useVarKeyword;
    }

    /** Include comments showing IR instructions. */
    public boolean isIncludeIRComments() {
        return includeIRComments;
    }

    /** Include line number comments. */
    public boolean isIncludeLineNumbers() {
        return includeLineNumbers;
    }

    /** Indent string (default tab). */
    public String getIndentString() {
        return indentString;
    }

    /** Use braces for single-statement blocks. */
    public boolean isAlwaysUseBraces() {
        return alwaysUseBraces;
    }

    /** Add blank lines between methods. */
    public boolean isBlankLinesBetweenMethods() {
        return blankLinesBetweenMethods;
    }

    /** Maximum line length before wrapping (0 = no limit). */
    public int getMaxLineLength() {
        return maxLineLength;
    }

    /** Use fully qualified class names. */
    public boolean isUseFullyQualifiedNames() {
        return useFullyQualifiedNames;
    }

    /** How to handle non-standard/obfuscated identifiers. */
    public IdentifierMode getIdentifierMode() {
        return identifierMode;
    }

    /** Whether to resolve bootstrap methods to actual method calls. */
    public boolean isResolveBootstrapMethods() {
        return resolveBootstrapMethods;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean useVarKeyword = false;
        private boolean includeIRComments = false;
        private boolean includeLineNumbers = false;
        private String indentString = "\t";
        private boolean alwaysUseBraces = true;
        private boolean blankLinesBetweenMethods = true;
        private int maxLineLength = 120;
        private boolean useFullyQualifiedNames = false;
        private IdentifierMode identifierMode = IdentifierMode.RAW;
        private boolean resolveBootstrapMethods = false;

        public Builder useVarKeyword(boolean useVarKeyword) {
            this.useVarKeyword = useVarKeyword;
            return this;
        }

        public Builder includeIRComments(boolean includeIRComments) {
            this.includeIRComments = includeIRComments;
            return this;
        }

        public Builder includeLineNumbers(boolean includeLineNumbers) {
            this.includeLineNumbers = includeLineNumbers;
            return this;
        }

        public Builder indentString(String indentString) {
            this.indentString = indentString;
            return this;
        }

        public Builder alwaysUseBraces(boolean alwaysUseBraces) {
            this.alwaysUseBraces = alwaysUseBraces;
            return this;
        }

        public Builder blankLinesBetweenMethods(boolean blankLinesBetweenMethods) {
            this.blankLinesBetweenMethods = blankLinesBetweenMethods;
            return this;
        }

        public Builder maxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        public Builder useFullyQualifiedNames(boolean useFullyQualifiedNames) {
            this.useFullyQualifiedNames = useFullyQualifiedNames;
            return this;
        }

        public Builder identifierMode(IdentifierMode identifierMode) {
            this.identifierMode = identifierMode;
            return this;
        }

        public Builder resolveBootstrapMethods(boolean resolveBootstrapMethods) {
            this.resolveBootstrapMethods = resolveBootstrapMethods;
            return this;
        }

        public SourceEmitterConfig build() {
            return new SourceEmitterConfig(this);
        }
    }

    /**
     * Default configuration.
     */
    public static SourceEmitterConfig defaults() {
        return SourceEmitterConfig.builder().build();
    }

    /**
     * Configuration for debugging output.
     */
    public static SourceEmitterConfig debug() {
        return SourceEmitterConfig.builder()
                .includeIRComments(true)
                .includeLineNumbers(true)
                .build();
    }

    /**
     * Compact configuration.
     */
    public static SourceEmitterConfig compact() {
        return SourceEmitterConfig.builder()
                .indentString("  ")
                .alwaysUseBraces(false)
                .blankLinesBetweenMethods(false)
                .build();
    }
}
