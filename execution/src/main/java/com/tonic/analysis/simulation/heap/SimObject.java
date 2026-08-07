package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;

import java.util.*;

/**
 * Immutable abstract heap object for one allocation site, holding a set of
 * possible values per field; every mutator returns a new instance.
 */
public final class SimObject
{

    private final AllocationSite site;
    private final Map<FieldKey, Set<SimValue>> fields;
    private final boolean escaped;

    /**
     * Creates an unescaped object with no fields written.
     * @param site the allocation site it stands for
     * @throws NullPointerException if the site is null
     */
    public SimObject(AllocationSite site)
    {
        this.site = Objects.requireNonNull(site);
        this.fields = Collections.emptyMap();
        this.escaped = false;
    }

    private SimObject(AllocationSite site, Map<FieldKey, Set<SimValue>> fields, boolean escaped)
    {
        this.site = site;
        this.fields = fields;
        this.escaped = escaped;
    }

    /**
     * @return the site
     */
    public AllocationSite getSite()
    {
        return site;
    }

    /**
     * @return whether escaped
     */
    public boolean hasEscaped()
    {
        return escaped;
    }

    /**
     * @return an unmodifiable view of the fields written so far
     */
    public Set<FieldKey> getFieldKeys()
    {
        return Collections.unmodifiableSet(fields.keySet());
    }

    /**
     * @param key the field to read
     * @return an unmodifiable view of its values, empty if never written
     */
    public Set<SimValue> getField(FieldKey key)
    {
        Set<SimValue> values = fields.get(key);
        return values != null ? Collections.unmodifiableSet(values) : Collections.emptySet();
    }

    /**
     * @param key the field to test
     * @return true if the field has been written
     */
    public boolean hasField(FieldKey key)
    {
        return fields.containsKey(key);
    }

    /**
     * Adds one value to a field's set - a weak update, keeping whatever is
     * already there.
     * @param key the field
     * @param value the value to add
     * @return the updated copy
     */
    public SimObject withField(FieldKey key, SimValue value)
    {
        Map<FieldKey, Set<SimValue>> newFields = new HashMap<>(fields);
        Set<SimValue> existing = newFields.get(key);
        if (existing == null)
        {
            newFields.put(key, Collections.singleton(value));
        }
        else
        {
            Set<SimValue> merged = new HashSet<>(existing);
            merged.add(value);
            newFields.put(key, merged);
        }
        return new SimObject(site, newFields, escaped);
    }

    /**
     * Adds several values to a field's set - a weak update, keeping whatever is
     * already there.
     * @param key the field
     * @param values the values to add; an empty set is a no-op
     * @return the updated copy, or this object if nothing was added
     */
    public SimObject withFieldSet(FieldKey key, Set<SimValue> values)
    {
        if (values.isEmpty())
        {
            return this;
        }
        Map<FieldKey, Set<SimValue>> newFields = new HashMap<>(fields);
        Set<SimValue> existing = newFields.get(key);
        if (existing == null)
        {
            newFields.put(key, new HashSet<>(values));
        }
        else
        {
            Set<SimValue> merged = new HashSet<>(existing);
            merged.addAll(values);
            newFields.put(key, merged);
        }
        return new SimObject(site, newFields, escaped);
    }

    /**
     * @return a copy flagged as escaped, or this object if it already is
     */
    public SimObject markEscaped()
    {
        if (escaped)
        {
            return this;
        }
        return new SimObject(site, fields, true);
    }

    /**
     * Unions the field value sets of two views of the same allocation site,
     * escaping the result if either side escaped.
     * @param other the other view of this site
     * @return the merged object
     * @throws IllegalArgumentException if the allocation sites differ
     */
    public SimObject merge(SimObject other)
    {
        if (!this.site.equals(other.site))
        {
            throw new IllegalArgumentException("Cannot merge SimObjects with different allocation sites");
        }

        Map<FieldKey, Set<SimValue>> mergedFields = new HashMap<>(this.fields);
        for (Map.Entry<FieldKey, Set<SimValue>> entry : other.fields.entrySet())
        {
            FieldKey key = entry.getKey();
            Set<SimValue> otherValues = entry.getValue();
            Set<SimValue> thisValues = mergedFields.get(key);

            if (thisValues == null)
            {
                mergedFields.put(key, new HashSet<>(otherValues));
            }
            else
            {
                Set<SimValue> merged = new HashSet<>(thisValues);
                merged.addAll(otherValues);
                mergedFields.put(key, merged);
            }
        }

        boolean mergedEscaped = this.escaped || other.escaped;
        return new SimObject(site, mergedFields, mergedEscaped);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SimObject)) return false;
        SimObject that = (SimObject) o;
        return escaped == that.escaped &&
               site.equals(that.site) &&
               fields.equals(that.fields);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(site, fields, escaped);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("SimObject[");
        sb.append("site=").append(site);
        if (escaped)
        {
            sb.append(", ESCAPED");
        }
        if (!fields.isEmpty())
        {
            sb.append(", fields={");
            boolean first = true;
            for (Map.Entry<FieldKey, Set<SimValue>> entry : fields.entrySet())
            {
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
