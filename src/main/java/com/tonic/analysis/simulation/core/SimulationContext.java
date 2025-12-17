package com.tonic.analysis.simulation.core;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;

/**
 * Configuration and shared resources for simulation.
 *
 * <p>SimulationContext is immutable. Use the builder-style methods to create
 * modified configurations.
 *
 * <p>Example usage:
 * <pre>
 * SimulationContext ctx = SimulationContext.forMethod(method)
 *     .withMode(SimulationMode.INSTRUCTION)
 *     .withMaxCallDepth(3)
 *     .withValueTracking(true);
 * </pre>
 */
public final class SimulationContext {

    private final ClassPool classPool;
    private final CallGraph callGraph;
    private final SimulationMode mode;
    private final int maxCallDepth;
    private final boolean trackHeap;
    private final boolean trackValues;
    private final boolean trackStackOperations;

    private SimulationContext(Builder builder) {
        this.classPool = builder.classPool;
        this.callGraph = builder.callGraph;
        this.mode = builder.mode;
        this.maxCallDepth = builder.maxCallDepth;
        this.trackHeap = builder.trackHeap;
        this.trackValues = builder.trackValues;
        this.trackStackOperations = builder.trackStackOperations;
    }

    // ========== Factory Methods ==========

    /**
     * Create a context for simulating a single method.
     */
    public static SimulationContext forMethod(MethodEntry method) {
        ClassPool pool = ClassPool.getDefault();
        return new Builder()
            .classPool(pool)
            .build();
    }

    /**
     * Create a context for simulating methods in a class.
     */
    public static SimulationContext forClass(ClassFile classFile) {
        ClassPool pool = ClassPool.getDefault();
        return new Builder()
            .classPool(pool)
            .build();
    }

    /**
     * Create a context for simulating across a class pool.
     */
    public static SimulationContext forPool(ClassPool pool) {
        return new Builder()
            .classPool(pool)
            .build();
    }

    /**
     * Create a default context.
     */
    public static SimulationContext defaults() {
        return new Builder().build();
    }

    // ========== Builder-style Configuration ==========

    /**
     * Set the simulation mode.
     */
    public SimulationContext withMode(SimulationMode mode) {
        return toBuilder().mode(mode).build();
    }

    /**
     * Set the maximum call depth for inter-procedural simulation.
     * 0 means intra-procedural only (default).
     */
    public SimulationContext withMaxCallDepth(int depth) {
        return toBuilder().maxCallDepth(depth).build();
    }

    /**
     * Enable or disable heap allocation tracking.
     */
    public SimulationContext withHeapTracking(boolean enabled) {
        return toBuilder().trackHeap(enabled).build();
    }

    /**
     * Enable or disable value flow tracking.
     */
    public SimulationContext withValueTracking(boolean enabled) {
        return toBuilder().trackValues(enabled).build();
    }

    /**
     * Enable or disable stack operation tracking.
     */
    public SimulationContext withStackOperationTracking(boolean enabled) {
        return toBuilder().trackStackOperations(enabled).build();
    }

    /**
     * Set the call graph for inter-procedural resolution.
     */
    public SimulationContext withCallGraph(CallGraph callGraph) {
        return toBuilder().callGraph(callGraph).build();
    }

    /**
     * Set the class pool.
     */
    public SimulationContext withClassPool(ClassPool classPool) {
        return toBuilder().classPool(classPool).build();
    }

    // ========== Getters ==========

    public ClassPool getClassPool() {
        return classPool;
    }

    public CallGraph getCallGraph() {
        return callGraph;
    }

    public SimulationMode getMode() {
        return mode;
    }

    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    public boolean isTrackHeap() {
        return trackHeap;
    }

    public boolean isTrackValues() {
        return trackValues;
    }

    public boolean isTrackStackOperations() {
        return trackStackOperations;
    }

    /**
     * Returns true if inter-procedural simulation is enabled.
     */
    public boolean isInterProcedural() {
        return maxCallDepth > 0;
    }

    /**
     * Returns true if instruction-level tracking is enabled.
     */
    public boolean isInstructionLevel() {
        return mode == SimulationMode.INSTRUCTION;
    }

    // ========== Builder ==========

    private Builder toBuilder() {
        return new Builder()
            .classPool(classPool)
            .callGraph(callGraph)
            .mode(mode)
            .maxCallDepth(maxCallDepth)
            .trackHeap(trackHeap)
            .trackValues(trackValues)
            .trackStackOperations(trackStackOperations);
    }

    public static class Builder {
        private ClassPool classPool;
        private CallGraph callGraph;
        private SimulationMode mode = SimulationMode.INSTRUCTION;
        private int maxCallDepth = 0;
        private boolean trackHeap = false;
        private boolean trackValues = false;
        private boolean trackStackOperations = true;

        public Builder classPool(ClassPool classPool) {
            this.classPool = classPool;
            return this;
        }

        public Builder callGraph(CallGraph callGraph) {
            this.callGraph = callGraph;
            return this;
        }

        public Builder mode(SimulationMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder maxCallDepth(int maxCallDepth) {
            this.maxCallDepth = maxCallDepth;
            return this;
        }

        public Builder trackHeap(boolean trackHeap) {
            this.trackHeap = trackHeap;
            return this;
        }

        public Builder trackValues(boolean trackValues) {
            this.trackValues = trackValues;
            return this;
        }

        public Builder trackStackOperations(boolean trackStackOperations) {
            this.trackStackOperations = trackStackOperations;
            return this;
        }

        public SimulationContext build() {
            return new SimulationContext(this);
        }
    }

    @Override
    public String toString() {
        return "SimulationContext[" +
            "mode=" + mode +
            ", maxCallDepth=" + maxCallDepth +
            ", trackHeap=" + trackHeap +
            ", trackValues=" + trackValues +
            ", trackStackOps=" + trackStackOperations +
            "]";
    }
}
