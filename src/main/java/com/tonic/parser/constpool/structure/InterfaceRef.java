package com.tonic.parser.constpool.structure;

import lombok.Getter;

/**
 * Represents an Interface Method Reference in the constant pool.
 */
@Getter
public class InterfaceRef {
    private final int classIndex;
    private final int nameAndTypeIndex;

    public InterfaceRef(int classIndex, int nameAndTypeIndex) {
        this.classIndex = classIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    @Override
    public String toString() {
        return "InterfaceRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}