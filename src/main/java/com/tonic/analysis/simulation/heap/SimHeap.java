package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.IRType;

import java.util.*;

/**
 * Central heap manager for simulation with configurable modes.
 * Tracks objects, arrays, and static fields.
 */
public final class SimHeap {

    private final HeapMode mode;
    private final Map<AllocationSite, SimObject> objects;
    private final Map<AllocationSite, SimArray> arrays;
    private final Map<FieldKey, Set<SimValue>> staticFields;

    public SimHeap(HeapMode mode) {
        this.mode = mode;
        this.objects = new HashMap<>();
        this.arrays = new HashMap<>();
        this.staticFields = new HashMap<>();
    }

    private SimHeap(HeapMode mode, Map<AllocationSite, SimObject> objects,
                    Map<AllocationSite, SimArray> arrays,
                    Map<FieldKey, Set<SimValue>> staticFields) {
        this.mode = mode;
        this.objects = objects;
        this.arrays = arrays;
        this.staticFields = staticFields;
    }

    public HeapMode getMode() {
        return mode;
    }

    public SimHeap allocate(AllocationSite site) {
        SimObject obj = new SimObject(site);
        if (mode == HeapMode.MUTABLE) {
            objects.put(site, obj);
            return this;
        } else {
            Map<AllocationSite, SimObject> newObjects = new HashMap<>(objects);
            newObjects.put(site, obj);
            return new SimHeap(mode, newObjects, arrays, staticFields);
        }
    }

    public SimValue allocateAndGetRef(AllocationSite site, IRType type,
                                       java.util.function.Function<AllocationSite, SimValue> refFactory) {
        allocate(site);
        return refFactory.apply(site);
    }

    public SimHeap allocateArray(AllocationSite site, IRType elementType, SimValue length) {
        SimArray arr = new SimArray(site, elementType, length);
        if (mode == HeapMode.MUTABLE) {
            arrays.put(site, arr);
            return this;
        } else {
            Map<AllocationSite, SimArray> newArrays = new HashMap<>(arrays);
            newArrays.put(site, arr);
            return new SimHeap(mode, objects, newArrays, staticFields);
        }
    }

    public SimObject getObject(AllocationSite site) {
        return objects.get(site);
    }

    public SimArray getArray(AllocationSite site) {
        return arrays.get(site);
    }

    public boolean hasObject(AllocationSite site) {
        return objects.containsKey(site);
    }

    public boolean hasArray(AllocationSite site) {
        return arrays.containsKey(site);
    }

    public SimHeap putField(AllocationSite site, FieldKey field, SimValue value) {
        SimObject obj = objects.get(site);
        if (obj == null) {
            return this;
        }
        SimObject updated = obj.withField(field, value);

        if (mode == HeapMode.MUTABLE) {
            objects.put(site, updated);
            return this;
        } else {
            Map<AllocationSite, SimObject> newObjects = new HashMap<>(objects);
            newObjects.put(site, updated);
            return new SimHeap(mode, newObjects, arrays, staticFields);
        }
    }

    public SimHeap putFieldForRef(Set<AllocationSite> sites, FieldKey field, SimValue value) {
        if (sites.isEmpty()) {
            return this;
        }

        if (mode == HeapMode.MUTABLE) {
            for (AllocationSite site : sites) {
                SimObject obj = objects.get(site);
                if (obj != null) {
                    objects.put(site, obj.withField(field, value));
                }
            }
            return this;
        } else {
            Map<AllocationSite, SimObject> newObjects = new HashMap<>(objects);
            for (AllocationSite site : sites) {
                SimObject obj = newObjects.get(site);
                if (obj != null) {
                    newObjects.put(site, obj.withField(field, value));
                }
            }
            return new SimHeap(mode, newObjects, arrays, staticFields);
        }
    }

    public Set<SimValue> getField(AllocationSite site, FieldKey field) {
        SimObject obj = objects.get(site);
        if (obj == null) {
            return Collections.emptySet();
        }
        return obj.getField(field);
    }

    public Set<SimValue> getFieldForRef(Set<AllocationSite> sites, FieldKey field) {
        if (sites.isEmpty()) {
            return Collections.emptySet();
        }
        if (sites.size() == 1) {
            return getField(sites.iterator().next(), field);
        }
        Set<SimValue> result = new HashSet<>();
        for (AllocationSite site : sites) {
            result.addAll(getField(site, field));
        }
        return result;
    }

    public SimHeap arrayStore(AllocationSite site, SimValue index, SimValue value) {
        SimArray arr = arrays.get(site);
        if (arr == null) {
            return this;
        }
        SimArray updated = arr.withElement(index, value);

        if (mode == HeapMode.MUTABLE) {
            arrays.put(site, updated);
            return this;
        } else {
            Map<AllocationSite, SimArray> newArrays = new HashMap<>(arrays);
            newArrays.put(site, updated);
            return new SimHeap(mode, objects, newArrays, staticFields);
        }
    }

    public SimHeap arrayStoreForRef(Set<AllocationSite> sites, SimValue index, SimValue value) {
        if (sites.isEmpty()) {
            return this;
        }

        if (mode == HeapMode.MUTABLE) {
            for (AllocationSite site : sites) {
                SimArray arr = arrays.get(site);
                if (arr != null) {
                    arrays.put(site, arr.withElement(index, value));
                }
            }
            return this;
        } else {
            Map<AllocationSite, SimArray> newArrays = new HashMap<>(arrays);
            for (AllocationSite site : sites) {
                SimArray arr = newArrays.get(site);
                if (arr != null) {
                    newArrays.put(site, arr.withElement(index, value));
                }
            }
            return new SimHeap(mode, objects, newArrays, staticFields);
        }
    }

    public Set<SimValue> arrayLoad(AllocationSite site, SimValue index) {
        SimArray arr = arrays.get(site);
        if (arr == null) {
            return Collections.emptySet();
        }
        return arr.getElement(index);
    }

    public Set<SimValue> arrayLoadForRef(Set<AllocationSite> sites, SimValue index) {
        if (sites.isEmpty()) {
            return Collections.emptySet();
        }
        if (sites.size() == 1) {
            return arrayLoad(sites.iterator().next(), index);
        }
        Set<SimValue> result = new HashSet<>();
        for (AllocationSite site : sites) {
            result.addAll(arrayLoad(site, index));
        }
        return result;
    }

    public SimHeap putStatic(FieldKey field, SimValue value) {
        if (mode == HeapMode.MUTABLE) {
            Set<SimValue> existing = staticFields.get(field);
            if (existing == null) {
                staticFields.put(field, new HashSet<>(Collections.singleton(value)));
            } else {
                existing.add(value);
            }
            return this;
        } else {
            Map<FieldKey, Set<SimValue>> newStatics = new HashMap<>(staticFields);
            Set<SimValue> existing = newStatics.get(field);
            if (existing == null) {
                newStatics.put(field, new HashSet<>(Collections.singleton(value)));
            } else {
                Set<SimValue> merged = new HashSet<>(existing);
                merged.add(value);
                newStatics.put(field, merged);
            }
            return new SimHeap(mode, objects, arrays, newStatics);
        }
    }

    public Set<SimValue> getStatic(FieldKey field) {
        Set<SimValue> values = staticFields.get(field);
        return values != null ? Collections.unmodifiableSet(values) : Collections.emptySet();
    }

    public SimHeap markEscaped(AllocationSite site) {
        SimObject obj = objects.get(site);
        SimArray arr = arrays.get(site);

        if (obj == null && arr == null) {
            return this;
        }

        if (mode == HeapMode.MUTABLE) {
            if (obj != null) objects.put(site, obj.markEscaped());
            if (arr != null) arrays.put(site, arr.markEscaped());
            return this;
        } else {
            Map<AllocationSite, SimObject> newObjects =
                obj != null ? new HashMap<>(objects) : objects;
            Map<AllocationSite, SimArray> newArrays =
                arr != null ? new HashMap<>(arrays) : arrays;

            if (obj != null) newObjects.put(site, obj.markEscaped());
            if (arr != null) newArrays.put(site, arr.markEscaped());

            return new SimHeap(mode, newObjects, newArrays, staticFields);
        }
    }

    public boolean hasEscaped(AllocationSite site) {
        SimObject obj = objects.get(site);
        if (obj != null && obj.hasEscaped()) {
            return true;
        }
        SimArray arr = arrays.get(site);
        return arr != null && arr.hasEscaped();
    }

    public boolean mayAlias(Set<AllocationSite> sites1, Set<AllocationSite> sites2) {
        if (sites1.isEmpty() || sites2.isEmpty()) {
            return false;
        }
        for (AllocationSite site : sites1) {
            if (sites2.contains(site)) {
                return true;
            }
        }
        return false;
    }

    public boolean mustAlias(Set<AllocationSite> sites1, Set<AllocationSite> sites2) {
        return sites1.size() == 1 && sites2.size() == 1 &&
               sites1.iterator().next().equals(sites2.iterator().next());
    }

    public SimHeap merge(SimHeap other) {
        if (this == other) {
            return this;
        }

        Map<AllocationSite, SimObject> mergedObjects = new HashMap<>(this.objects);
        for (Map.Entry<AllocationSite, SimObject> entry : other.objects.entrySet()) {
            AllocationSite site = entry.getKey();
            SimObject otherObj = entry.getValue();
            SimObject thisObj = mergedObjects.get(site);

            if (thisObj == null) {
                mergedObjects.put(site, otherObj);
            } else {
                mergedObjects.put(site, thisObj.merge(otherObj));
            }
        }

        Map<AllocationSite, SimArray> mergedArrays = new HashMap<>(this.arrays);
        for (Map.Entry<AllocationSite, SimArray> entry : other.arrays.entrySet()) {
            AllocationSite site = entry.getKey();
            SimArray otherArr = entry.getValue();
            SimArray thisArr = mergedArrays.get(site);

            if (thisArr == null) {
                mergedArrays.put(site, otherArr);
            } else {
                mergedArrays.put(site, thisArr.merge(otherArr));
            }
        }

        Map<FieldKey, Set<SimValue>> mergedStatics = new HashMap<>(this.staticFields);
        for (Map.Entry<FieldKey, Set<SimValue>> entry : other.staticFields.entrySet()) {
            FieldKey field = entry.getKey();
            Set<SimValue> otherValues = entry.getValue();
            Set<SimValue> thisValues = mergedStatics.get(field);

            if (thisValues == null) {
                mergedStatics.put(field, new HashSet<>(otherValues));
            } else {
                Set<SimValue> merged = new HashSet<>(thisValues);
                merged.addAll(otherValues);
                mergedStatics.put(field, merged);
            }
        }

        HeapMode mergedMode = (mode == HeapMode.COPY_ON_MERGE || other.mode == HeapMode.COPY_ON_MERGE)
            ? HeapMode.COPY_ON_MERGE
            : mode;

        return new SimHeap(mergedMode, mergedObjects, mergedArrays, mergedStatics);
    }

    public SimHeap copy() {
        return new SimHeap(mode,
            new HashMap<>(objects),
            new HashMap<>(arrays),
            new HashMap<>(staticFields));
    }

    public Set<AllocationSite> getAllObjectSites() {
        return Collections.unmodifiableSet(objects.keySet());
    }

    public Set<AllocationSite> getAllArraySites() {
        return Collections.unmodifiableSet(arrays.keySet());
    }

    public Set<AllocationSite> getAllSites() {
        Set<AllocationSite> all = new HashSet<>(objects.keySet());
        all.addAll(arrays.keySet());
        return all;
    }

    public int getObjectCount() {
        return objects.size();
    }

    public int getArrayCount() {
        return arrays.size();
    }

    public boolean isEmpty() {
        return objects.isEmpty() && arrays.isEmpty() && staticFields.isEmpty();
    }

    @Override
    public String toString() {
        return "SimHeap[mode=" + mode +
               ", objects=" + objects.size() +
               ", arrays=" + arrays.size() +
               ", statics=" + staticFields.size() + "]";
    }
}
