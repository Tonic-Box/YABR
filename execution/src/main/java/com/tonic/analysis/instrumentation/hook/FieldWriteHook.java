package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for field write instrumentation.
 * Hooks are called before PUTFIELD/PUTSTATIC instructions.
 */
public class FieldWriteHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passOwner;
    private final boolean passFieldName;
    private final boolean passNewValue;
    private final boolean passOldValue;
    private final boolean canModifyValue;
    private final boolean instrumentStatic;
    private final boolean instrumentInstance;

    private FieldWriteHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.passOwner = builder.passOwner;
        this.passFieldName = builder.passFieldName;
        this.passNewValue = builder.passNewValue;
        this.passOldValue = builder.passOldValue;
        this.canModifyValue = builder.canModifyValue;
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
     * @return whether the new value being written (boxed if primitive) is passed to the hook
     */
    public boolean isPassNewValue()
    {
        return passNewValue;
    }

    /**
     * @return whether the old value (requires an additional load) is passed to the hook
     */
    public boolean isPassOldValue()
    {
        return passOldValue;
    }

    /**
     * @return whether the hook can replace the value being written (by returning a new one)
     */
    public boolean isCanModifyValue()
    {
        return canModifyValue;
    }

    /**
     * @return whether static field writes are instrumented
     */
    public boolean isInstrumentStatic()
    {
        return instrumentStatic;
    }

    /**
     * @return whether instance field writes are instrumented
     */
    public boolean isInstrumentInstance()
    {
        return instrumentInstance;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        return InstrumentationTarget.FIELD_WRITE;
    }

    /**
     * Creates a hook with default settings that calls a static method.
     * @param hookOwner internal name of the class declaring the hook method
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the configured hook
     */
    public static FieldWriteHook simple(String hookOwner, String hookName, String hookDescriptor)
    {
        return FieldWriteHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for {@link FieldWriteHook}, defaulting to enabled, priority 100, no values
     * passed, and both static and instance writes instrumented.
     */
    public static class Builder
    {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private boolean passOwner = false;
        private boolean passFieldName = false;
        private boolean passNewValue = false;
        private boolean passOldValue = false;
        private boolean canModifyValue = false;
        private boolean instrumentStatic = true;
        private boolean instrumentInstance = true;

        /**
         * Sets the method the instrumentation will call.
         * @param hookDescriptor the hook method descriptor
         * @return this builder
         */
        public Builder hookDescriptor(HookDescriptor hookDescriptor)
        {
            this.hookDescriptor = hookDescriptor;
            return this;
        }

        /**
         * Replaces the filters deciding which sites are instrumented.
         * @param filters the filter list
         * @return this builder
         */
        public Builder filters(List<InstrumentationFilter> filters)
        {
            this.filters = filters;
            return this;
        }

        /**
         * Sets whether the hook is applied at all.
         * @param enabled whether the hook is active
         * @return this builder
         */
        public Builder enabled(boolean enabled)
        {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the ordering weight against other hooks on the same site.
         * @param priority the priority value
         * @return this builder
         */
        public Builder priority(int priority)
        {
            this.priority = priority;
            return this;
        }

        /**
         * Sets whether the owning object is passed to the hook.
         * @param passOwner whether to pass the owner
         * @return this builder
         */
        public Builder passOwner(boolean passOwner)
        {
            this.passOwner = passOwner;
            return this;
        }

        /**
         * Sets whether the field name is passed to the hook.
         * @param passFieldName whether to pass the field name
         * @return this builder
         */
        public Builder passFieldName(boolean passFieldName)
        {
            this.passFieldName = passFieldName;
            return this;
        }

        /**
         * Sets whether the value being written is passed to the hook.
         * @param passNewValue whether to pass the new value
         * @return this builder
         */
        public Builder passNewValue(boolean passNewValue)
        {
            this.passNewValue = passNewValue;
            return this;
        }

        /**
         * Sets whether the previous field value is read and passed to the hook.
         * @param passOldValue whether to pass the old value
         * @return this builder
         */
        public Builder passOldValue(boolean passOldValue)
        {
            this.passOldValue = passOldValue;
            return this;
        }

        /**
         * Sets whether the hook's return value replaces the value written.
         * @param canModifyValue whether the hook may substitute a value
         * @return this builder
         */
        public Builder canModifyValue(boolean canModifyValue)
        {
            this.canModifyValue = canModifyValue;
            return this;
        }

        /**
         * Sets whether PUTSTATIC sites are instrumented.
         * @param instrumentStatic whether to instrument static writes
         * @return this builder
         */
        public Builder instrumentStatic(boolean instrumentStatic)
        {
            this.instrumentStatic = instrumentStatic;
            return this;
        }

        /**
         * Sets whether PUTFIELD sites are instrumented.
         * @param instrumentInstance whether to instrument instance writes
         * @return this builder
         */
        public Builder instrumentInstance(boolean instrumentInstance)
        {
            this.instrumentInstance = instrumentInstance;
            return this;
        }

        /**
         * @return the configured hook
         */
        public FieldWriteHook build()
        {
            return new FieldWriteHook(this);
        }
    }
}
