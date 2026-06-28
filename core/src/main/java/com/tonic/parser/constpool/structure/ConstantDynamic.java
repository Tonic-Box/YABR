package com.tonic.parser.constpool.structure;

/**
 * Represents a CONSTANT_Dynamic entry structure in the constant pool.
 * Used by ldc/ldc_w/ldc2_w to load dynamically computed constants (Java 11+).
 *
 * Structure is identical to InvokeDynamic but used in different context.
 */
public class ConstantDynamic {
    private final int bootstrapMethodAttrIndex;
    private final int nameAndTypeIndex;

    public ConstantDynamic(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        this.bootstrapMethodAttrIndex = bootstrapMethodAttrIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    public int getBootstrapMethodAttrIndex() {
        return bootstrapMethodAttrIndex;
    }

    public int getNameAndTypeIndex() {
        return nameAndTypeIndex;
    }

    @Override
    public String toString() {
        return "ConstantDynamic{" +
                "bootstrapMethodAttrIndex=" + bootstrapMethodAttrIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}
