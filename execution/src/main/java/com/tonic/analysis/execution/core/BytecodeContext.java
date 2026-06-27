package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.resolve.ClassResolver;

public final class BytecodeContext {

    private final ExecutionMode mode;
    private final HeapManager heapManager;
    private final ClassResolver classResolver;
    private final NativeRegistry nativeRegistry;
    private final int maxCallDepth;
    private final int maxInstructions;
    private final boolean trackStatistics;

    private BytecodeContext(Builder builder) {
        this.mode = builder.mode;
        this.heapManager = builder.heapManager;
        this.classResolver = builder.classResolver;
        this.nativeRegistry = builder.nativeRegistry;
        this.maxCallDepth = builder.maxCallDepth;
        this.maxInstructions = builder.maxInstructions;
        this.trackStatistics = builder.trackStatistics;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public HeapManager getHeapManager() {
        return heapManager;
    }

    public ClassResolver getClassResolver() {
        return classResolver;
    }

    public NativeRegistry getNativeRegistry() {
        return nativeRegistry;
    }

    public int getMaxCallDepth() {
        return maxCallDepth;
    }

    public int getMaxInstructions() {
        return maxInstructions;
    }

    public boolean isTrackStatistics() {
        return trackStatistics;
    }

    public static class Builder {
        private ExecutionMode mode = ExecutionMode.RECURSIVE;
        private HeapManager heapManager;
        private ClassResolver classResolver;
        private NativeRegistry nativeRegistry;
        private int maxCallDepth = 1000;
        private int maxInstructions = 10_000_000;
        private boolean trackStatistics = false;

        public Builder mode(ExecutionMode mode) {
            if (mode == null) {
                throw new IllegalArgumentException("Mode cannot be null");
            }
            this.mode = mode;
            return this;
        }

        public Builder heapManager(HeapManager heapManager) {
            this.heapManager = heapManager;
            return this;
        }

        public Builder classResolver(ClassResolver classResolver) {
            this.classResolver = classResolver;
            return this;
        }

        public Builder nativeRegistry(NativeRegistry nativeRegistry) {
            this.nativeRegistry = nativeRegistry;
            return this;
        }

        public Builder maxCallDepth(int depth) {
            if (depth <= 0) {
                throw new IllegalArgumentException("Max call depth must be positive: " + depth);
            }
            this.maxCallDepth = depth;
            return this;
        }

        public Builder maxInstructions(int limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("Max instructions must be positive: " + limit);
            }
            this.maxInstructions = limit;
            return this;
        }

        public Builder trackStatistics(boolean track) {
            this.trackStatistics = track;
            return this;
        }

        public BytecodeContext build() {
            if (heapManager == null) {
                throw new IllegalStateException("HeapManager is required");
            }
            if (classResolver == null) {
                throw new IllegalStateException("ClassResolver is required");
            }
            if (nativeRegistry == null) {
                nativeRegistry = new NativeRegistry();
                nativeRegistry.registerDefaults();
            }

            boolean useCompactStrings = classResolver.usesCompactStrings();
            heapManager.setUseCompactStrings(useCompactStrings);

            return new BytecodeContext(this);
        }
    }
}
