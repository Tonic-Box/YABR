package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method exit instrumentation.
 * Hooks are called before each return statement in the method.
 */
public class MethodExitHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passThis;
    private final boolean passMethodName;
    private final boolean passClassName;
    private final boolean passReturnValue;
    private final boolean canModifyReturn;
    private final boolean instrumentExceptionalExits;

    private MethodExitHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.passThis = builder.passThis;
        this.passMethodName = builder.passMethodName;
        this.passClassName = builder.passClassName;
        this.passReturnValue = builder.passReturnValue;
        this.canModifyReturn = builder.canModifyReturn;
        this.instrumentExceptionalExits = builder.instrumentExceptionalExits;
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
     * @return whether the {@code this} reference (null for static methods) is passed to the hook
     */
    public boolean isPassThis()
    {
        return passThis;
    }

    /**
     * @return whether the method name is passed to the hook
     */
    public boolean isPassMethodName()
    {
        return passMethodName;
    }

    /**
     * @return whether the class name is passed to the hook
     */
    public boolean isPassClassName()
    {
        return passClassName;
    }

    /**
     * @return whether the return value (boxed if primitive, null for void) is passed to the hook
     */
    public boolean isPassReturnValue()
    {
        return passReturnValue;
    }

    /**
     * @return whether the hook can replace the return value (by returning a new one)
     */
    public boolean isCanModifyReturn()
    {
        return canModifyReturn;
    }

    /**
     * @return whether exceptional exits (in finally blocks) are instrumented
     */
    public boolean isInstrumentExceptionalExits()
    {
        return instrumentExceptionalExits;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        return InstrumentationTarget.METHOD_EXIT;
    }

    /**
     * Creates a hook with default settings that calls a static method.
     * @param hookOwner internal name of the class declaring the hook method
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the configured hook
     */
    public static MethodExitHook simple(String hookOwner, String hookName, String hookDescriptor)
    {
        return MethodExitHook.builder()
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
     * Builder for {@link MethodExitHook}, defaulting to enabled, priority 100, no values
     * passed and normal returns only.
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
        private boolean passReturnValue = false;
        private boolean canModifyReturn = false;
        private boolean instrumentExceptionalExits = false;

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
         * Replaces the filters deciding which methods are instrumented.
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
         * Sets whether the {@code this} reference is passed to the hook.
         * @param passThis whether to pass the receiver
         * @return this builder
         */
        public Builder passThis(boolean passThis)
        {
            this.passThis = passThis;
            return this;
        }

        /**
         * Sets whether the enclosing method name is passed to the hook.
         * @param passMethodName whether to pass the method name
         * @return this builder
         */
        public Builder passMethodName(boolean passMethodName)
        {
            this.passMethodName = passMethodName;
            return this;
        }

        /**
         * Sets whether the enclosing class name is passed to the hook.
         * @param passClassName whether to pass the class name
         * @return this builder
         */
        public Builder passClassName(boolean passClassName)
        {
            this.passClassName = passClassName;
            return this;
        }

        /**
         * Sets whether the returned value is passed to the hook.
         * @param passReturnValue whether to pass the return value
         * @return this builder
         */
        public Builder passReturnValue(boolean passReturnValue)
        {
            this.passReturnValue = passReturnValue;
            return this;
        }

        /**
         * Sets whether the hook's return value replaces the value returned.
         * @param canModifyReturn whether the hook may substitute a return value
         * @return this builder
         */
        public Builder canModifyReturn(boolean canModifyReturn)
        {
            this.canModifyReturn = canModifyReturn;
            return this;
        }

        /**
         * Sets whether exits by thrown exception are hooked as well as returns.
         * @param instrumentExceptionalExits whether to instrument exceptional exits
         * @return this builder
         */
        public Builder instrumentExceptionalExits(boolean instrumentExceptionalExits)
        {
            this.instrumentExceptionalExits = instrumentExceptionalExits;
            return this;
        }

        /**
         * @return the configured hook
         */
        public MethodExitHook build()
        {
            return new MethodExitHook(this);
        }
    }
}
