package com.tonic.analysis.ssa.type;

/**
 * Represents the void type (for method returns).
 */
public final class VoidType implements IRType {

    public static final VoidType INSTANCE = new VoidType();

    private VoidType() {}

    @Override
    public String getDescriptor() {
        return "V";
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isVoid() {
        return true;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isTwoSlot() {
        return false;
    }

    @Override
    public String toString() {
        return "void";
    }
}
