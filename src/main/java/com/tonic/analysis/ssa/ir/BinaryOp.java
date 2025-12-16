package com.tonic.analysis.ssa.ir;

/**
 * Binary operation types.
 */
public enum BinaryOp {
    /** Addition. */
    ADD,
    /** Subtraction. */
    SUB,
    /** Multiplication. */
    MUL,
    /** Division. */
    DIV,
    /** Remainder (modulo). */
    REM,
    /** Shift left. */
    SHL,
    /** Arithmetic shift right. */
    SHR,
    /** Logical shift right (unsigned). */
    USHR,
    /** Bitwise AND. */
    AND,
    /** Bitwise OR. */
    OR,
    /** Bitwise XOR. */
    XOR,
    /** Long comparison. */
    LCMP,
    /** Float comparison (less on NaN). */
    FCMPL,
    /** Float comparison (greater on NaN). */
    FCMPG,
    /** Double comparison (less on NaN). */
    DCMPL,
    /** Double comparison (greater on NaN). */
    DCMPG
}
