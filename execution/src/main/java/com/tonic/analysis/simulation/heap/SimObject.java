package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;

import java.util.*;

/**
 * Represents an abstract heap object with field-sensitive tracking.
 * SimObjects are immutable - all modifications return new instances.
 */
public final class SimObject {

    private final AllocationSite site;
    private final Map<FieldKey, Set<SimValue>> fields;
    private final boolean escaped;

    public SimObject(AllocationSite site) {
        this.site = Objects.requireNonNull(site);
        this.fields = Collections.emptyMap();
        this.escaped = false;
    }

    private SimObject(AllocationSite site, Map<FieldKey, Set<SimValue>> fields, boolean escaped) {
        this.site = site;
        this.fields = fields;
        this.escaped = escaped;
    }

    public AllocationSite getSite() {
        return site;
    }

    public boolean hasEscaped() {
        return escaped;
    }

    public Set<FieldKey> getFieldKeys() {
        return Collections.unmodifiableSet(fields.keySet());
    }

    public Set<SimValue> getField(FieldKey key) {
        Set<SimValue> values = fields.get(key);
        return values != null ? Collections.unmodifiableSet(values) : Collections.emptySet();
    }

    public boolean hasField(FieldKey key) {
        return fields.containsKey(key);
    }

    public SimObject withField(FieldKey key, SimValue value) {
        Map<FieldKey, Set<SimValue>> newFields = new HashMap<>(fields);
        Set<SimValue> existing = newFields.get(key);
        if (existing == null) {
            newFields.put(key, Collections.singleton(value));
        } else {
            Set<SimValue> merged = new HashSet<>(existing);
            merged.add(value);
            newFields.put(key, merged);
        }
        return new SimObject(site, newFields, escaped);
    }

    public SimObject withFieldSet(FieldKey key, Set<SimValue> values) {
        if (values.isEmpty()) {
            return this;
        }
        Map<FieldKey, Set<SimValue>> newFields = new HashMap<>(fields);
        Set<SimValue> existing = newFields.get(key);
        if (existing == null) {
            newFields.put(key, new HashSet<>(values));
        } else {
            Set<SimValue> merged = new HashSet<>(existing);
            merged.addAll(values);
            newFields.put(key, merged);
        }
        return new SimObject(site, newFields, escaped);
    }

    public SimObject markEscaped() {
        if (escaped) {
            return this;
        }
        return new SimObject(site, fields, true);
    }

    public SimObject merge(SimObject other) {
        if (!this.site.equals(other.site)) {
            throw new IllegalArgumentException("Cannot merge SimObjects with different allocation sites");
        }

        Map<FieldKey, Set<SimValue>> mergedFields = new HashMap<>(this.fields);
        for (Map.Entry<FieldKey, Set<SimValue>> entry : other.fields.entrySet()) {
            FieldKey key = entry.getKey();
            Set<SimValue> otherValues = entry.getValue();
            Set<SimValue> thisValues = mergedFields.get(key);

            if (thisValues == null) {
                mergedFields.put(key, new HashSet<>(otherValues));
            } else {
                Set<SimValue> merged = new HashSet<>(thisValues);
                merged.addAll(otherValues);
                mergedFields.put(key, merged);
            }
        }

        boolean mergedEscaped = this.escaped || other.escaped;
        return new SimObject(site, mergedFields, mergedEscaped);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimObject)) return false;
        SimObject that = (SimObject) o;
        return escaped == that.escaped &&
               site.equals(that.site) &&
               fields.equals(that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(site, fields, escaped);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SimObject[");
        sb.append("site=").append(site);
        if (escaped) {
            sb.append(", ESCAPED");
        }
        if (!fields.isEmpty()) {
            sb.append(", fields={");
            boolean first = true;
            for (Map.Entry<FieldKey, Set<SimValue>> entry : fields.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(entry.getKey().getName()).append("=").append(entry.getValue().size()).append(" values");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
}
