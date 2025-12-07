package com.tonic.analysis.ssa.ir;

/**
 * Comparison operations for branches.
 */
public enum CompareOp {
    EQ,
    NE,
    LT,
    GE,
    GT,
    LE,
    IFEQ,
    IFNE,
    IFLT,
    IFGE,
    IFGT,
    IFLE,
    IFNULL,
    IFNONNULL,
    ACMPEQ,
    ACMPNE
}
