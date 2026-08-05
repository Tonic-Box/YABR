package com.tonic.analysis.ssa.llvm;

/**
 * The closed set of LLVM IR types the computational-subset lowering emits, plus rendering helpers.
 *
 *{@link #PTR} exists so the lattice is complete for the eventual reference/heap model; the v1
 * computational subset never produces it (reference types route to {@link UnsupportedLowering}).
 */
enum LlvmType
{
    /**
     * A single bit, the type of comparison and predicate results.
     */
    I1("i1", 1),
    /**
     * An 8-bit integer, the narrowing target of a byte conversion.
     */
    I8("i8", 8),
    /**
     * A 16-bit integer, the narrowing target of a char or short conversion.
     */
    I16("i16", 16),
    /**
     * A 32-bit integer, the workhorse type for int values and array indices.
     */
    I32("i32", 32),
    /**
     * A 64-bit integer, used for long values.
     */
    I64("i64", 64),
    /**
     * A 32-bit IEEE 754 float; reports a bit width of 0 because widths are
     * tracked for integers only.
     */
    FLOAT("float", 0),
    /**
     * A 64-bit IEEE 754 double; reports a bit width of 0 because widths are
     * tracked for integers only.
     */
    DOUBLE("double", 0),
    /**
     * The absence of a value, used as the return type of a function that
     * yields nothing.
     */
    VOID("void", 0),
    /**
     * An opaque pointer, used for objects and arrays across the runtime ABI.
     */
    PTR("ptr", 0);

    private final String mnemonic;
    private final int bitWidth;

    LlvmType(String mnemonic, int bitWidth)
    {
        this.mnemonic = mnemonic;
        this.bitWidth = bitWidth;
    }

    String render()
    {
        return mnemonic;
    }

    boolean isInteger()
    {
        return this == I1 || this == I8 || this == I16 || this == I32 || this == I64;
    }

    boolean isFloatingPoint()
    {
        return this == FLOAT || this == DOUBLE;
    }

    /**
     * Bit width for integer types (1/8/16/32/64); 0 for non-integers.
     */
    int bitWidth()
    {
        return bitWidth;
    }

    @Override
    public String toString()
    {
        return mnemonic;
    }
}
