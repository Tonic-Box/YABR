package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;

import java.util.*;

/**
 * Read-only view over a simulated heap answering aliasing, nullness and
 * reachability questions about points-to sets.
 */
public final class PointsToQuery
{

    private final SimHeap heap;

    /**
     * Binds the query to a heap.
     * @param heap the heap to read
     * @throws NullPointerException if the heap is null
     */
    public PointsToQuery(SimHeap heap)
    {
        this.heap = Objects.requireNonNull(heap);
    }

    /**
     * @param ref the reference, may be null
     * @return the allocation sites it may denote, empty if null
     */
    public Set<AllocationSite> pointsTo(SimValue ref)
    {
        if (ref == null)
        {
            return Collections.emptySet();
        }
        return ref.getPointsTo();
    }

    /**
     * Tests whether a reference could point at a site.
     * @param ref the reference, may be null
     * @param site the allocation site, may be null
     * @return true if the site is in the points-to set
     */
    public boolean mayPointTo(SimValue ref, AllocationSite site)
    {
        if (ref == null || site == null)
        {
            return false;
        }
        return ref.getPointsTo().contains(site);
    }

    /**
     * Tests whether a reference points only at one site.
     * @param ref the reference, may be null
     * @param site the allocation site, may be null
     * @return true if the site is the sole target
     */
    public boolean mustPointTo(SimValue ref, AllocationSite site)
    {
        if (ref == null || site == null)
        {
            return false;
        }
        Set<AllocationSite> pts = ref.getPointsTo();
        return pts.size() == 1 && pts.contains(site);
    }

    /**
     * Tests whether two references could denote the same object; an empty
     * points-to set is treated as unknown and aliases anything.
     * @param ref1 the first reference, may be null
     * @param ref2 the second reference, may be null
     * @return true if they may alias
     */
    public boolean mayAlias(SimValue ref1, SimValue ref2)
    {
        if (ref1 == null || ref2 == null)
        {
            return false;
        }
        if (ref1.isDefinitelyNull() || ref2.isDefinitelyNull())
        {
            return ref1.isDefinitelyNull() && ref2.isDefinitelyNull();
        }

        Set<AllocationSite> pts1 = ref1.getPointsTo();
        Set<AllocationSite> pts2 = ref2.getPointsTo();

        if (pts1.isEmpty() || pts2.isEmpty())
        {
            return true;
        }

        for (AllocationSite site : pts1)
        {
            if (pts2.contains(site))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether two references necessarily denote the same object - both
     * known null, or both pinned to the same single allocation site.
     * @param ref1 the first reference, may be null
     * @param ref2 the second reference, may be null
     * @return true if they must alias
     */
    public boolean mustAlias(SimValue ref1, SimValue ref2)
    {
        if (ref1 == null || ref2 == null)
        {
            return false;
        }
        if (ref1.isDefinitelyNull() && ref2.isDefinitelyNull())
        {
            return true;
        }

        Set<AllocationSite> pts1 = ref1.getPointsTo();
        Set<AllocationSite> pts2 = ref2.getPointsTo();

        return pts1.size() == 1 && pts2.size() == 1 &&
               pts1.iterator().next().equals(pts2.iterator().next());
    }

    /**
     * @param ref the reference, may be null
     * @return true if the value could be null; a null reference counts as unknown
     */
    public boolean mayBeNull(SimValue ref)
    {
        return ref == null || ref.mayBeNull();
    }

    /**
     * @param ref the reference, may be null
     * @return true if the value is known null; false for a null reference
     */
    public boolean isDefinitelyNull(SimValue ref)
    {
        return ref != null && ref.isDefinitelyNull();
    }

    /**
     * @param ref the reference, may be null
     * @return true if the value is known non-null; false for a null reference
     */
    public boolean isDefinitelyNotNull(SimValue ref)
    {
        return ref != null && ref.isDefinitelyNotNull();
    }

    /**
     * Walks fields and array elements from a root, following every allocation
     * site each value may point to.
     * @param root the starting reference, may be null
     * @return the reachable values, including the root
     */
    public Set<SimValue> reachableFrom(SimValue root)
    {
        if (root == null)
        {
            return Collections.emptySet();
        }

        Set<SimValue> visited = new HashSet<>();
        Queue<SimValue> worklist = new LinkedList<>();
        worklist.add(root);
        visited.add(root);

        while (!worklist.isEmpty())
        {
            SimValue current = worklist.poll();

            for (AllocationSite site : current.getPointsTo())
            {
                collectReachableFromSite(site, visited, worklist);
            }
        }

        return visited;
    }

    private void collectReachableFromSite(AllocationSite site, Set<SimValue> visited, Queue<SimValue> worklist)
    {
        SimObject obj = heap.getObject(site);
        if (obj != null)
        {
            for (FieldKey field : obj.getFieldKeys())
            {
                for (SimValue value : obj.getField(field))
                {
                    if (visited.add(value))
                    {
                        worklist.add(value);
                    }
                }
            }
        }

        SimArray arr = heap.getArray(site);
        if (arr != null)
        {
            for (SimValue element : arr.getAllElements())
            {
                if (visited.add(element))
                {
                    worklist.add(element);
                }
            }
        }
    }

    /**
     * Collects the allocation sites of everything transitively reachable from a
     * root.
     * @param root the starting reference, may be null
     * @return the reachable sites
     */
    public Set<AllocationSite> reachableSitesFrom(SimValue root)
    {
        Set<AllocationSite> sites = new HashSet<>();
        for (SimValue value : reachableFrom(root))
        {
            sites.addAll(value.getPointsTo());
        }
        return sites;
    }

    /**
     * Collects a field's values across every object a reference may point to.
     * @param objectRef the object reference
     * @param field the field to read
     * @return the union of the field values
     */
    public Set<SimValue> getFieldValues(SimValue objectRef, FieldKey field)
    {
        Set<SimValue> result = new HashSet<>();
        for (AllocationSite site : objectRef.getPointsTo())
        {
            result.addAll(heap.getField(site, field));
        }
        return result;
    }

    /**
     * Collects the elements of every array a reference may point to.
     * @param arrayRef the array reference
     * @return the union of the element values
     */
    public Set<SimValue> getArrayElements(SimValue arrayRef)
    {
        Set<SimValue> result = new HashSet<>();
        for (AllocationSite site : arrayRef.getPointsTo())
        {
            SimArray arr = heap.getArray(site);
            if (arr != null)
            {
                result.addAll(arr.getAllElements());
            }
        }
        return result;
    }

    /**
     * @param ref the reference, may be null
     * @return the number of allocation sites it may point to, 0 if null
     */
    public int getPointsToSetSize(SimValue ref)
    {
        return ref == null ? 0 : ref.getPointsTo().size();
    }

    /**
     * Tests whether a reference resolves to exactly one allocation site.
     * @param ref the reference, may be null
     * @return true if the points-to set holds one site
     */
    public boolean isSingleton(SimValue ref)
    {
        return ref != null && ref.getPointsTo().size() == 1;
    }

    @Override
    public String toString()
    {
        return "PointsToQuery[heap=" + heap + "]";
    }
}
