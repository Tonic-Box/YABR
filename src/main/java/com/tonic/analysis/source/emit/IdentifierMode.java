package com.tonic.analysis.source.emit;

/**
 * Modes for handling non-standard identifiers during source emission.
 */
public enum IdentifierMode {
    /**
     * Keep identifiers exactly as they appear in bytecode.
     * May produce invalid Java source for obfuscated names.
     */
    RAW,

    /**
     * Escape non-standard characters to \\uXXXX format.
     * Produces valid Java identifiers while preserving original information.
     */
    UNICODE_ESCAPE,

    /**
     * Rename invalid identifiers to semantic names (method_1, field_2, etc.).
     * Produces clean, readable output but loses original name information.
     */
    SEMANTIC_RENAME
}
