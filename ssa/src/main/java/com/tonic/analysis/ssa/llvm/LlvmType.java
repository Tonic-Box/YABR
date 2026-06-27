package com.tonic.analysis.ssa.llvm;

/**
 * The closed set of LLVM IR types the computational-subset lowering emits, plus rendering helpers.
 *
 * <p>{@link #PTR} exists so the lattice is complete for the eventual reference/heap model; the v1
 * computational subset never produces it (reference types route to {@link UnsupportedLowering}).
 */
enum LlvmType {
    I1("i1", 1),
    I8("i8", 8),
    I16("i16", 16),
    I32("i32", 32),
    I64("i64", 64),
    FLOAT("float", 0),
    DOUBLE("double", 0),
    VOID("void", 0),
    PTR("ptr", 0);

    private final String mnemonic;
    private final int bitWidth;

    LlvmType(String mnemonic, int bitWidth) {
        this.mnemonic = mnemonic;
        this.bitWidth = bitWidth;
    }

    String render() {
        return mnemonic;
    }

    boolean isInteger() {
        return this == I1 || this == I8 || this == I16 || this == I32 || this == I64;
    }

    boolean isFloatingPoint() {
        return this == FLOAT || this == DOUBLE;
    }

    /** Bit width for integer types (1/8/16/32/64); 0 for non-integers. */
    int bitWidth() {
        return bitWidth;
    }

    @Override
    public String toString() {
        return mnemonic;
    }
}
