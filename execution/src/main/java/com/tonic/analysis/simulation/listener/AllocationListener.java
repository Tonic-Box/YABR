package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.NewArrayInstruction;
import com.tonic.analysis.ssa.ir.NewInstruction;
import com.tonic.analysis.ssa.type.IRType;

import java.util.*;

/**
 * Simulation listener that counts object and array allocations by type, optionally recording each
 * allocation site. Counters reset at each simulation start.
 */
public class AllocationListener extends AbstractListener
{

    private int objectAllocationCount;
    private int arrayAllocationCount;
    private final Map<String, Integer> allocationsByType;
    private final List<AllocationSite> allocationSites;
    private boolean trackSites;

    /**
     * Creates a listener that also records allocation sites.
     */
    public AllocationListener()
    {
        this(true);
    }

    /**
     * Creates a listener with all counters at zero.
     *
     * @param trackSites whether to record each allocation site as well as counting
     */
    public AllocationListener(boolean trackSites)
    {
        this.trackSites = trackSites;
        this.allocationsByType = new HashMap<>();
        this.allocationSites = new ArrayList<>();
    }

    @Override
    public void onSimulationStart(IRMethod method)
    {
        super.onSimulationStart(method);
        objectAllocationCount = 0;
        arrayAllocationCount = 0;
        allocationsByType.clear();
        allocationSites.clear();
    }

    @Override
    public void onAllocation(NewInstruction instr, SimulationState state)
    {
        objectAllocationCount++;

        IRType type = instr.getResultType();
        String typeName = type != null ? type.getDescriptor() : "unknown";
        allocationsByType.merge(typeName, 1, Integer::sum);

        if (trackSites)
        {
            allocationSites.add(new AllocationSite(instr, typeName, false, state.stackDepth()));
        }
    }

    @Override
    public void onArrayAllocation(NewArrayInstruction instr, SimulationState state)
    {
        arrayAllocationCount++;

        IRType elementType = instr.getElementType();
        String typeName = (elementType != null ? elementType.getDescriptor() : "?") + "[]";
        if (instr.isMultiDimensional())
        {
            typeName += "(" + instr.getDimensions().size() + "D)";
        }
        allocationsByType.merge(typeName, 1, Integer::sum);

        if (trackSites)
        {
            allocationSites.add(new AllocationSite(instr, typeName, true, state.stackDepth()));
        }
    }

    /**
     * @return the number of NEW allocations seen
     */
    public int getObjectAllocationCount()
    {
        return objectAllocationCount;
    }

    /**
     * @return the number of array allocations seen
     */
    public int getArrayAllocationCount()
    {
        return arrayAllocationCount;
    }

    /**
     * @return the object and array allocation counts summed
     */
    public int getTotalCount()
    {
        return objectAllocationCount + arrayAllocationCount;
    }

    /**
     * @return an unmodifiable view of the per-type allocation counts
     */
    public Map<String, Integer> getAllocationsByType()
    {
        return Collections.unmodifiableMap(allocationsByType);
    }

    /**
     * Looks up how many times one type was allocated.
     *
     * @param typeName type key as recorded, e.g. a descriptor or a descriptor with "[]" appended
     * @return the allocation count, or 0 if that type was never allocated
     */
    public int getCountForType(String typeName)
    {
        return allocationsByType.getOrDefault(typeName, 0);
    }

    /**
     * @return an unmodifiable view of the recorded sites, empty unless site tracking is on
     */
    public List<AllocationSite> getAllocationSites()
    {
        return Collections.unmodifiableList(allocationSites);
    }

    /**
     * Filters the recorded sites to one type.
     *
     * @param typeName type key to match
     * @return a new list of the matching sites in allocation order
     */
    public List<AllocationSite> getAllocationsOf(String typeName)
    {
        List<AllocationSite> result = new ArrayList<>();
        for (AllocationSite site : allocationSites)
        {
            if (typeName.equals(site.getTypeName()))
            {
                result.add(site);
            }
        }
        return result;
    }

    /**
     * @return how many distinct types were allocated
     */
    public int getDistinctTypeCount()
    {
        return allocationsByType.size();
    }

    /**
     * One recorded allocation: the instruction, the type key, whether it was an array, and the
     * operand stack depth at the time.
     */
    public static class AllocationSite
    {
        private final Object instruction; // NewInstruction or NewArrayInstruction
        private final String typeName;
        private final boolean isArray;
        private final int stackDepthAtAllocation;

        public AllocationSite(Object instruction, String typeName, boolean isArray, int stackDepth)
        {
            this.instruction = instruction;
            this.typeName = typeName;
            this.isArray = isArray;
            this.stackDepthAtAllocation = stackDepth;
        }

        /**
         * @return the instruction
         */
        public Object getInstruction()
        {
            return instruction;
        }

        /**
         * @return the type name
         */
        public String getTypeName()
        {
            return typeName;
        }

        /**
         * @return whether array
         */
        public boolean isArray()
        {
            return isArray;
        }

        /**
         * @return the stack depth at allocation
         */
        public int getStackDepthAtAllocation()
        {
            return stackDepthAtAllocation;
        }

        @Override
        public String toString()
        {
            return (isArray ? "NEWARRAY " : "NEW ") + typeName;
        }
    }

    @Override
    public String toString()
    {
        return "AllocationListener[objects=" + objectAllocationCount +
            ", arrays=" + arrayAllocationCount +
            ", types=" + allocationsByType.size() + "]";
    }
}
