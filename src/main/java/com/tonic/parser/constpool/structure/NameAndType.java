package com.tonic.parser.constpool.structure;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Name and Type in the constant pool.
 */
@Getter
@Setter
public class NameAndType {
    private int nameIndex;
    private int descriptorIndex;

    public NameAndType(int nameIndex, int descriptorIndex) {
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
    }

    @Override
    public String toString() {
        return "NameAndType{" +
                "nameIndex=" + nameIndex +
                ", descriptorIndex=" + descriptorIndex +
                '}';
    }
}