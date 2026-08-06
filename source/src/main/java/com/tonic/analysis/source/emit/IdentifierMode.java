package com.tonic.analysis.source.emit;

/**
 * Modes for handling non-standard identifiers during source emission.
 */
public enum IdentifierMode
{
    /**
     * Keep identifiers exactly as they appear in bytecode.
     */
    RAW,

    /**
     * Escape non-standard characters to \\uXXXX format.
     */
    UNICODE_ESCAPE,

    /**
     * Rename invalid identifiers to semantic names (method_1, field_2, etc.).
     */
    SEMANTIC_RENAME
}
