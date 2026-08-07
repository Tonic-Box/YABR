package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for array load instrumentation.
 */
public class ArrayLoadHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passArray;
    private final boolean passIndex;
    private final boolean passValue;
    private final String arrayTypeFilter;

    private ArrayLoadHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.passArray = builder.passArray;
        this.passIndex = builder.passIndex;
        this.passValue = builder.passValue;
        this.arrayTypeFilter = builder.arrayTypeFilter;
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
     * @return whether the array reference is passed to the hook
     */
    public boolean isPassArray()
    {
        return passArray;
    }

    /**
     * @return whether the array index is passed to the hook
     */
    public boolean isPassIndex()
    {
        return passIndex;
    }

    /**
     * @return whether the loaded value (boxed if primitive) is passed to the hook
     */
    public boolean isPassValue()
    {
        return passValue;
    }

    /**
     * @return the array element type filter (e.g. {@code "[Ljava/lang/Object;"}), or null for no filter
     */
    public String getArrayTypeFilter()
    {
        return arrayTypeFilter;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        return InstrumentationTarget.ARRAY_LOAD;
    }

    /**
     * Creates an array load hook that invokes the given static hook method.
     * @param hookOwner the hook method's owning class (internal name)
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the new hook
     */
    public static ArrayLoadHook simple(String hookOwner, String hookName, String hookDescriptor)
    {
        return ArrayLoadHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * Creates a builder for an array load hook.
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ArrayLoadHook} instances.
     */
    public static class Builder
    {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private boolean passArray = false;
        private boolean passIndex = false;
        private boolean passValue = false;
        private String arrayTypeFilter;

        /**
         * Sets the hook method invoked at each instrumented array load.
         * @param hookDescriptor the hook method descriptor
         * @return this builder
         */
        public Builder hookDescriptor(HookDescriptor hookDescriptor)
        {
            this.hookDescriptor = hookDescriptor;
            return this;
        }

        /**
         * Sets the filters that restrict which code is instrumented.
         * @param filters the instrumentation filters
         * @return this builder
         */
        public Builder filters(List<InstrumentationFilter> filters)
        {
            this.filters = filters;
            return this;
        }

        /**
         * Sets whether the hook is active.
         * @param enabled true to enable the hook
         * @return this builder
         */
        public Builder enabled(boolean enabled)
        {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the ordering priority relative to other hooks.
         * @param priority the priority value
         * @return this builder
         */
        public Builder priority(int priority)
        {
            this.priority = priority;
            return this;
        }

        /**
         * Sets whether the array reference is passed to the hook.
         * @param passArray true to pass the array reference
         * @return this builder
         */
        public Builder passArray(boolean passArray)
        {
            this.passArray = passArray;
            return this;
        }

        /**
         * Sets whether the array index is passed to the hook.
         * @param passIndex true to pass the index
         * @return this builder
         */
        public Builder passIndex(boolean passIndex)
        {
            this.passIndex = passIndex;
            return this;
        }

        /**
         * Sets whether the loaded value is passed to the hook.
         * @param passValue true to pass the loaded value
         * @return this builder
         */
        public Builder passValue(boolean passValue)
        {
            this.passValue = passValue;
            return this;
        }

        /**
         * Sets the array element type filter; null instruments all array types.
         * @param arrayTypeFilter the array type descriptor to match
         * @return this builder
         */
        public Builder arrayTypeFilter(String arrayTypeFilter)
        {
            this.arrayTypeFilter = arrayTypeFilter;
            return this;
        }

        /**
         * Builds the configured array load hook.
         * @return the new hook
         */
        public ArrayLoadHook build()
        {
            return new ArrayLoadHook(this);
        }
    }
}
