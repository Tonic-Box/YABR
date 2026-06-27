package com.tonic.analysis.ssa.ir;

/**
 * Unary operation types.
 */
public enum UnaryOp {
    /** Numeric negation. */
    NEG,
    /** Convert int to long. */
    I2L,
    /** Convert int to float. */
    I2F,
    /** Convert int to double. */
    I2D,
    /** Convert long to int. */
    L2I,
    /** Convert long to float. */
    L2F,
    /** Convert long to double. */
    L2D,
    /** Convert float to int. */
    F2I,
    /** Convert float to long. */
    F2L,
    /** Convert float to double. */
    F2D,
    /** Convert double to int. */
    D2I,
    /** Convert double to long. */
    D2L,
    /** Convert double to float. */
    D2F,
    /** Convert int to byte. */
    I2B,
    /** Convert int to char. */
    I2C,
    /** Convert int to short. */
    I2S
}
