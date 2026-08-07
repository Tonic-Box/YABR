package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.AllocationListener;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of the allocations counted during a simulation.
 */
public class AllocationMetrics
{

    private final int objectCount;
    private final int arrayCount;
    private final Map<String, Integer> allocationsByType;

    private AllocationMetrics(int objectCount, int arrayCount, Map<String, Integer> allocationsByType)
    {
        this.objectCount = objectCount;
        this.arrayCount = arrayCount;
        this.allocationsByType = Map.copyOf(allocationsByType);
    }

    /**
     * Snapshots the counts a listener collected during simulation.
     *
     * @param listener the listener to read counts from
     * @return the metrics snapshot
     */
    public static AllocationMetrics from(AllocationListener listener)
    {
        return new AllocationMetrics(
            listener.getObjectAllocationCount(),
            listener.getArrayAllocationCount(),
            listener.getAllocationsByType()
        );
    }

    /**
     * Creates metrics with no recorded allocations.
     *
     * @return the empty metrics
     */
    public static AllocationMetrics empty()
    {
        return new AllocationMetrics(0, 0, Collections.emptyMap());
    }

    /**
     * @return the number of object allocations
     */
    public int getObjectCount()
    {
        return objectCount;
    }

    /**
     * @return the number of array allocations
     */
    public int getArrayCount()
    {
        return arrayCount;
    }

    /**
     * @return the object and array allocation counts summed
     */
    public int getTotalCount()
    {
        return objectCount + arrayCount;
    }

    /**
     * @return an unmodifiable map of type name to allocation count
     */
    public Map<String, Integer> getAllocationsByType()
    {
        return allocationsByType;
    }

    /**
     * Looks up one type's tally.
     *
     * @param typeName the allocated type to look up
     * @return the count for that type, or 0 if it was never allocated
     */
    public int getCountForType(String typeName)
    {
        return allocationsByType.getOrDefault(typeName, 0);
    }

    /**
     * @return the number of distinct types allocated
     */
    public int getDistinctTypeCount()
    {
        return allocationsByType.size();
    }

    /**
     * @return true if any object or array was allocated
     */
    public boolean hasAllocations()
    {
        return objectCount > 0 || arrayCount > 0;
    }

    /**
     * Scans the per-type tally for the highest count.
     *
     * @return the type name allocated most often, or null if nothing was allocated
     */
    public String getMostAllocatedType()
    {
        String maxType = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : allocationsByType.entrySet())
        {
            if (entry.getValue() > maxCount)
            {
                maxCount = entry.getValue();
                maxType = entry.getKey();
            }
        }
        return maxType;
    }

    /**
     * Adds the counts and per-type tallies of both metrics.
     *
     * @param other the metrics to add
     * @return a new metrics holding the summed counts
     */
    public AllocationMetrics combine(AllocationMetrics other)
    {
        Map<String, Integer> combined = new HashMap<>(this.allocationsByType);
        for (Map.Entry<String, Integer> entry : other.allocationsByType.entrySet())
        {
            combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return new AllocationMetrics(
            this.objectCount + other.objectCount,
            this.arrayCount + other.arrayCount,
            combined
        );
    }

    @Override
    public String toString()
    {
        return "AllocationMetrics[objects=" + objectCount +
            ", arrays=" + arrayCount +
            ", types=" + allocationsByType.size() + "]";
    }
}
