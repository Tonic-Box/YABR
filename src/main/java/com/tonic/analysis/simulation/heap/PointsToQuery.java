package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;

import java.util.*;

/**
 * Query interface for points-to analysis results.
 * Provides methods to query aliasing and reachability.
 */
public final class PointsToQuery {

    private final SimHeap heap;

    public PointsToQuery(SimHeap heap) {
        this.heap = Objects.requireNonNull(heap);
    }

    public Set<AllocationSite> pointsTo(SimValue ref) {
        if (ref == null) {
            return Collections.emptySet();
        }
        return ref.getPointsTo();
    }

    public boolean mayPointTo(SimValue ref, AllocationSite site) {
        if (ref == null || site == null) {
            return false;
        }
        return ref.getPointsTo().contains(site);
    }

    public boolean mustPointTo(SimValue ref, AllocationSite site) {
        if (ref == null || site == null) {
            return false;
        }
        Set<AllocationSite> pts = ref.getPointsTo();
        return pts.size() == 1 && pts.contains(site);
    }

    public boolean mayAlias(SimValue ref1, SimValue ref2) {
        if (ref1 == null || ref2 == null) {
            return false;
        }
        if (ref1.isDefinitelyNull() || ref2.isDefinitelyNull()) {
            return ref1.isDefinitelyNull() && ref2.isDefinitelyNull();
        }

        Set<AllocationSite> pts1 = ref1.getPointsTo();
        Set<AllocationSite> pts2 = ref2.getPointsTo();

        if (pts1.isEmpty() || pts2.isEmpty()) {
            return true;
        }

        for (AllocationSite site : pts1) {
            if (pts2.contains(site)) {
                return true;
            }
        }
        return false;
    }

    public boolean mustAlias(SimValue ref1, SimValue ref2) {
        if (ref1 == null || ref2 == null) {
            return false;
        }
        if (ref1.isDefinitelyNull() && ref2.isDefinitelyNull()) {
            return true;
        }

        Set<AllocationSite> pts1 = ref1.getPointsTo();
        Set<AllocationSite> pts2 = ref2.getPointsTo();

        return pts1.size() == 1 && pts2.size() == 1 &&
               pts1.iterator().next().equals(pts2.iterator().next());
    }

    public boolean mayBeNull(SimValue ref) {
        return ref == null || ref.mayBeNull();
    }

    public boolean isDefinitelyNull(SimValue ref) {
        return ref != null && ref.isDefinitelyNull();
    }

    public boolean isDefinitelyNotNull(SimValue ref) {
        return ref != null && ref.isDefinitelyNotNull();
    }

    public Set<SimValue> reachableFrom(SimValue root) {
        if (root == null) {
            return Collections.emptySet();
        }

        Set<SimValue> visited = new HashSet<>();
        Queue<SimValue> worklist = new LinkedList<>();
        worklist.add(root);
        visited.add(root);

        while (!worklist.isEmpty()) {
            SimValue current = worklist.poll();

            for (AllocationSite site : current.getPointsTo()) {
                collectReachableFromSite(site, visited, worklist);
            }
        }

        return visited;
    }

    private void collectReachableFromSite(AllocationSite site, Set<SimValue> visited, Queue<SimValue> worklist) {
        SimObject obj = heap.getObject(site);
        if (obj != null) {
            for (FieldKey field : obj.getFieldKeys()) {
                for (SimValue value : obj.getField(field)) {
                    if (visited.add(value)) {
                        worklist.add(value);
                    }
                }
            }
        }

        SimArray arr = heap.getArray(site);
        if (arr != null) {
            for (SimValue element : arr.getAllElements()) {
                if (visited.add(element)) {
                    worklist.add(element);
                }
            }
        }
    }

    public Set<AllocationSite> reachableSitesFrom(SimValue root) {
        Set<AllocationSite> sites = new HashSet<>();
        for (SimValue value : reachableFrom(root)) {
            sites.addAll(value.getPointsTo());
        }
        return sites;
    }

    public Set<SimValue> getFieldValues(SimValue objectRef, FieldKey field) {
        Set<SimValue> result = new HashSet<>();
        for (AllocationSite site : objectRef.getPointsTo()) {
            result.addAll(heap.getField(site, field));
        }
        return result;
    }

    public Set<SimValue> getArrayElements(SimValue arrayRef) {
        Set<SimValue> result = new HashSet<>();
        for (AllocationSite site : arrayRef.getPointsTo()) {
            SimArray arr = heap.getArray(site);
            if (arr != null) {
                result.addAll(arr.getAllElements());
            }
        }
        return result;
    }

    public int getPointsToSetSize(SimValue ref) {
        return ref == null ? 0 : ref.getPointsTo().size();
    }

    public boolean isSingleton(SimValue ref) {
        return ref != null && ref.getPointsTo().size() == 1;
    }

    @Override
    public String toString() {
        return "PointsToQuery[heap=" + heap + "]";
    }
}
