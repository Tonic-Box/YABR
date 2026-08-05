package com.tonic.analysis.ssa.ir;

/**
 * Whether a {@link TypeCheckInstruction} is a checkcast or an instanceof test.
 */
public enum TypeCheckOp
{
    /**
     * A checked cast that passes the value through under the target type and
     * throws {@code ClassCastException} when it does not fit.
     */
    CAST,
    /**
     * A non-throwing type test that produces an int, 1 when the value is an
     * instance of the target type and 0 otherwise; null tests as 0.
     */
    INSTANCEOF
}
