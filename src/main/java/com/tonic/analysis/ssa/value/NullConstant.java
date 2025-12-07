package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

/**
 * Represents the null constant.
 */
public final class NullConstant extends Constant {

    public static final NullConstant INSTANCE = new NullConstant();

    private NullConstant() {}

    @Override
    public IRType getType() {
        return ReferenceType.OBJECT;
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public String toString() {
        return "null";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NullConstant;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
