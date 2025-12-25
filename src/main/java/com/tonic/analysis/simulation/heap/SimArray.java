package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.IRType;

import java.util.*;

/**
 * Represents an abstract array with element tracking.
 * Uses both index-insensitive (all possible elements) and index-sensitive
 * (known constant indices) tracking for precision.
 */
public final class SimArray {

    private final AllocationSite site;
    private final IRType elementType;
    private final SimValue length;
    private final Set<SimValue> elements;
    private final Map<Integer, Set<SimValue>> knownIndices;
    private final boolean escaped;

    public SimArray(AllocationSite site, IRType elementType, SimValue length) {
        this.site = Objects.requireNonNull(site);
        this.elementType = elementType;
        this.length = length;
        this.elements = Collections.emptySet();
        this.knownIndices = Collections.emptyMap();
        this.escaped = false;
    }

    private SimArray(AllocationSite site, IRType elementType, SimValue length,
                     Set<SimValue> elements, Map<Integer, Set<SimValue>> knownIndices,
                     boolean escaped) {
        this.site = site;
        this.elementType = elementType;
        this.length = length;
        this.elements = elements;
        this.knownIndices = knownIndices;
        this.escaped = escaped;
    }

    public AllocationSite getSite() {
        return site;
    }

    public IRType getElementType() {
        return elementType;
    }

    public SimValue getLength() {
        return length;
    }

    public boolean hasEscaped() {
        return escaped;
    }

    public Set<SimValue> getAllElements() {
        return Collections.unmodifiableSet(elements);
    }

    public Set<SimValue> getElement(SimValue index) {
        if (index != null && index.isConstant()) {
            Object constVal = index.getConstantValue();
            if (constVal instanceof Integer) {
                int idx = (Integer) constVal;
                Set<SimValue> indexedValues = knownIndices.get(idx);
                if (indexedValues != null && !indexedValues.isEmpty()) {
                    return Collections.unmodifiableSet(indexedValues);
                }
            }
        }
        return Collections.unmodifiableSet(elements);
    }

    public Set<SimValue> getElementAt(int index) {
        Set<SimValue> indexedValues = knownIndices.get(index);
        if (indexedValues != null && !indexedValues.isEmpty()) {
            return Collections.unmodifiableSet(indexedValues);
        }
        return Collections.unmodifiableSet(elements);
    }

    public SimArray withElement(SimValue index, SimValue value) {
        Set<SimValue> newElements = new HashSet<>(elements);
        newElements.add(value);

        Map<Integer, Set<SimValue>> newKnownIndices = new HashMap<>(knownIndices);

        if (index != null && index.isConstant()) {
            Object constVal = index.getConstantValue();
            if (constVal instanceof Integer) {
                int idx = (Integer) constVal;
                Set<SimValue> existing = newKnownIndices.get(idx);
                if (existing == null) {
                    newKnownIndices.put(idx, Collections.singleton(value));
                } else {
                    Set<SimValue> merged = new HashSet<>(existing);
                    merged.add(value);
                    newKnownIndices.put(idx, merged);
                }
            }
        }

        return new SimArray(site, elementType, length, newElements, newKnownIndices, escaped);
    }

    public SimArray withElementAt(int index, SimValue value) {
        Set<SimValue> newElements = new HashSet<>(elements);
        newElements.add(value);

        Map<Integer, Set<SimValue>> newKnownIndices = new HashMap<>(knownIndices);
        Set<SimValue> existing = newKnownIndices.get(index);
        if (existing == null) {
            newKnownIndices.put(index, Collections.singleton(value));
        } else {
            Set<SimValue> merged = new HashSet<>(existing);
            merged.add(value);
            newKnownIndices.put(index, merged);
        }

        return new SimArray(site, elementType, length, newElements, newKnownIndices, escaped);
    }

    public SimArray markEscaped() {
        if (escaped) {
            return this;
        }
        return new SimArray(site, elementType, length, elements, knownIndices, true);
    }

    public SimArray merge(SimArray other) {
        if (!this.site.equals(other.site)) {
            throw new IllegalArgumentException("Cannot merge SimArrays with different allocation sites");
        }

        Set<SimValue> mergedElements = new HashSet<>(this.elements);
        mergedElements.addAll(other.elements);

        Map<Integer, Set<SimValue>> mergedIndices = new HashMap<>(this.knownIndices);
        for (Map.Entry<Integer, Set<SimValue>> entry : other.knownIndices.entrySet()) {
            int idx = entry.getKey();
            Set<SimValue> otherValues = entry.getValue();
            Set<SimValue> thisValues = mergedIndices.get(idx);

            if (thisValues == null) {
                mergedIndices.put(idx, new HashSet<>(otherValues));
            } else {
                Set<SimValue> merged = new HashSet<>(thisValues);
                merged.addAll(otherValues);
                mergedIndices.put(idx, merged);
            }
        }

        boolean mergedEscaped = this.escaped || other.escaped;
        return new SimArray(site, elementType, length, mergedElements, mergedIndices, mergedEscaped);
    }

    public int getKnownIndexCount() {
        return knownIndices.size();
    }

    public Set<Integer> getKnownIndices() {
        return Collections.unmodifiableSet(knownIndices.keySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimArray)) return false;
        SimArray that = (SimArray) o;
        return escaped == that.escaped &&
               site.equals(that.site) &&
               Objects.equals(elementType, that.elementType) &&
               Objects.equals(length, that.length) &&
               elements.equals(that.elements) &&
               knownIndices.equals(that.knownIndices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(site, elementType, length, elements, knownIndices, escaped);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SimArray[");
        sb.append("site=").append(site);
        if (elementType != null) {
            sb.append(", type=").append(elementType);
        }
        sb.append(", elements=").append(elements.size());
        if (!knownIndices.isEmpty()) {
            sb.append(", knownIndices=").append(knownIndices.size());
        }
        if (escaped) {
            sb.append(", ESCAPED");
        }
        sb.append("]");
        return sb.toString();
    }
}
