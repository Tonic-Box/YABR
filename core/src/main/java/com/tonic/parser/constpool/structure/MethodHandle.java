package com.tonic.parser.constpool.structure;

/**
 * Represents a Method Handle in the constant pool.
 */
public class MethodHandle {
    private final int referenceKind;
    private final int referenceIndex;

    public MethodHandle(int referenceKind, int referenceIndex) {
        this.referenceKind = referenceKind;
        this.referenceIndex = referenceIndex;
    }

    public int getReferenceKind() {
        return referenceKind;
    }

    public int getReferenceIndex() {
        return referenceIndex;
    }

    @Override
    public String toString() {
        return "MethodHandle{" +
                "referenceKind=" + referenceKind +
                ", referenceIndex=" + referenceIndex +
                '}';
    }
}
