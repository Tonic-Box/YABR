package com.tonic.parser.constpool.structure;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Field Reference in the constant pool.
 */
@Getter
@Setter
public class FieldRef {
    private int classIndex;
    private int nameAndTypeIndex;

    public FieldRef(int classIndex, int nameAndTypeIndex) {
        this.classIndex = classIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    @Override
    public String toString() {
        return "FieldRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}