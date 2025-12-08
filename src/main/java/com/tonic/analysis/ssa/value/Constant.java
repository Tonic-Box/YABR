package com.tonic.analysis.ssa.value;

/**
 * Base class for all constant values.
 */
public abstract sealed class Constant implements Value
        permits IntConstant, LongConstant, FloatConstant, DoubleConstant,
                StringConstant, NullConstant, ClassConstant,
                MethodHandleConstant, MethodTypeConstant, DynamicConstant {

    @Override
    public boolean isConstant() {
        return true;
    }

    /**
     * Gets the value of this constant.
     *
     * @return the constant value
     */
    public abstract Object getValue();
}
