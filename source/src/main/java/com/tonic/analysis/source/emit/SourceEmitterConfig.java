package com.tonic.analysis.source.emit;

/**
 * Configuration options for source code emission.
 */
public class SourceEmitterConfig
{

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

    private SourceEmitterConfig(Builder b)
    {
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

    /**
     * @return true if local declarations use var instead of the declared type
     */
    public boolean isUseVarKeyword()
    {
        return useVarKeyword;
    }

    /**
     * @return true if IR instructions are emitted as comments
     */
    public boolean isIncludeIRComments()
    {
        return includeIRComments;
    }

    /**
     * @return true if original line numbers are emitted as comments
     */
    public boolean isIncludeLineNumbers()
    {
        return includeLineNumbers;
    }

    /**
     * @return the string used for one indent level
     */
    public String getIndentString()
    {
        return indentString;
    }

    /**
     * @return true if single-statement bodies are still braced
     */
    public boolean isAlwaysUseBraces()
    {
        return alwaysUseBraces;
    }

    /**
     * @return true if a blank line separates methods
     */
    public boolean isBlankLinesBetweenMethods()
    {
        return blankLinesBetweenMethods;
    }

    /**
     * @return the line length the emitter wraps at, 0 for no limit
     */
    public int getMaxLineLength()
    {
        return maxLineLength;
    }

    /**
     * @return true if type names are emitted fully qualified instead of imported
     */
    public boolean isUseFullyQualifiedNames()
    {
        return useFullyQualifiedNames;
    }

    /**
     * @return how non-standard or obfuscated identifiers are emitted
     */
    public IdentifierMode getIdentifierMode()
    {
        return identifierMode;
    }

    /**
     * @return true if invokedynamic sites are resolved back to the methods they call
     */
    public boolean isResolveBootstrapMethods()
    {
        return resolveBootstrapMethods;
    }

    /**
     * @return a new builder preloaded with the default emitter settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Fluent builder for emitter settings; every setting has a default.
     */
    public static final class Builder
    {
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

        /**
         * Sets whether local declarations use var instead of the declared type; defaults to false.
         *
         * @param useVarKeyword true to emit var
         * @return this builder
         */
        public Builder useVarKeyword(boolean useVarKeyword)
        {
            this.useVarKeyword = useVarKeyword;
            return this;
        }

        /**
         * Sets whether IR detail is emitted as comments; defaults to false.
         *
         * @param includeIRComments true to emit IR comments
         * @return this builder
         */
        public Builder includeIRComments(boolean includeIRComments)
        {
            this.includeIRComments = includeIRComments;
            return this;
        }

        /**
         * Sets whether original line numbers are emitted; defaults to false.
         *
         * @param includeLineNumbers true to emit line numbers
         * @return this builder
         */
        public Builder includeLineNumbers(boolean includeLineNumbers)
        {
            this.includeLineNumbers = includeLineNumbers;
            return this;
        }

        /**
         * Sets the string used for one indent level; defaults to a tab.
         *
         * @param indentString the indent string
         * @return this builder
         */
        public Builder indentString(String indentString)
        {
            this.indentString = indentString;
            return this;
        }

        /**
         * Sets whether single-statement bodies still get braces; defaults to true.
         *
         * @param alwaysUseBraces true to always brace bodies
         * @return this builder
         */
        public Builder alwaysUseBraces(boolean alwaysUseBraces)
        {
            this.alwaysUseBraces = alwaysUseBraces;
            return this;
        }

        /**
         * Sets whether a blank line separates methods; defaults to true.
         *
         * @param blankLinesBetweenMethods true to separate methods with a blank line
         * @return this builder
         */
        public Builder blankLinesBetweenMethods(boolean blankLinesBetweenMethods)
        {
            this.blankLinesBetweenMethods = blankLinesBetweenMethods;
            return this;
        }

        /**
         * Sets the line length the emitter wraps at; defaults to 120.
         *
         * @param maxLineLength the maximum line length
         * @return this builder
         */
        public Builder maxLineLength(int maxLineLength)
        {
            this.maxLineLength = maxLineLength;
            return this;
        }

        /**
         * Sets whether type names are emitted fully qualified instead of imported; defaults to false.
         *
         * @param useFullyQualifiedNames true to emit qualified names
         * @return this builder
         */
        public Builder useFullyQualifiedNames(boolean useFullyQualifiedNames)
        {
            this.useFullyQualifiedNames = useFullyQualifiedNames;
            return this;
        }

        /**
         * Sets how non-standard or obfuscated identifiers are emitted; defaults to RAW.
         *
         * @param identifierMode the identifier mode
         * @return this builder
         */
        public Builder identifierMode(IdentifierMode identifierMode)
        {
            this.identifierMode = identifierMode;
            return this;
        }

        /**
         * Sets whether invokedynamic sites are resolved back to the methods they call; defaults to false.
         *
         * @param resolveBootstrapMethods true to resolve bootstrap methods
         * @return this builder
         */
        public Builder resolveBootstrapMethods(boolean resolveBootstrapMethods)
        {
            this.resolveBootstrapMethods = resolveBootstrapMethods;
            return this;
        }

        /**
         * @return an immutable config holding the configured settings
         */
        public SourceEmitterConfig build()
        {
            return new SourceEmitterConfig(this);
        }
    }

    /**
     * @return a config with every builder setting left at its default
     */
    public static SourceEmitterConfig defaults()
    {
        return SourceEmitterConfig.builder().build();
    }

    /**
     * @return a config that emits IR comments and line numbers
     */
    public static SourceEmitterConfig debug()
    {
        return SourceEmitterConfig.builder()
                .includeIRComments(true)
                .includeLineNumbers(true)
                .build();
    }

    /**
     * @return a config with two-space indent, no optional braces and no blank line between methods
     */
    public static SourceEmitterConfig compact()
    {
        return SourceEmitterConfig.builder()
                .indentString("  ")
                .alwaysUseBraces(false)
                .blankLinesBetweenMethods(false)
                .build();
    }
}
