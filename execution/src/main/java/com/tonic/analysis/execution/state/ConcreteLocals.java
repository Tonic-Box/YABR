package com.tonic.analysis.execution.state;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.parser.MethodEntry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Fixed-size local variable table of concrete values, with wide values occupying two slots.
 */
public final class ConcreteLocals
{

    private final ConcreteValue[] locals;
    private final int maxLocals;

    /**
     * Creates an empty table.
     * @param maxLocals number of slots
     */
    public ConcreteLocals(int maxLocals)
    {
        this.maxLocals = maxLocals;
        this.locals = new ConcreteValue[maxLocals];
    }

    /**
     * Stores a value, clearing the following slot for wide values.
     * @param slot target slot
     * @param value value to store
     * @throws IndexOutOfBoundsException if the slot is out of range
     * @throws IllegalArgumentException if a wide value would overflow the table
     */
    public void set(int slot, ConcreteValue value)
    {
        checkSlot(slot);
        locals[slot] = value;

        if (value.isWide())
        {
            if (slot + 1 >= maxLocals)
            {
                throw new IllegalArgumentException("Wide value at slot " + slot + " exceeds max locals " + maxLocals);
            }
            locals[slot + 1] = null;
        }
    }

    /**
     * Stores an int.
     * @param slot target slot
     * @param value value to store
     */
    public void setInt(int slot, int value)
    {
        set(slot, ConcreteValue.intValue(value));
    }

    /**
     * Stores a long.
     * @param slot target slot
     * @param value value to store
     */
    public void setLong(int slot, long value)
    {
        set(slot, ConcreteValue.longValue(value));
    }

    /**
     * Stores a float.
     * @param slot target slot
     * @param value value to store
     */
    public void setFloat(int slot, float value)
    {
        set(slot, ConcreteValue.floatValue(value));
    }

    /**
     * Stores a double.
     * @param slot target slot
     * @param value value to store
     */
    public void setDouble(int slot, double value)
    {
        set(slot, ConcreteValue.doubleValue(value));
    }

    /**
     * Stores a reference.
     * @param slot target slot
     * @param instance instance to store
     */
    public void setReference(int slot, ObjectInstance instance)
    {
        set(slot, ConcreteValue.reference(instance));
    }

    /**
     * Stores a null reference.
     * @param slot target slot
     */
    public void setNull(int slot)
    {
        set(slot, ConcreteValue.nullRef());
    }

    /**
     * Reads a slot.
     * @param slot slot to read
     * @return the stored value
     * @throws IndexOutOfBoundsException if the slot is out of range
     * @throws IllegalStateException if the slot is undefined
     */
    public ConcreteValue get(int slot)
    {
        checkSlot(slot);
        ConcreteValue value = locals[slot];
        if (value == null)
        {
            throw new IllegalStateException("Local variable " + slot + " is not defined");
        }
        return value;
    }

    /**
     * Reads a slot as an int.
     * @param slot slot to read
     * @return the int value
     */
    public int getInt(int slot)
    {
        return get(slot).asInt();
    }

    /**
     * Reads a slot as a long.
     * @param slot slot to read
     * @return the long value
     */
    public long getLong(int slot)
    {
        return get(slot).asLong();
    }

    /**
     * Reads a slot as a float.
     * @param slot slot to read
     * @return the float value
     */
    public float getFloat(int slot)
    {
        return get(slot).asFloat();
    }

    /**
     * Reads a slot as a double.
     * @param slot slot to read
     * @return the double value
     */
    public double getDouble(int slot)
    {
        return get(slot).asDouble();
    }

    /**
     * Reads a slot as a reference.
     * @param slot slot to read
     * @return the instance, or null for a null reference
     */
    public ObjectInstance getReference(int slot)
    {
        return get(slot).asReference();
    }

    /**
     * @return the number of slots
     */
    public int size()
    {
        return maxLocals;
    }

    /**
     * Tests whether a slot holds a value.
     * @param slot slot to test
     * @return true if the slot is in range and defined
     */
    public boolean isDefined(int slot)
    {
        if (slot < 0 || slot >= maxLocals)
        {
            return false;
        }
        return locals[slot] != null;
    }

    /**
     * Captures the defined slots as an unmodifiable map.
     * @return slot-to-value map of defined slots
     */
    public Map<Integer, ConcreteValue> snapshot()
    {
        Map<Integer, ConcreteValue> result = new HashMap<>();
        for (int i = 0; i < maxLocals; i++)
        {
            if (locals[i] != null)
            {
                result.put(i, locals[i]);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Creates a table sized for a method with its arguments preloaded by slot category.
     * @param method the method to size for
     * @param args argument values in order, or null for none
     * @return the initialized locals
     * @throws IllegalArgumentException if the method is null or the arguments overflow the table
     */
    public static ConcreteLocals forMethod(MethodEntry method, ConcreteValue[] args)
    {
        if (method == null)
        {
            throw new IllegalArgumentException("Method cannot be null");
        }

        int maxLocals = 0;
        if (method.getCodeAttribute() != null)
        {
            maxLocals = method.getCodeAttribute().getMaxLocals();
        }
        else
        {
            maxLocals = (args != null ? args.length : 0) + 10;
        }

        ConcreteLocals locals = new ConcreteLocals(maxLocals);

        if (args != null)
        {
            int slot = 0;
            for (ConcreteValue arg : args)
            {
                if (slot >= maxLocals)
                {
                    throw new IllegalArgumentException("Too many arguments for method max locals: " + maxLocals);
                }
                locals.set(slot, arg);
                slot += arg.getCategory();
            }
        }

        return locals;
    }

    private void checkSlot(int slot)
    {
        if (slot < 0 || slot >= maxLocals)
        {
            throw new IndexOutOfBoundsException("Slot " + slot + " out of bounds [0, " + maxLocals + ")");
        }
    }

    @Override
    public String toString()
    {
        return "ConcreteLocals[max=" + maxLocals + ", values=" + snapshot() + "]";
    }
}
