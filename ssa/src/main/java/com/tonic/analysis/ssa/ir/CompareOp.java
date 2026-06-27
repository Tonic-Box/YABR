package com.tonic.analysis.ssa.ir;

/**
 * Comparison operations for branches.
 */
public enum CompareOp {
    /** Equal. */
    EQ,
    /** Not equal. */
    NE,
    /** Less than. */
    LT,
    /** Greater or equal. */
    GE,
    /** Greater than. */
    GT,
    /** Less or equal. */
    LE,
    /** If equal to zero. */
    IFEQ,
    /** If not equal to zero. */
    IFNE,
    /** If less than zero. */
    IFLT,
    /** If greater or equal to zero. */
    IFGE,
    /** If greater than zero. */
    IFGT,
    /** If less or equal to zero. */
    IFLE,
    /** If reference is null. */
    IFNULL,
    /** If reference is not null. */
    IFNONNULL,
    /** If references are equal. */
    ACMPEQ,
    /** If references are not equal. */
    ACMPNE
}
