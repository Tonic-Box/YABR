package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method call interception.
 */
public class MethodCallHook implements Hook
{

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final String targetClass;
    private final String targetMethod;
    private final String targetDescriptor;
    private final boolean before;
    private final boolean after;
    private final boolean passReceiver;
    private final boolean passArguments;
    private final boolean passResult;
    private final boolean passMethodName;

    private MethodCallHook(Builder builder)
    {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.targetClass = builder.targetClass;
        this.targetMethod = builder.targetMethod;
        this.targetDescriptor = builder.targetDescriptor;
        this.before = builder.before;
        this.after = builder.after;
        this.passReceiver = builder.passReceiver;
        this.passArguments = builder.passArguments;
        this.passResult = builder.passResult;
        this.passMethodName = builder.passMethodName;
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
     * @return the target class being called (internal name), or null for any class
     */
    public String getTargetClass()
    {
        return targetClass;
    }

    /**
     * @return the target method being called, or null for any method on the target class
     */
    public String getTargetMethod()
    {
        return targetMethod;
    }

    /**
     * @return the target method descriptor, or null for any descriptor
     */
    public String getTargetDescriptor()
    {
        return targetDescriptor;
    }

    /**
     * @return whether the hook runs before the call
     */
    public boolean isBefore()
    {
        return before;
    }

    /**
     * @return whether the hook runs after the call
     */
    public boolean isAfter()
    {
        return after;
    }

    /**
     * @return whether the receiver object is passed to the hook
     */
    public boolean isPassReceiver()
    {
        return passReceiver;
    }

    /**
     * @return whether all call arguments are passed to the hook as an {@code Object[]}
     */
    public boolean isPassArguments()
    {
        return passArguments;
    }

    /**
     * @return whether the call result is passed to the hook (after-hooks only)
     */
    public boolean isPassResult()
    {
        return passResult;
    }

    /**
     * @return whether the target method name is passed to the hook
     */
    public boolean isPassMethodName()
    {
        return passMethodName;
    }

    @Override
    public InstrumentationTarget getTarget()
    {
        if (before && !after) return InstrumentationTarget.METHOD_CALL_BEFORE;
        if (!before && after) return InstrumentationTarget.METHOD_CALL_AFTER;
        return InstrumentationTarget.METHOD_CALL_BEFORE;  // Default to before if both
    }

    /**
     * Creates a hook that runs before calls to a named method.
     * @param targetClass internal name of the class whose calls are intercepted
     * @param targetMethod the intercepted method name
     * @param hookOwner internal name of the class declaring the hook method
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the configured hook
     */
    public static MethodCallHook beforeCall(String targetClass, String targetMethod, String hookOwner, String hookName, String hookDescriptor)
    {
        return MethodCallHook.builder()
                .targetClass(targetClass)
                .targetMethod(targetMethod)
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .before(true)
                .after(false)
                .build();
    }

    /**
     * Creates a hook that runs after calls to a named method.
     * @param targetClass internal name of the class whose calls are intercepted
     * @param targetMethod the intercepted method name
     * @param hookOwner internal name of the class declaring the hook method
     * @param hookName the hook method name
     * @param hookDescriptor the hook method descriptor
     * @return the configured hook
     */
    public static MethodCallHook afterCall(String targetClass, String targetMethod, String hookOwner, String hookName, String hookDescriptor)
    {
        return MethodCallHook.builder()
                .targetClass(targetClass)
                .targetMethod(targetMethod)
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .before(false)
                .after(true)
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
     * Builder for {@link MethodCallHook}, defaulting to enabled, priority 100, before-call
     * placement, an unrestricted target and no values passed.
     */
    public static class Builder
    {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private String targetClass;
        private String targetMethod;
        private String targetDescriptor;
        private boolean before = true;
        private boolean after = false;
        private boolean passReceiver = false;
        private boolean passArguments = false;
        private boolean passResult = false;
        private boolean passMethodName = false;

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
         * Replaces the filters deciding which call sites are instrumented.
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
         * Restricts interception to calls on one class.
         * @param targetClass internal name of the class, or null for any class
         * @return this builder
         */
        public Builder targetClass(String targetClass)
        {
            this.targetClass = targetClass;
            return this;
        }

        /**
         * Restricts interception to one method name.
         * @param targetMethod the method name, or null for any method
         * @return this builder
         */
        public Builder targetMethod(String targetMethod)
        {
            this.targetMethod = targetMethod;
            return this;
        }

        /**
         * Restricts interception to one overload.
         * @param targetDescriptor the method descriptor, or null for any descriptor
         * @return this builder
         */
        public Builder targetDescriptor(String targetDescriptor)
        {
            this.targetDescriptor = targetDescriptor;
            return this;
        }

        /**
         * Sets whether the hook runs before the call.
         * @param before whether to hook the call site entry
         * @return this builder
         */
        public Builder before(boolean before)
        {
            this.before = before;
            return this;
        }

        /**
         * Sets whether the hook runs after the call.
         * @param after whether to hook the call site exit
         * @return this builder
         */
        public Builder after(boolean after)
        {
            this.after = after;
            return this;
        }

        /**
         * Sets whether the call receiver is passed to the hook.
         * @param passReceiver whether to pass the receiver
         * @return this builder
         */
        public Builder passReceiver(boolean passReceiver)
        {
            this.passReceiver = passReceiver;
            return this;
        }

        /**
         * Sets whether the call arguments are passed to the hook as an object array.
         * @param passArguments whether to pass the arguments
         * @return this builder
         */
        public Builder passArguments(boolean passArguments)
        {
            this.passArguments = passArguments;
            return this;
        }

        /**
         * Sets whether the call result is passed to the hook; only meaningful for after-hooks.
         * @param passResult whether to pass the result
         * @return this builder
         */
        public Builder passResult(boolean passResult)
        {
            this.passResult = passResult;
            return this;
        }

        /**
         * Sets whether the intercepted method name is passed to the hook.
         * @param passMethodName whether to pass the method name
         * @return this builder
         */
        public Builder passMethodName(boolean passMethodName)
        {
            this.passMethodName = passMethodName;
            return this;
        }

        /**
         * @return the configured hook
         */
        public MethodCallHook build()
        {
            return new MethodCallHook(this);
        }
    }
}
