package com.tonic.analysis.absexec;

/**
 * The abstract local-variable table: an array of {@link VarCtx} indexed by slot. Copy-constructed when a
 * {@link Frame} forks at a branch.
 */
public final class Variables
{

    private final VarCtx[] slots;

    /**
     * Creates a table sized for the method's declared max locals.
     * @param maxLocals the method's declared local-slot count
     */
    public Variables(int maxLocals)
    {
        slots = new VarCtx[Math.max(maxLocals, 1)];
    }

    /**
     * Copies another table for a forked frame.
     * @param other the table to copy
     */
    public Variables(Variables other)
    {
        this.slots = other.slots.clone();
    }

    /**
     * Writes a slot, ignoring out-of-range indices.
     * @param index the slot index
     * @param value the context to store
     */
    public void set(int index, VarCtx value)
    {
        if (index >= 0 && index < slots.length)
        {
            slots[index] = value;
        }
    }

    /**
     * Reads a slot, returning null when the index is out of range or the slot is unset.
     * @param index the slot index
     * @return the slot's context, or null
     */
    public VarCtx get(int index)
    {
        return index >= 0 && index < slots.length ? slots[index] : null;
    }

    /**
     * @return the number of local slots
     */
    public int size()
    {
        return slots.length;
    }
}
