package com.tonic.analysis.simulation.state;

import java.util.*;

/**
 * Immutable representation of local variable slots during simulation.
 * All operations return new LocalState instances.
 */
public final class LocalState {

    private final Map<Integer, SimValue> locals;
    private final int maxLocal;

    private LocalState(Map<Integer, SimValue> locals, int maxLocal) {
        this.locals = Collections.unmodifiableMap(new HashMap<>(locals));
        this.maxLocal = maxLocal;
    }

    /**
     * Creates an empty local state.
     */
    public static LocalState empty() {
        return new LocalState(Collections.emptyMap(), 0);
    }

    /**
     * Creates a local state with the given initial values.
     */
    public static LocalState of(Map<Integer, SimValue> values) {
        int max = values.keySet().stream().mapToInt(i -> i).max().orElse(0);
        return new LocalState(values, max);
    }

    /**
     * Creates a local state initialized for a method's parameters.
     *
     * @param paramCount number of parameters (including 'this' for instance methods)
     * @param paramValues the parameter values
     */
    public static LocalState forParameters(List<SimValue> paramValues) {
        Map<Integer, SimValue> locals = new HashMap<>();
        int slot = 0;
        for (SimValue value : paramValues) {
            locals.put(slot, value);
            slot++;
            if (value.isWide()) {
                locals.put(slot, SimValue.wideSecondSlot());
                slot++;
            }
        }
        return new LocalState(locals, slot > 0 ? slot - 1 : 0);
    }

    /**
     * Set a local variable.
     */
    public LocalState set(int index, SimValue value) {
        Map<Integer, SimValue> newLocals = new HashMap<>(locals);
        newLocals.put(index, value);
        return new LocalState(newLocals, Math.max(maxLocal, index));
    }

    /**
     * Set a wide local variable (long/double), occupying two slots.
     */
    public LocalState setWide(int index, SimValue value) {
        Map<Integer, SimValue> newLocals = new HashMap<>(locals);
        newLocals.put(index, value);
        newLocals.put(index + 1, SimValue.wideSecondSlot());
        return new LocalState(newLocals, Math.max(maxLocal, index + 1));
    }

    /**
     * Get a local variable.
     */
    public SimValue get(int index) {
        SimValue value = locals.get(index);
        if (value == null) {
            return SimValue.unknown(null);
        }
        return value;
    }

    /**
     * Get a local variable, skipping wide second slots.
     */
    public SimValue getValue(int index) {
        SimValue value = get(index);
        if (value.isWideSecondSlot()) {
            // This shouldn't happen in well-formed bytecode
            throw new IllegalStateException("Attempted to read wide second slot at index " + index);
        }
        return value;
    }

    /**
     * Check if a local variable is defined.
     */
    public boolean isDefined(int index) {
        return locals.containsKey(index);
    }

    /**
     * Get the maximum local variable index used.
     */
    public int maxLocal() {
        return maxLocal;
    }

    /**
     * Get the number of local variables defined.
     */
    public int size() {
        return locals.size();
    }

    /**
     * Get all local variable mappings.
     */
    public Map<Integer, SimValue> getAll() {
        return locals;
    }

    /**
     * Get all defined local variable indices.
     */
    public Set<Integer> getDefinedIndices() {
        return locals.keySet();
    }

    /**
     * Merge this local state with another for control flow convergence.
     */
    public LocalState merge(LocalState other) {
        if (other == null) return this;

        Map<Integer, SimValue> merged = new HashMap<>(locals);
        // For now, prefer this state's values
        // A more sophisticated implementation would merge types
        for (Map.Entry<Integer, SimValue> entry : other.locals.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return new LocalState(merged, Math.max(this.maxLocal, other.maxLocal));
    }

    /**
     * Create a copy with cleared locals (but preserving structure).
     */
    public LocalState clear() {
        return empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalState)) return false;
        LocalState that = (LocalState) o;
        return Objects.equals(locals, that.locals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locals);
    }

    @Override
    public String toString() {
        return "LocalState[size=" + locals.size() + ", maxLocal=" + maxLocal + ", locals=" + locals + "]";
    }
}
