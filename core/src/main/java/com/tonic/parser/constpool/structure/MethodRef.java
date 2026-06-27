package com.tonic.parser.constpool.structure;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Method Reference in the constant pool.
 */
@Getter
@Setter
public class MethodRef {
    private int classIndex;
    private int nameAndTypeIndex;

    public MethodRef(int classIndex, int nameAndTypeIndex) {
        this.classIndex = classIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    @Override
    public String toString() {
        return "MethodRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}