package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable slot-to-value view of a frame's locals, taken at one point in execution.
 */
public final class LocalsSnapshot
{

    private final Map<Integer, ValueInfo> values;

    /**
     * Copies every occupied slot into an unmodifiable map of value descriptions.
     * @param locals the live local variable table
     * @throws IllegalArgumentException if the table is null
     */
    public LocalsSnapshot(ConcreteLocals locals)
    {
        if (locals == null)
        {
            throw new IllegalArgumentException("Locals cannot be null");
        }

        Map<Integer, ValueInfo> temp = new LinkedHashMap<>();
        Map<Integer, ConcreteValue> snapshot = locals.snapshot();

        for (Map.Entry<Integer, ConcreteValue> entry : snapshot.entrySet())
        {
            temp.put(entry.getKey(), new ValueInfo(entry.getValue()));
        }

        this.values = Collections.unmodifiableMap(temp);
    }

    /**
     * @return the values
     */
    public Map<Integer, ValueInfo> getValues()
    {
        return values;
    }

    /**
     * @param slot local variable slot index
     * @return the value in a slot, or null if the slot was empty
     */
    public ValueInfo get(int slot)
    {
        return values.get(slot);
    }

    /**
     * @return the number of occupied slots
     */
    public int size()
    {
        return values.size();
    }

    @Override
    public String toString()
    {
        return "LocalsSnapshot{size=" + size() + ", values=" + values + "}";
    }
}
