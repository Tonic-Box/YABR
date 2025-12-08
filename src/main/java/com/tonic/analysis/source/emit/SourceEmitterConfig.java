package com.tonic.analysis.source.emit;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration options for source code emission.
 */
@Getter
@Builder
public class SourceEmitterConfig {

    /** Use 'var' keyword for local variables when possible (Java 10+) */
    @Builder.Default
    private boolean useVarKeyword = false;

    /** Include comments showing IR instructions */
    @Builder.Default
    private boolean includeIRComments = false;

    /** Include line number comments */
    @Builder.Default
    private boolean includeLineNumbers = false;

    /** Indent string (default 4 spaces) */
    @Builder.Default
    private String indentString = "    ";

    /** Use braces for single-statement blocks */
    @Builder.Default
    private boolean alwaysUseBraces = true;

    /** Add blank lines between methods */
    @Builder.Default
    private boolean blankLinesBetweenMethods = true;

    /** Maximum line length before wrapping (0 = no limit) */
    @Builder.Default
    private int maxLineLength = 120;

    /** Use fully qualified class names */
    @Builder.Default
    private boolean useFullyQualifiedNames = false;

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
