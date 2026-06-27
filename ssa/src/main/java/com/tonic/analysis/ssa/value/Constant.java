package com.tonic.analysis.ssa.value;

/**
 * Base class for all constant values.
 */
public abstract class Constant implements Value {

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
