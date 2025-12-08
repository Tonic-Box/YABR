package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents a MethodType constant loaded via ldc.
 * Corresponds to CONSTANT_MethodType in the constant pool.
 *
 * A MethodType represents a method signature (parameter types and return type)
 * and is commonly used with invokedynamic and method handles.
 */
@Getter
public final class MethodTypeConstant extends Constant {

    private final String descriptor;  // Method descriptor, e.g., "(II)V"

    /**
     * Creates a MethodType constant.
     *
     * @param descriptor the method descriptor
     */
    public MethodTypeConstant(String descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public IRType getType() {
        return new ReferenceType("java/lang/invoke/MethodType");
    }

    @Override
    public Object getValue() {
        return descriptor;
    }

    @Override
    public String toString() {
        return "MethodType[" + descriptor + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodTypeConstant that)) return false;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }
}
