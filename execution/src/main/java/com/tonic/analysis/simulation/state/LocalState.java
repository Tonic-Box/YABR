package com.tonic.analysis.simulation.state;

import java.util.*;

/**
 * Immutable representation of local variable slots during simulation.
 * All operations return new LocalState instances.
 */
public final class LocalState
{

    private final Map<Integer, SimValue> locals;
    private final int maxLocal;

    private LocalState(Map<Integer, SimValue> locals, int maxLocal)
    {
        this.locals = Collections.unmodifiableMap(new HashMap<>(locals));
        this.maxLocal = maxLocal;
    }

    /**
     * Creates a state with no slots written.
     *
     * @return the empty state
     */
    public static LocalState empty()
    {
        return new LocalState(Collections.emptyMap(), 0);
    }

    /**
     * Creates a state from an explicit slot map, taking the highest key as the maximum slot.
     *
     * @param values the initial slot values
     * @return the populated state
     */
    public static LocalState of(Map<Integer, SimValue> values)
    {
        int max = values.keySet().stream().mapToInt(i -> i).max().orElse(0);
        return new LocalState(values, max);
    }

    /**
     * Lays parameter values out from slot 0, giving each wide value two slots.
     *
     * @param paramValues the parameter values, including 'this' for instance methods
     * @return the initialized state
     */
    public static LocalState forParameters(List<SimValue> paramValues)
    {
        Map<Integer, SimValue> locals = new HashMap<>();
        int slot = 0;
        for (SimValue value : paramValues)
        {
            locals.put(slot, value);
            slot++;
            if (value.isWide())
            {
                locals.put(slot, SimValue.wideSecondSlot());
                slot++;
            }
        }
        return new LocalState(locals, slot > 0 ? slot - 1 : 0);
    }

    /**
     * Writes a single slot.
     *
     * @param index the slot index to write
     * @param value the value to store
     * @return a new state with that slot written
     */
    public LocalState set(int index, SimValue value)
    {
        Map<Integer, SimValue> newLocals = new HashMap<>(locals);
        newLocals.put(index, value);
        return new LocalState(newLocals, Math.max(maxLocal, index));
    }

    /**
     * Writes a long or double, marking the following slot as its upper half.
     *
     * @param index the slot index to write
     * @param value the wide value to store
     * @return a new state with both slots written
     */
    public LocalState setWide(int index, SimValue value)
    {
        Map<Integer, SimValue> newLocals = new HashMap<>(locals);
        newLocals.put(index, value);
        newLocals.put(index + 1, SimValue.wideSecondSlot());
        return new LocalState(newLocals, Math.max(maxLocal, index + 1));
    }

    /**
     * Reads a slot.
     *
     * @param index the slot index
     * @return the value at that slot, or an unknown value if unset
     */
    public SimValue get(int index)
    {
        SimValue value = locals.get(index);
        if (value == null)
        {
            return SimValue.unknown(null);
        }
        return value;
    }

    /**
     * Reads a slot that must not be the upper half of a wide value.
     *
     * @param index the slot index
     * @return the value at that slot, or an unknown value if unset
     * @throws IllegalStateException if the slot holds a wide second slot marker
     */
    public SimValue getValue(int index)
    {
        SimValue value = get(index);
        if (value.isWideSecondSlot())
        {
            // This shouldn't happen in well-formed bytecode
            throw new IllegalStateException("Attempted to read wide second slot at index " + index);
        }
        return value;
    }

    /**
     * Tests whether a slot holds a value.
     *
     * @param index the slot index
     * @return true if a value is recorded at that slot
     */
    public boolean isDefined(int index)
    {
        return locals.containsKey(index);
    }

    /**
     * @return the highest slot index this state has ever written
     */
    public int maxLocal()
    {
        return maxLocal;
    }

    /**
     * @return the number of slots holding a value
     */
    public int size()
    {
        return locals.size();
    }

    /**
     * @return an unmodifiable map of slot index to value
     */
    public Map<Integer, SimValue> getAll()
    {
        return locals;
    }

    /**
     * @return the indices that hold a value
     */
    public Set<Integer> getDefinedIndices()
    {
        return locals.keySet();
    }

    /**
     * Merges another state in at a control flow join, keeping this state's value
     * wherever both define a slot.
     *
     * @param other the state to merge in, may be null
     * @return the merged state, or this state if other is null
     */
    public LocalState merge(LocalState other)
    {
        if (other == null) return this;

        Map<Integer, SimValue> merged = new HashMap<>(locals);
        // For now, prefer this state's values
        // A more sophisticated implementation would merge types
        for (Map.Entry<Integer, SimValue> entry : other.locals.entrySet())
        {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return new LocalState(merged, Math.max(this.maxLocal, other.maxLocal));
    }

    /**
     * Discards every recorded slot.
     *
     * @return an empty state
     */
    public LocalState clear()
    {
        return empty();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof LocalState)) return false;
        LocalState that = (LocalState) o;
        return Objects.equals(locals, that.locals);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(locals);
    }

    @Override
    public String toString()
    {
        return "LocalState[size=" + locals.size() + ", maxLocal=" + maxLocal + ", locals=" + locals + "]";
    }
}
