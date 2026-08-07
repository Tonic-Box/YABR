package com.tonic.analysis.ssa.type;

/**
 * Represents JVM primitive types.
 */
public enum PrimitiveType implements IRType
{
    /**
     * Truth value, descriptor {@code Z}, treated as integral since the JVM carries it as an int.
     */
    BOOLEAN("Z", 1),
    /**
     * 8-bit signed integer, descriptor {@code B}, integral and one slot wide.
     */
    BYTE("B", 1),
    /**
     * 16-bit unsigned integer, descriptor {@code C}, counted as integral here.
     */
    CHAR("C", 1),
    /**
     * 16-bit signed integer, descriptor {@code S}, integral and one slot wide.
     */
    SHORT("S", 1),
    /**
     * 32-bit signed integer, descriptor {@code I}, and the width the narrower integral types
     * compute in.
     */
    INT("I", 1),
    /**
     * 64-bit signed integer, descriptor {@code J}, occupying two local or stack slots.
     */
    LONG("J", 2),
    /**
     * 32-bit floating point, descriptor {@code F}, occupying one slot.
     */
    FLOAT("F", 1),
    /**
     * 64-bit floating point, descriptor {@code D}, occupying two local or stack slots.
     */
    DOUBLE("D", 2);

    private final String descriptor;
    private final int size;

    PrimitiveType(String descriptor, int size)
    {
        this.descriptor = descriptor;
        this.size = size;
    }

    @Override
    public String getDescriptor()
    {
        return descriptor;
    }

    @Override
    public int getSize()
    {
        return size;
    }

    @Override
    public boolean isReference()
    {
        return false;
    }

    @Override
    public boolean isPrimitive()
    {
        return true;
    }

    @Override
    public boolean isVoid()
    {
        return false;
    }

    @Override
    public boolean isArray()
    {
        return false;
    }

    @Override
    public boolean isTwoSlot()
    {
        return this == LONG || this == DOUBLE;
    }

    /**
     * Checks if this is an integral type.
     * @return true if boolean, byte, char, short, or int
     */
    public boolean isIntegral()
    {
        return this == BOOLEAN || this == BYTE || this == CHAR || this == SHORT || this == INT;
    }

    /**
     * Checks if this is a floating-point type.
     * @return true if float or double
     */
    public boolean isFloatingPoint()
    {
        return this == FLOAT || this == DOUBLE;
    }
}
