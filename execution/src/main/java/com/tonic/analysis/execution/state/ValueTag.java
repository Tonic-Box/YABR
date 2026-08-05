package com.tonic.analysis.execution.state;

/**
 * Runtime type of an interpreted value, carrying its JVM slot category.
 */
public enum ValueTag
{
    /**
     * A 32-bit signed integer occupying one slot; also carries the narrower
     * boolean, byte, char, and short values, which the JVM computes on as ints.
     */
    INT(1),
    /**
     * A 64-bit signed integer occupying two slots.
     */
    LONG(2),
    /**
     * A 32-bit floating point value occupying one slot.
     */
    FLOAT(1),
    /**
     * A 64-bit floating point value occupying two slots.
     */
    DOUBLE(2),
    /**
     * A non-null pointer to an object or array on the interpreter heap,
     * occupying one slot.
     */
    REFERENCE(1),
    /**
     * The null reference occupying one slot, tagged apart from
     * {@link #REFERENCE} so an absent instance needs no heap object.
     */
    NULL(1),
    /**
     * A jsr return address occupying one slot; produced only by the legacy
     * subroutine opcodes.
     */
    RETURN_ADDRESS(1);

    private final int category;

    ValueTag(int category)
    {
        this.category = category;
    }

    /**
     * @return the category
     */
    public int getCategory()
    {
        return category;
    }

    /**
     * @return true for the category 2 types long and double, which occupy two slots
     */
    public boolean isWide()
    {
        return category == 2;
    }
}
