package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;

import java.util.*;

/**
 * Analyzes escape states of objects in the simulation heap.
 * Determines whether objects escape method/thread scope.
 */
public final class EscapeAnalyzer {

    public enum EscapeState {
        NO_ESCAPE,
        ARG_ESCAPE,
        GLOBAL_ESCAPE
    }

    private final SimHeap heap;

    public EscapeAnalyzer(SimHeap heap) {
        this.heap = heap;
    }

    public EscapeState analyze(AllocationSite site) {
        if (heap.hasEscaped(site)) {
            return EscapeState.GLOBAL_ESCAPE;
        }

        if (isStoredInStaticField(site)) {
            return EscapeState.GLOBAL_ESCAPE;
        }

        if (isReachableFromEscaped(site)) {
            return EscapeState.GLOBAL_ESCAPE;
        }

        return EscapeState.NO_ESCAPE;
    }

    public Set<AllocationSite> getNonEscaping() {
        Set<AllocationSite> nonEscaping = new HashSet<>();
        for (AllocationSite site : heap.getAllSites()) {
            if (analyze(site) == EscapeState.NO_ESCAPE) {
                nonEscaping.add(site);
            }
        }
        return nonEscaping;
    }

    public Set<AllocationSite> getEscaping() {
        Set<AllocationSite> escaping = new HashSet<>();
        for (AllocationSite site : heap.getAllSites()) {
            if (analyze(site) != EscapeState.NO_ESCAPE) {
                escaping.add(site);
            }
        }
        return escaping;
    }

    public boolean mayEscape(AllocationSite site) {
        return analyze(site) != EscapeState.NO_ESCAPE;
    }

    public boolean definitelyEscapes(AllocationSite site) {
        return analyze(site) == EscapeState.GLOBAL_ESCAPE;
    }

    private boolean isStoredInStaticField(AllocationSite site) {
        SimObject obj = heap.getObject(site);
        if (obj == null) return false;

        return false;
    }

    private boolean isReachableFromEscaped(AllocationSite site) {
        Set<AllocationSite> escaped = new HashSet<>();
        for (AllocationSite s : heap.getAllSites()) {
            if (heap.hasEscaped(s)) {
                escaped.add(s);
            }
        }

        Set<AllocationSite> reachable = computeReachable(escaped);
        return reachable.contains(site);
    }

    private Set<AllocationSite> computeReachable(Set<AllocationSite> roots) {
        Set<AllocationSite> reachable = new HashSet<>(roots);
        Queue<AllocationSite> worklist = new LinkedList<>(roots);

        while (!worklist.isEmpty()) {
            AllocationSite current = worklist.poll();

            SimObject obj = heap.getObject(current);
            if (obj != null) {
                for (FieldKey field : obj.getFieldKeys()) {
                    for (SimValue value : obj.getField(field)) {
                        for (AllocationSite target : value.getPointsTo()) {
                            if (reachable.add(target)) {
                                worklist.add(target);
                            }
                        }
                    }
                }
            }

            SimArray arr = heap.getArray(current);
            if (arr != null) {
                for (SimValue element : arr.getAllElements()) {
                    for (AllocationSite target : element.getPointsTo()) {
                        if (reachable.add(target)) {
                            worklist.add(target);
                        }
                    }
                }
            }
        }

        return reachable;
    }

    public Set<AllocationSite> getReachableFrom(AllocationSite root) {
        return computeReachable(Collections.singleton(root));
    }

    public boolean isReachableFrom(AllocationSite source, AllocationSite target) {
        return getReachableFrom(source).contains(target);
    }

    @Override
    public String toString() {
        int total = heap.getAllSites().size();
        int escaped = getEscaping().size();
        return "EscapeAnalyzer[total=" + total + ", escaped=" + escaped + "]";
    }
}
