package com.tonic.analysis.ssa.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression test: the cached-instance factories must not canonicalize -0.0 to +0.0,
 * which primitive == comparison silently does.
 */
class NegativeZeroConstantTest {

    @Test
    void doubleNegativeZeroKeepsItsSign() {
        assertSame(DoubleConstant.ZERO, DoubleConstant.of(0.0));
        assertNotSame(DoubleConstant.ZERO, DoubleConstant.of(-0.0));
        assertEquals(Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(DoubleConstant.of(-0.0).getValue()));
    }

    @Test
    void floatNegativeZeroKeepsItsSign() {
        assertSame(FloatConstant.ZERO, FloatConstant.of(0.0f));
        assertNotSame(FloatConstant.ZERO, FloatConstant.of(-0.0f));
        assertEquals(Float.floatToRawIntBits(-0.0f),
                Float.floatToRawIntBits(FloatConstant.of(-0.0f).getValue()));
    }
}
