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
public class MethodEntryHook implements Hook {

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passThis;
    private final boolean passMethodName;
    private final boolean passClassName;
    private final boolean passAllParameters;
    private final List<Integer> parameterIndices;

    private MethodEntryHook(Builder builder) {
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

    public HookDescriptor getHookDescriptor() {
        return hookDescriptor;
    }

    public List<InstrumentationFilter> getFilters() {
        return filters;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    /** Returns whether the {@code this} reference (null for static methods) is passed to the hook. */
    public boolean isPassThis() {
        return passThis;
    }

    /** Returns whether the method name is passed to the hook. */
    public boolean isPassMethodName() {
        return passMethodName;
    }

    /** Returns whether the class name is passed to the hook. */
    public boolean isPassClassName() {
        return passClassName;
    }

    /** Returns whether all parameters are passed to the hook as an {@code Object[]}. */
    public boolean isPassAllParameters() {
        return passAllParameters;
    }

    /** Returns the specific parameter indices to pass (boxed if primitive). */
    public List<Integer> getParameterIndices() {
        return parameterIndices;
    }

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.METHOD_ENTRY;
    }

    /**
     * Creates a simple method entry hook that calls the specified static method.
     */
    public static MethodEntryHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return MethodEntryHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HookDescriptor hookDescriptor;
        private List<InstrumentationFilter> filters = new ArrayList<>();
        private boolean enabled = true;
        private int priority = 100;
        private boolean passThis = false;
        private boolean passMethodName = false;
        private boolean passClassName = false;
        private boolean passAllParameters = false;
        private List<Integer> parameterIndices = new ArrayList<>();

        public Builder hookDescriptor(HookDescriptor hookDescriptor) {
            this.hookDescriptor = hookDescriptor;
            return this;
        }

        public Builder filters(List<InstrumentationFilter> filters) {
            this.filters = filters;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder passThis(boolean passThis) {
            this.passThis = passThis;
            return this;
        }

        public Builder passMethodName(boolean passMethodName) {
            this.passMethodName = passMethodName;
            return this;
        }

        public Builder passClassName(boolean passClassName) {
            this.passClassName = passClassName;
            return this;
        }

        public Builder passAllParameters(boolean passAllParameters) {
            this.passAllParameters = passAllParameters;
            return this;
        }

        public Builder parameterIndices(List<Integer> parameterIndices) {
            this.parameterIndices = parameterIndices;
            return this;
        }

        public MethodEntryHook build() {
            return new MethodEntryHook(this);
        }
    }
}
