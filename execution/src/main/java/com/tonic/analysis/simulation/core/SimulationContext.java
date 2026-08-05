package com.tonic.analysis.simulation.core;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

/**
 * Immutable configuration and shared resources for simulation; the with* methods return
 * modified copies.
 */
public final class SimulationContext
{

    private final ClassPool classPool;
    private final CallGraph callGraph;
    private final SimulationMode mode;
    private final int maxCallDepth;
    private final boolean trackHeap;
    private final boolean trackValues;
    private final boolean trackStackOperations;

    private SimulationContext(Builder builder)
    {
        this.classPool = builder.classPool;
        this.callGraph = builder.callGraph;
        this.mode = builder.mode;
        this.maxCallDepth = builder.maxCallDepth;
        this.trackHeap = builder.trackHeap;
        this.trackValues = builder.trackValues;
        this.trackStackOperations = builder.trackStackOperations;
    }

    // Factory Methods

    /**
     * Creates a context for simulating a single method, backed by the default class pool.
     * @param method the method to simulate
     * @return a new context with default settings
     */
    public static SimulationContext forMethod(MethodEntry method)
    {
        ClassPool pool = ClassPool.getDefault();
        return new Builder()
            .classPool(pool)
            .build();
    }

    /**
     * Creates a context for simulating methods in a class, backed by the default class pool.
     * @param classFile the class whose methods will be simulated
     * @return a new context with default settings
     */
    public static SimulationContext forClass(ClassFile classFile)
    {
        ClassPool pool = ClassPool.getDefault();
        return new Builder()
            .classPool(pool)
            .build();
    }

    /**
     * Creates a context for simulating across a class pool.
     * @param pool the class pool used to resolve classes and callees
     * @return a new context with default settings
     */
    public static SimulationContext forPool(ClassPool pool)
    {
        return new Builder()
            .classPool(pool)
            .build();
    }

    /**
     * Creates a context with default settings and no class pool.
     * @return a new default context
     */
    public static SimulationContext defaults()
    {
        return new Builder().build();
    }

    // Builder-style Configuration

    /**
     * Sets the simulation mode.
     * @param mode the mode to use
     * @return a new context with the mode applied
     */
    public SimulationContext withMode(SimulationMode mode)
    {
        return toBuilder().mode(mode).build();
    }

    /**
     * Sets the maximum call depth for inter-procedural simulation.
     * @param depth the depth limit; 0 means intra-procedural only (the default)
     * @return a new context with the depth applied
     */
    public SimulationContext withMaxCallDepth(int depth)
    {
        return toBuilder().maxCallDepth(depth).build();
    }

    /**
     * Enables or disables heap allocation tracking.
     * @param enabled whether to track heap allocations
     * @return a new context with the setting applied
     */
    public SimulationContext withHeapTracking(boolean enabled)
    {
        return toBuilder().trackHeap(enabled).build();
    }

    /**
     * Enables or disables value flow tracking.
     * @param enabled whether to track value flow
     * @return a new context with the setting applied
     */
    public SimulationContext withValueTracking(boolean enabled)
    {
        return toBuilder().trackValues(enabled).build();
    }

    /**
     * Enables or disables stack operation tracking.
     * @param enabled whether to track stack operations
     * @return a new context with the setting applied
     */
    public SimulationContext withStackOperationTracking(boolean enabled)
    {
        return toBuilder().trackStackOperations(enabled).build();
    }

    /**
     * Sets the call graph for inter-procedural resolution.
     * @param callGraph the call graph to use
     * @return a new context with the call graph applied
     */
    public SimulationContext withCallGraph(CallGraph callGraph)
    {
        return toBuilder().callGraph(callGraph).build();
    }

    /**
     * Sets the class pool used to resolve classes and callees.
     * @param classPool the class pool to use
     * @return a new context with the class pool applied
     */
    public SimulationContext withClassPool(ClassPool classPool)
    {
        return toBuilder().classPool(classPool).build();
    }

    // Getters

    /**
     * @return the class pool
     */
    public ClassPool getClassPool()
    {
        return classPool;
    }

    /**
     * @return the call graph
     */
    public CallGraph getCallGraph()
    {
        return callGraph;
    }

    /**
     * @return the simulation mode
     */
    public SimulationMode getMode()
    {
        return mode;
    }

    /**
     * @return the maximum inter-procedural call depth
     */
    public int getMaxCallDepth()
    {
        return maxCallDepth;
    }

    /**
     * @return whether heap allocation tracking is enabled
     */
    public boolean isTrackHeap()
    {
        return trackHeap;
    }

    /**
     * @return whether value flow tracking is enabled
     */
    public boolean isTrackValues()
    {
        return trackValues;
    }

    /**
     * @return whether stack operation tracking is enabled
     */
    public boolean isTrackStackOperations()
    {
        return trackStackOperations;
    }

    /**
     * @return true when inter-procedural simulation is enabled (max call depth above zero)
     */
    public boolean isInterProcedural()
    {
        return maxCallDepth > 0;
    }

    /**
     * @return true when instruction-level state tracking is enabled
     */
    public boolean isInstructionLevel()
    {
        return mode == SimulationMode.INSTRUCTION;
    }

    // Builder

    private Builder toBuilder()
    {
        return new Builder()
            .classPool(classPool)
            .callGraph(callGraph)
            .mode(mode)
            .maxCallDepth(maxCallDepth)
            .trackHeap(trackHeap)
            .trackValues(trackValues)
            .trackStackOperations(trackStackOperations);
    }

    /**
     * Mutable builder for SimulationContext instances.
     */
    public static class Builder
    {
        private ClassPool classPool;
        private CallGraph callGraph;
        private SimulationMode mode = SimulationMode.INSTRUCTION;
        private int maxCallDepth = 0;
        private boolean trackHeap = false;
        private boolean trackValues = false;
        private boolean trackStackOperations = true;

        /**
         * Sets the class pool used to resolve classes and callees.
         * @param classPool the class pool to use
         * @return this builder
         */
        public Builder classPool(ClassPool classPool)
        {
            this.classPool = classPool;
            return this;
        }

        /**
         * Sets the call graph for inter-procedural resolution.
         * @param callGraph the call graph to use
         * @return this builder
         */
        public Builder callGraph(CallGraph callGraph)
        {
            this.callGraph = callGraph;
            return this;
        }

        /**
         * Sets the simulation mode.
         * @param mode the mode to use
         * @return this builder
         */
        public Builder mode(SimulationMode mode)
        {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the maximum inter-procedural call depth.
         * @param maxCallDepth the depth limit; 0 means intra-procedural only
         * @return this builder
         */
        public Builder maxCallDepth(int maxCallDepth)
        {
            this.maxCallDepth = maxCallDepth;
            return this;
        }

        /**
         * Sets whether heap allocations are tracked.
         * @param trackHeap whether to track heap allocations
         * @return this builder
         */
        public Builder trackHeap(boolean trackHeap)
        {
            this.trackHeap = trackHeap;
            return this;
        }

        /**
         * Sets whether value flow is tracked.
         * @param trackValues whether to track value flow
         * @return this builder
         */
        public Builder trackValues(boolean trackValues)
        {
            this.trackValues = trackValues;
            return this;
        }

        /**
         * Sets whether stack operations are tracked.
         * @param trackStackOperations whether to track stack operations
         * @return this builder
         */
        public Builder trackStackOperations(boolean trackStackOperations)
        {
            this.trackStackOperations = trackStackOperations;
            return this;
        }

        /**
         * Builds the immutable context from the current settings.
         * @return a new SimulationContext
         */
        public SimulationContext build()
        {
            return new SimulationContext(this);
        }
    }

    @Override
    public String toString()
    {
        return "SimulationContext[" +
            "mode=" + mode +
            ", maxCallDepth=" + maxCallDepth +
            ", trackHeap=" + trackHeap +
            ", trackValues=" + trackValues +
            ", trackStackOps=" + trackStackOperations +
            "]";
    }
}
