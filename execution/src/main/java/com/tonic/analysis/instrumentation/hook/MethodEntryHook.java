package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method entry instrumentation.
 * Hooks are called at the beginning of methods, before any user code executes.
 */
public class MethodEntryHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passThis;
    private final boolean passMethodName;
    private final boolean passClassName;
    private final boolean passAllParameters;
    private final List<Integer> parameterIndices;

    private MethodEntryHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.passThis = builder.passThis;
        this.passMethodName = builder.passMethodName;
        this.passClassName = builder.passClassName;
        this.passAllParameters = builder.passAllParameters;
        this.parameterIndices = builder.parameterIndices;
    }

    /**
     * @return the hook descriptor
     */
    public HookDescriptor getHookDescriptor()
    {
        return hookDescriptor;
    }

    /**
     * @return the filters
     */
    public List<InstrumentationFilter> getFilters()
    {
        return filters;
    }

    /**
     * @return whether enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * @return the priority
     */
    public int getPriority()
    {
        return priority;
    }

    /**
     * @return true if the receiver, null for static methods, is passed to the hook
     */
    public boolean isPassThis()
    {
        return passThis;
    }

    /**
     * @return true if the instrumented method's name is passed to the hook
     */
    public boolean isPassMethodName()
    {
        return passMethodName;
    }

    /**
     * @return true if the instrumented class's name is passed to the hook
     */
    public boolean isPassClassName()
    {
        return passClassName;
    }

    /**
     * @return true if every parameter is passed to the hook as one Object array
     */
    public boolean isPassAllParameters()
    {
        return passAllParameters;
    }

    /**
     * @return the individual parameter positions to pass, boxed when primitive
     */
    public List<Integer> getParameterIndices()
    {
        return parameterIndices;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        return InstrumentationTarget.METHOD_ENTRY;
    }

    /**
     * Creates a hook that calls a static method with no filters and no extra arguments.
     * @param hookOwner the internal name of the class declaring the hook method
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the configured hook
     */
    public static MethodEntryHook simple(String hookOwner, String hookName, String hookDescriptor)
    {
        return MethodEntryHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * @return a new builder with default settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Fluent builder for a method entry hook; every setting has a default except the hook descriptor.
     */
    public static class Builder
    {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private boolean passThis = false;
        private boolean passMethodName = false;
        private boolean passClassName = false;
        private boolean passAllParameters = false;
        private List<Integer> parameterIndices = new ArrayList<>();

        /**
         * Sets the static method the hook calls.
         * @param hookDescriptor the target hook method
         * @return this builder
         */
        public Builder hookDescriptor(HookDescriptor hookDescriptor)
        {
            this.hookDescriptor = hookDescriptor;
            return this;
        }

        /**
         * Sets the filters deciding which methods get instrumented.
         * @param filters the filter list, replacing the current one
         * @return this builder
         */
        public Builder filters(List<InstrumentationFilter> filters)
        {
            this.filters = filters;
            return this;
        }

        /**
         * Sets whether the hook is applied at all; defaults to true.
         * @param enabled true to apply the hook
         * @return this builder
         */
        public Builder enabled(boolean enabled)
        {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the ordering priority among hooks; defaults to 100.
         * @param priority the priority value
         * @return this builder
         */
        public Builder priority(int priority)
        {
            this.priority = priority;
            return this;
        }

        /**
         * Sets whether the receiver is passed to the hook, null for static methods.
         * @param passThis true to pass the receiver
         * @return this builder
         */
        public Builder passThis(boolean passThis)
        {
            this.passThis = passThis;
            return this;
        }

        /**
         * Sets whether the instrumented method's name is passed to the hook.
         * @param passMethodName true to pass the method name
         * @return this builder
         */
        public Builder passMethodName(boolean passMethodName)
        {
            this.passMethodName = passMethodName;
            return this;
        }

        /**
         * Sets whether the instrumented class's name is passed to the hook.
         * @param passClassName true to pass the class name
         * @return this builder
         */
        public Builder passClassName(boolean passClassName)
        {
            this.passClassName = passClassName;
            return this;
        }

        /**
         * Sets whether all parameters are passed as one Object array.
         * @param passAllParameters true to pass every parameter
         * @return this builder
         */
        public Builder passAllParameters(boolean passAllParameters)
        {
            this.passAllParameters = passAllParameters;
            return this;
        }

        /**
         * Sets the individual parameter positions to pass, boxed when primitive.
         * @param parameterIndices the parameter indices, replacing the current list
         * @return this builder
         */
        public Builder parameterIndices(List<Integer> parameterIndices)
        {
            this.parameterIndices = parameterIndices;
            return this;
        }

        /**
         * @return a hook with the configured settings
         */
        public MethodEntryHook build()
        {
            return new MethodEntryHook(this);
        }
    }
}
