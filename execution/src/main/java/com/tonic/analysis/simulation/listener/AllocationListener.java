package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.ir.NewInstruction;
import com.tonic.analysis.ssa.type.IRType;

import java.util.*;

/**
 * Listener that tracks object and array allocations during simulation.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Total allocation count</li>
 *   <li>Allocations by type</li>
 *   <li>Array allocations separately</li>
 *   <li>Allocation sites</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * AllocationListener listener = new AllocationListener();
 * engine.addListener(listener);
 * engine.simulate(method);
 *
 * System.out.println("Total allocations: " + listener.getTotalCount());
 * for (var entry : listener.getAllocationsByType().entrySet()) {
 *     System.out.println(entry.getKey() + ": " + entry.getValue());
 * }
 * </pre>
 */
public class AllocationListener extends AbstractListener {

    private int objectAllocationCount;
    private int arrayAllocationCount;
    private final Map<String, Integer> allocationsByType;
    private final List<AllocationSite> allocationSites;
    private boolean trackSites;

    public AllocationListener() {
        this(true);
    }

    public AllocationListener(boolean trackSites) {
        this.trackSites = trackSites;
        this.allocationsByType = new HashMap<>();
        this.allocationSites = new ArrayList<>();
    }

    @Override
    public void onSimulationStart(IRMethod method) {
        super.onSimulationStart(method);
        objectAllocationCount = 0;
        arrayAllocationCount = 0;
        allocationsByType.clear();
        allocationSites.clear();
    }

    @Override
    public void onAllocation(NewInstruction instr, SimulationState state) {
        objectAllocationCount++;

        IRType type = instr.getResultType();
        String typeName = type != null ? type.getDescriptor() : "unknown";
        allocationsByType.merge(typeName, 1, Integer::sum);

        if (trackSites) {
            allocationSites.add(new AllocationSite(instr, typeName, false, state.stackDepth()));
        }
    }

    @Override
    public void onArrayAllocation(NewArrayInstruction instr, SimulationState state) {
        arrayAllocationCount++;

        IRType elementType = instr.getElementType();
        String typeName = (elementType != null ? elementType.getDescriptor() : "?") + "[]";
        if (instr.isMultiDimensional()) {
            typeName += "(" + instr.getDimensions().size() + "D)";
        }
        allocationsByType.merge(typeName, 1, Integer::sum);

        if (trackSites) {
            allocationSites.add(new AllocationSite(instr, typeName, true, state.stackDepth()));
        }
    }

    /**
     * Gets the total number of object allocations (NEW).
     */
    public int getObjectAllocationCount() {
        return objectAllocationCount;
    }

    /**
     * Gets the total number of array allocations (NEWARRAY, etc.).
     */
    public int getArrayAllocationCount() {
        return arrayAllocationCount;
    }

    /**
     * Gets the total number of all allocations.
     */
    public int getTotalCount() {
        return objectAllocationCount + arrayAllocationCount;
    }

    /**
     * Gets allocation counts by type.
     */
    public Map<String, Integer> getAllocationsByType() {
        return Collections.unmodifiableMap(allocationsByType);
    }

    /**
     * Gets allocation count for a specific type.
     */
    public int getCountForType(String typeName) {
        return allocationsByType.getOrDefault(typeName, 0);
    }

    /**
     * Gets all allocation sites.
     */
    public List<AllocationSite> getAllocationSites() {
        return Collections.unmodifiableList(allocationSites);
    }

    /**
     * Gets allocation sites for a specific type.
     */
    public List<AllocationSite> getAllocationsOf(String typeName) {
        List<AllocationSite> result = new ArrayList<>();
        for (AllocationSite site : allocationSites) {
            if (typeName.equals(site.getTypeName())) {
                result.add(site);
            }
        }
        return result;
    }

    /**
     * Gets the number of distinct types allocated.
     */
    public int getDistinctTypeCount() {
        return allocationsByType.size();
    }

    /**
     * Represents an allocation site.
     */
    public static class AllocationSite {
        private final Object instruction; // NewInstruction or NewArrayInstruction
        private final String typeName;
        private final boolean isArray;
        private final int stackDepthAtAllocation;

        public AllocationSite(Object instruction, String typeName, boolean isArray, int stackDepth) {
            this.instruction = instruction;
            this.typeName = typeName;
            this.isArray = isArray;
            this.stackDepthAtAllocation = stackDepth;
        }

        public Object getInstruction() {
            return instruction;
        }

        public String getTypeName() {
            return typeName;
        }

        public boolean isArray() {
            return isArray;
        }

        public int getStackDepthAtAllocation() {
            return stackDepthAtAllocation;
        }

        @Override
        public String toString() {
            return (isArray ? "NEWARRAY " : "NEW ") + typeName;
        }
    }

    @Override
    public String toString() {
        return "AllocationListener[objects=" + objectAllocationCount +
            ", arrays=" + arrayAllocationCount +
            ", types=" + allocationsByType.size() + "]";
    }
}
