package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for exception interception.
 */
public class ExceptionHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final String exceptionType;
    private final boolean passException;
    private final boolean passMethodName;
    private final boolean passClassName;
    private final boolean canSuppress;

    private ExceptionHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.exceptionType = builder.exceptionType;
        this.passException = builder.passException;
        this.passMethodName = builder.passMethodName;
        this.passClassName = builder.passClassName;
        this.canSuppress = builder.canSuppress;
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
     * @return the exception type to intercept (internal name), or null for all types
     */
    public String getExceptionType()
    {
        return exceptionType;
    }

    /**
     * @return whether the exception object is passed to the hook
     */
    public boolean isPassException()
    {
        return passException;
    }

    /**
     * @return whether the name of the method where the exception occurred is passed to the hook
     */
    public boolean isPassMethodName()
    {
        return passMethodName;
    }

    /**
     * @return whether the name of the class where the exception occurred is passed to the hook
     */
    public boolean isPassClassName()
    {
        return passClassName;
    }

    /**
     * @return whether the hook can suppress the exception by returning true
     */
    public boolean isCanSuppress()
    {
        return canSuppress;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        return InstrumentationTarget.EXCEPTION_HANDLER;
    }

    /**
     * Creates an exception hook that invokes the given static hook method for all exception types.
     * @param hookOwner the hook method's owning class (internal name)
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the new hook
     */
    public static ExceptionHook simple(String hookOwner, String hookName, String hookDescriptor)
    {
        return ExceptionHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * Creates an exception hook restricted to a specific exception type.
     * @param exceptionType the exception type to intercept (internal name)
     * @param hookOwner the hook method's owning class (internal name)
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the new hook
     */
    public static ExceptionHook forType(String exceptionType, String hookOwner, String hookName, String hookDescriptor)
    {
        return ExceptionHook.builder()
                .exceptionType(exceptionType)
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * Creates a builder for an exception hook.
     * @return a new builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ExceptionHook} instances.
     */
    public static class Builder
    {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private String exceptionType;
        private boolean passException = true;
        private boolean passMethodName = false;
        private boolean passClassName = false;
        private boolean canSuppress = false;

        /**
         * Sets the hook method invoked in each instrumented exception handler.
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
         * Sets the exception type to intercept; null intercepts all types.
         * @param exceptionType the exception type (internal name)
         * @return this builder
         */
        public Builder exceptionType(String exceptionType)
        {
            this.exceptionType = exceptionType;
            return this;
        }

        /**
         * Sets whether the exception object is passed to the hook.
         * @param passException true to pass the exception
         * @return this builder
         */
        public Builder passException(boolean passException)
        {
            this.passException = passException;
            return this;
        }

        /**
         * Sets whether the enclosing method name is passed to the hook.
         * @param passMethodName true to pass the method name
         * @return this builder
         */
        public Builder passMethodName(boolean passMethodName)
        {
            this.passMethodName = passMethodName;
            return this;
        }

        /**
         * Sets whether the enclosing class name is passed to the hook.
         * @param passClassName true to pass the class name
         * @return this builder
         */
        public Builder passClassName(boolean passClassName)
        {
            this.passClassName = passClassName;
            return this;
        }

        /**
         * Sets whether the hook can suppress the exception by returning true.
         * @param canSuppress true to let the hook suppress the exception
         * @return this builder
         */
        public Builder canSuppress(boolean canSuppress)
        {
            this.canSuppress = canSuppress;
            return this;
        }

        /**
         * Builds the configured exception hook.
         * @return the new hook
         */
        public ExceptionHook build()
        {
            return new ExceptionHook(this);
        }
    }
}
