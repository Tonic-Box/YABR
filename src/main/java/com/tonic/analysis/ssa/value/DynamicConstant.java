package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents a dynamic constant loaded via ldc (condy).
 * Corresponds to CONSTANT_Dynamic in the constant pool (Java 11+).
 *
 * Dynamic constants are computed at runtime using a bootstrap method,
 * similar to invokedynamic but for constants rather than method calls.
 */
@Getter
public final class DynamicConstant extends Constant {

    private final String name;              // Name of the constant
    private final String descriptor;        // Type descriptor of the constant
    private final int bootstrapMethodIndex; // Index into BootstrapMethods attribute
    private final int originalCpIndex;      // Original constant pool index for roundtrip

    /**
     * Creates a dynamic constant.
     *
     * @param name the name of the constant
     * @param descriptor the type descriptor
     * @param bootstrapMethodIndex the index into the BootstrapMethods attribute
     * @param originalCpIndex the original constant pool index
     */
    public DynamicConstant(String name, String descriptor, int bootstrapMethodIndex, int originalCpIndex) {
        this.name = name;
        this.descriptor = descriptor;
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.originalCpIndex = originalCpIndex;
    }

    @Override
    public IRType getType() {
        return IRType.fromDescriptor(descriptor);
    }

    @Override
    public Object getValue() {
        return this; // Return self for complex constants
    }

    @Override
    public String toString() {
        return "Dynamic[" + name + ":" + descriptor + " @bsm" + bootstrapMethodIndex + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DynamicConstant that)) return false;
        return bootstrapMethodIndex == that.bootstrapMethodIndex &&
                Objects.equals(name, that.name) &&
                Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, descriptor, bootstrapMethodIndex);
    }
}
