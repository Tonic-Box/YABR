package com.tonic.analysis.source.recovery;

/**
 * Strategy for recovering variable names.
 */
public enum NameRecoveryStrategy {
    /** Use LocalVariableTable when available, synthetic fallback */
    PREFER_DEBUG_INFO,

    /** Always use synthetic names */
    ALWAYS_SYNTHETIC,

    /** Debug info for parameters only, synthetic for locals */
    PARAMETERS_ONLY
}
