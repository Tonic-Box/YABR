package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;

import java.util.*;

/**
 * Escape classifier for simulation heap allocation sites, driven by the heap's escape marks and
 * reachability from them.
 */
public final class EscapeAnalyzer
{

    /**
     * How far an allocation escapes its allocating method.
     */
    public enum EscapeState
    {
        /**
         * The allocation stays confined to its allocating method, so it is a candidate for
         * stack allocation or scalar replacement.
         */
        NO_ESCAPE,
        /**
         * The allocation is handed to a callee but no further; this analyzer never reports it.
         */
        ARG_ESCAPE,
        /**
         * The allocation is visible outside the method, having been marked escaped, stored into
         * a static field, or made reachable from an escaped object.
         */
        GLOBAL_ESCAPE
    }

    private final SimHeap heap;

    /**
     * Creates an analyzer over an existing heap; results reflect the heap's state at each query.
     * @param heap the simulation heap to inspect
     */
    public EscapeAnalyzer(SimHeap heap)
    {
        this.heap = heap;
    }

    /**
     * Classifies a site as globally escaping if the heap marked it escaped, it lands in a static
     * field, or it is reachable from an escaped site.
     * @param site the allocation site to classify
     * @return the escape state; ARG_ESCAPE is never reported
     */
    public EscapeState analyze(AllocationSite site)
    {
        if (heap.hasEscaped(site))
        {
            return EscapeState.GLOBAL_ESCAPE;
        }
        return stateOf(site, escapedClosure(heap.getAllSites()));
    }

    /**
     * @return every heap site that classifies as NO_ESCAPE
     */
    public Set<AllocationSite> getNonEscaping()
    {
        Set<AllocationSite> allSites = heap.getAllSites();
        Set<AllocationSite> closure = escapedClosure(allSites);

        Set<AllocationSite> nonEscaping = new HashSet<>();
        for (AllocationSite site : allSites)
        {
            if (!closure.contains(site))
            {
                nonEscaping.add(site);
            }
        }
        return nonEscaping;
    }

    /**
     * @return every heap site that does not classify as NO_ESCAPE
     */
    public Set<AllocationSite> getEscaping()
    {
        Set<AllocationSite> allSites = heap.getAllSites();
        Set<AllocationSite> closure = escapedClosure(allSites);

        Set<AllocationSite> escaping = new HashSet<>();
        for (AllocationSite site : allSites)
        {
            if (closure.contains(site))
            {
                escaping.add(site);
            }
        }
        return escaping;
    }

    /**
     * @param site the allocation site to classify
     * @return true if the site is anything other than NO_ESCAPE
     */
    public boolean mayEscape(AllocationSite site)
    {
        return analyze(site) != EscapeState.NO_ESCAPE;
    }

    /**
     * @param site the allocation site to classify
     * @return true if the site is known to escape globally
     */
    public boolean definitelyEscapes(AllocationSite site)
    {
        return analyze(site) == EscapeState.GLOBAL_ESCAPE;
    }

    /**
     * Every site visible outside its allocating method: the escape-marked and statically held sites,
     * plus everything reachable from them through fields and array elements. Escape marks and static
     * fields are both global roots, and reachability from a union of roots is the union of their
     * reachable sets, so a single walk from the combined roots answers every classification. The
     * closure is rebuilt per query rather than cached because a MUTABLE heap is written in place and
     * the analyzer is contracted to reflect the heap's state at each query.
     * @param allSites every site the heap holds
     * @return the escaping sites, empty if the heap has no global roots
     */
    private Set<AllocationSite> escapedClosure(Set<AllocationSite> allSites)
    {
        Set<AllocationSite> roots = new HashSet<>(heap.getStaticRoots());
        for (AllocationSite site : allSites)
        {
            if (heap.hasEscaped(site))
            {
                roots.add(site);
            }
        }

        if (roots.isEmpty())
        {
            return Collections.emptySet();
        }
        return computeReachable(roots);
    }

    private static EscapeState stateOf(AllocationSite site, Set<AllocationSite> closure)
    {
        return closure.contains(site) ? EscapeState.GLOBAL_ESCAPE : EscapeState.NO_ESCAPE;
    }

    private Set<AllocationSite> computeReachable(Set<AllocationSite> roots)
    {
        Set<AllocationSite> reachable = new HashSet<>(roots);
        Deque<AllocationSite> worklist = new ArrayDeque<>(roots);

        while (!worklist.isEmpty())
        {
            AllocationSite current = worklist.poll();

            SimObject obj = heap.getObject(current);
            if (obj != null)
            {
                for (FieldKey field : obj.getFieldKeys())
                {
                    for (SimValue value : obj.getField(field))
                    {
                        for (AllocationSite target : value.getPointsTo())
                        {
                            if (reachable.add(target))
                            {
                                worklist.add(target);
                            }
                        }
                    }
                }
            }

            SimArray arr = heap.getArray(current);
            if (arr != null)
            {
                for (SimValue element : arr.getAllElements())
                {
                    for (AllocationSite target : element.getPointsTo())
                    {
                        if (reachable.add(target))
                        {
                            worklist.add(target);
                        }
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * Walks fields and array elements transitively from one site.
     * @param root the site to start from
     * @return the reachable sites, including the root
     */
    public Set<AllocationSite> getReachableFrom(AllocationSite root)
    {
        return computeReachable(Collections.singleton(root));
    }

    /**
     * Tests whether one site can be reached by following fields and array elements from another.
     * @param source the site to start from
     * @param target the site to look for
     * @return true if target is reachable from source
     */
    public boolean isReachableFrom(AllocationSite source, AllocationSite target)
    {
        return getReachableFrom(source).contains(target);
    }

    @Override
    public String toString()
    {
        int total = heap.getAllSites().size();
        int escaped = getEscaping().size();
        return "EscapeAnalyzer[total=" + total + ", escaped=" + escaped + "]";
    }
}
