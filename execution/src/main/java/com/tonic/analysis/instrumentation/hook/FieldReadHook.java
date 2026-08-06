package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for field read instrumentation.
 */
public class FieldReadHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passOwner;
    private final boolean passFieldName;
    private final boolean passReadValue;
    private final boolean instrumentStatic;
    private final boolean instrumentInstance;

    private FieldReadHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.passOwner = builder.passOwner;
        this.passFieldName = builder.passFieldName;
        this.passReadValue = builder.passReadValue;
        this.instrumentStatic = builder.instrumentStatic;
        this.instrumentInstance = builder.instrumentInstance;
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
     * @return whether the field's owning object (null for static fields) is passed to the hook
     */
    public boolean isPassOwner()
    {
        return passOwner;
    }

    /**
     * @return whether the field name is passed to the hook
     */
    public boolean isPassFieldName()
    {
        return passFieldName;
    }

    /**
     * @return whether the value that was read (boxed if primitive) is passed to the hook
     */
    public boolean isPassReadValue()
    {
        return passReadValue;
    }

    /**
     * @return whether static field reads are instrumented
     */
    public boolean isInstrumentStatic()
    {
        return instrumentStatic;
    }

    /**
     * @return whether instance field reads are instrumented
     */
    public boolean isInstrumentInstance()
    {
        return instrumentInstance;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        return InstrumentationTarget.FIELD_READ;
    }

    /**
     * Creates a field read hook that invokes the given static hook method.
     * @param hookOwner the hook method's owning class (internal name)
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the new hook
     */
    public static FieldReadHook simple(String hookOwner, String hookName, String hookDescriptor)
    {
        return FieldReadHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * Creates a builder for a field read hook.
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Fluent builder for {@link FieldReadHook} instances.
     */
    public static class Builder
    {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private boolean passOwner = false;
        private boolean passFieldName = false;
        private boolean passReadValue = false;
        private boolean instrumentStatic = true;
        private boolean instrumentInstance = true;

        /**
         * Sets the hook method invoked at each instrumented field read.
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
         * Sets whether the field's owning object is passed to the hook.
         * @param passOwner true to pass the owning object
         * @return this builder
         */
        public Builder passOwner(boolean passOwner)
        {
            this.passOwner = passOwner;
            return this;
        }

        /**
         * Sets whether the field name is passed to the hook.
         * @param passFieldName true to pass the field name
         * @return this builder
         */
        public Builder passFieldName(boolean passFieldName)
        {
            this.passFieldName = passFieldName;
            return this;
        }

        /**
         * Sets whether the value that was read is passed to the hook.
         * @param passReadValue true to pass the read value
         * @return this builder
         */
        public Builder passReadValue(boolean passReadValue)
        {
            this.passReadValue = passReadValue;
            return this;
        }

        /**
         * Sets whether static field reads are instrumented.
         * @param instrumentStatic true to instrument static reads
         * @return this builder
         */
        public Builder instrumentStatic(boolean instrumentStatic)
        {
            this.instrumentStatic = instrumentStatic;
            return this;
        }

        /**
         * Sets whether instance field reads are instrumented.
         * @param instrumentInstance true to instrument instance reads
         * @return this builder
         */
        public Builder instrumentInstance(boolean instrumentInstance)
        {
            this.instrumentInstance = instrumentInstance;
            return this;
        }

        /**
         * Builds the configured field read hook.
         * @return the new hook
         */
        public FieldReadHook build()
        {
            return new FieldReadHook(this);
        }
    }
}
