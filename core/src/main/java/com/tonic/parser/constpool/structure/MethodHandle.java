package com.tonic.parser.constpool.structure;

import lombok.Getter;

/**
 * Represents a Method Handle in the constant pool.
 */
@Getter
public class MethodHandle {
    private final int referenceKind;
    private final int referenceIndex;

    public MethodHandle(int referenceKind, int referenceIndex) {
        this.referenceKind = referenceKind;
        this.referenceIndex = referenceIndex;
    }

    @Override
    public String toString() {
        return "MethodHandle{" +
                "referenceKind=" + referenceKind +
                ", referenceIndex=" + referenceIndex +
                '}';
    }
}
