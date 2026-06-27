package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.AllocationListener;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Metrics container for allocation operations.
 *
 * <p>This class provides a clean interface to allocation statistics
 * collected during simulation.
 */
public class AllocationMetrics {

    private final int objectCount;
    private final int arrayCount;
    private final Map<String, Integer> allocationsByType;

    private AllocationMetrics(int objectCount, int arrayCount, Map<String, Integer> allocationsByType) {
        this.objectCount = objectCount;
        this.arrayCount = arrayCount;
        this.allocationsByType = Collections.unmodifiableMap(new HashMap<>(allocationsByType));
    }

    /**
     * Creates metrics from an AllocationListener.
     */
    public static AllocationMetrics from(AllocationListener listener) {
        return new AllocationMetrics(
            listener.getObjectAllocationCount(),
            listener.getArrayAllocationCount(),
            listener.getAllocationsByType()
        );
    }

    /**
     * Creates empty metrics.
     */
    public static AllocationMetrics empty() {
        return new AllocationMetrics(0, 0, Collections.emptyMap());
    }

    /**
     * Gets the number of object allocations.
     */
    public int getObjectCount() {
        return objectCount;
    }

    /**
     * Gets the number of array allocations.
     */
    public int getArrayCount() {
        return arrayCount;
    }

    /**
     * Gets the total allocation count.
     */
    public int getTotalCount() {
        return objectCount + arrayCount;
    }

    /**
     * Gets allocation counts by type.
     */
    public Map<String, Integer> getAllocationsByType() {
        return allocationsByType;
    }

    /**
     * Gets the allocation count for a specific type.
     */
    public int getCountForType(String typeName) {
        return allocationsByType.getOrDefault(typeName, 0);
    }

    /**
     * Gets the number of distinct types allocated.
     */
    public int getDistinctTypeCount() {
        return allocationsByType.size();
    }

    /**
     * Returns true if any allocations occurred.
     */
    public boolean hasAllocations() {
        return objectCount > 0 || arrayCount > 0;
    }

    /**
     * Gets the most allocated type.
     */
    public String getMostAllocatedType() {
        String maxType = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : allocationsByType.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxType = entry.getKey();
            }
        }
        return maxType;
    }

    /**
     * Combines this metrics with another.
     */
    public AllocationMetrics combine(AllocationMetrics other) {
        Map<String, Integer> combined = new HashMap<>(this.allocationsByType);
        for (Map.Entry<String, Integer> entry : other.allocationsByType.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return new AllocationMetrics(
            this.objectCount + other.objectCount,
            this.arrayCount + other.arrayCount,
            combined
        );
    }

    @Override
    public String toString() {
        return "AllocationMetrics[objects=" + objectCount +
            ", arrays=" + arrayCount +
            ", types=" + allocationsByType.size() + "]";
    }
}
