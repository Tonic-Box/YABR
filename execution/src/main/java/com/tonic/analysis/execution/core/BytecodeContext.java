package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.invoke.InvocationHandler;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.resolve.ClassResolver;

/**
 * Immutable configuration for a bytecode engine: execution mode, heap, resolution services, and
 * execution limits.
 */
public final class BytecodeContext
{

    private final ExecutionMode mode;
    private final HeapManager heapManager;
    private final ClassResolver classResolver;
    private final NativeRegistry nativeRegistry;
    private final InvocationHandler invocationHandler;
    private final int maxCallDepth;
    private final int maxInstructions;
    private final boolean trackStatistics;

    private BytecodeContext(Builder builder)
    {
        this.mode = builder.mode;
        this.heapManager = builder.heapManager;
        this.classResolver = builder.classResolver;
        this.nativeRegistry = builder.nativeRegistry;
        this.invocationHandler = builder.invocationHandler;
        this.maxCallDepth = builder.maxCallDepth;
        this.maxInstructions = builder.maxInstructions;
        this.trackStatistics = builder.trackStatistics;
    }

    /**
     * @return the mode
     */
    public ExecutionMode getMode()
    {
        return mode;
    }

    /**
     * @return the heap manager
     */
    public HeapManager getHeapManager()
    {
        return heapManager;
    }

    /**
     * @return the class resolver
     */
    public ClassResolver getClassResolver()
    {
        return classResolver;
    }

    /**
     * @return the native registry
     */
    public NativeRegistry getNativeRegistry()
    {
        return nativeRegistry;
    }

    /**
     * @return the custom invocation handler overriding the default per-mode handler, or
     *         {@code null} to use the default; only consulted in {@link ExecutionMode#RECURSIVE},
     *         where it lets a caller intercept every call (e.g. to stub the environment for
     *         differential execution)
     */
    public InvocationHandler getInvocationHandler()
    {
        return invocationHandler;
    }

    /**
     * @return the max call depth
     */
    public int getMaxCallDepth()
    {
        return maxCallDepth;
    }

    /**
     * @return the max instructions
     */
    public int getMaxInstructions()
    {
        return maxInstructions;
    }

    /**
     * @return whether track statistics
     */
    public boolean isTrackStatistics()
    {
        return trackStatistics;
    }

    /**
     * Builder for {@link BytecodeContext}; a heap manager and class resolver are required.
     */
    public static class Builder
    {
        private ExecutionMode mode = ExecutionMode.RECURSIVE;
        private HeapManager heapManager;
        private ClassResolver classResolver;
        private NativeRegistry nativeRegistry;
        private InvocationHandler invocationHandler;
        private int maxCallDepth = 1000;
        private int maxInstructions = 10_000_000;
        private boolean trackStatistics = false;

        /**
         * Sets how method invocations are executed.
         * @param mode the execution mode
         * @return this builder
         * @throws IllegalArgumentException if mode is null
         */
        public Builder mode(ExecutionMode mode)
        {
            if (mode == null)
            {
                throw new IllegalArgumentException("Mode cannot be null");
            }
            this.mode = mode;
            return this;
        }

        /**
         * Sets the heap manager that owns objects and arrays during execution.
         * @param heapManager the heap manager
         * @return this builder
         */
        public Builder heapManager(HeapManager heapManager)
        {
            this.heapManager = heapManager;
            return this;
        }

        /**
         * Sets the resolver used to look up classes and methods.
         * @param classResolver the class resolver
         * @return this builder
         */
        public Builder classResolver(ClassResolver classResolver)
        {
            this.classResolver = classResolver;
            return this;
        }

        /**
         * Sets the registry of native method handlers; a default-populated registry is created
         * if omitted.
         * @param nativeRegistry the native registry
         * @return this builder
         */
        public Builder nativeRegistry(NativeRegistry nativeRegistry)
        {
            this.nativeRegistry = nativeRegistry;
            return this;
        }

        /**
         * Overrides the default per-mode invocation handler.
         * @param invocationHandler the handler to use, or null for the mode default
         * @return this builder
         */
        public Builder invocationHandler(InvocationHandler invocationHandler)
        {
            this.invocationHandler = invocationHandler;
            return this;
        }

        /**
         * Sets the maximum call stack depth.
         * @param depth maximum number of nested frames
         * @return this builder
         * @throws IllegalArgumentException if depth is not positive
         */
        public Builder maxCallDepth(int depth)
        {
            if (depth <= 0)
            {
                throw new IllegalArgumentException("Max call depth must be positive: " + depth);
            }
            this.maxCallDepth = depth;
            return this;
        }

        /**
         * Sets the instruction budget after which execution aborts.
         * @param limit maximum number of instructions to execute
         * @return this builder
         * @throws IllegalArgumentException if limit is not positive
         */
        public Builder maxInstructions(int limit)
        {
            if (limit <= 0)
            {
                throw new IllegalArgumentException("Max instructions must be positive: " + limit);
            }
            this.maxInstructions = limit;
            return this;
        }

        /**
         * Controls whether execution statistics are tracked.
         * @param track true to track statistics
         * @return this builder
         */
        public Builder trackStatistics(boolean track)
        {
            this.trackStatistics = track;
            return this;
        }

        /**
         * Validates required components and creates the context, defaulting the native registry
         * and propagating the resolver's compact-strings setting to the heap.
         * @return the built context
         * @throws IllegalStateException if the heap manager or class resolver is missing
         */
        public BytecodeContext build()
        {
            if (heapManager == null)
            {
                throw new IllegalStateException("HeapManager is required");
            }
            if (classResolver == null)
            {
                throw new IllegalStateException("ClassResolver is required");
            }
            if (nativeRegistry == null)
            {
                nativeRegistry = new NativeRegistry();
                nativeRegistry.registerDefaults();
            }

            boolean useCompactStrings = classResolver.usesCompactStrings();
            heapManager.setUseCompactStrings(useCompactStrings);

            return new BytecodeContext(this);
        }
    }
}
