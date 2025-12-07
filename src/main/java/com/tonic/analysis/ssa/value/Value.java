package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;

/**
 * Base interface for all values in SSA form.
 * A value represents any data that can be used as an operand.
 */
public interface Value {

    /**
     * Gets the IR type of this value.
     *
     * @return the IR type
     */
    IRType getType();

    /**
     * Checks if this value is a constant.
     *
     * @return true if constant, false otherwise
     */
    boolean isConstant();

    /**
     * Checks if this value is an SSA value.
     *
     * @return true if this is an SSAValue instance, false otherwise
     */
    default boolean isSSAValue() {
        return this instanceof SSAValue;
    }

    /**
     * Checks if this value is null.
     *
     * @return true if this is a NullConstant, false otherwise
     */
    default boolean isNull() {
        return this instanceof NullConstant;
    }
}
