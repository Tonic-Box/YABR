package com.tonic.parser.constpool.structure;

import lombok.Getter;

/**
 * Represents an InvokeDynamic in the constant pool.
 */
@Getter
public class InvokeDynamic {
    private final int bootstrapMethodAttrIndex;
    private final int nameAndTypeIndex;

    public InvokeDynamic(int bootstrapMethodAttrIndex, int nameAndTypeIndex) {
        this.bootstrapMethodAttrIndex = bootstrapMethodAttrIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    @Override
    public String toString() {
        return "InvokeDynamic{" +
                "bootstrapMethodAttrIndex=" + bootstrapMethodAttrIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}
