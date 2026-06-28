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
public class MethodExitHook implements Hook {

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

    private MethodExitHook(Builder builder) {
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

    /** Returns whether the return value (boxed if primitive, null for void) is passed to the hook. */
    public boolean isPassReturnValue() {
        return passReturnValue;
    }

    /** Returns whether the hook can replace the return value (by returning a new one). */
    public boolean isCanModifyReturn() {
        return canModifyReturn;
    }

    /** Returns whether exceptional exits (in finally blocks) are instrumented. */
    public boolean isInstrumentExceptionalExits() {
        return instrumentExceptionalExits;
    }

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.METHOD_EXIT;
    }

    /**
     * Creates a simple method exit hook that calls the specified static method.
     */
    public static MethodExitHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return MethodExitHook.builder()
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
        private boolean passReturnValue = false;
        private boolean canModifyReturn = false;
        private boolean instrumentExceptionalExits = false;

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

        public Builder passReturnValue(boolean passReturnValue) {
            this.passReturnValue = passReturnValue;
            return this;
        }

        public Builder canModifyReturn(boolean canModifyReturn) {
            this.canModifyReturn = canModifyReturn;
            return this;
        }

        public Builder instrumentExceptionalExits(boolean instrumentExceptionalExits) {
            this.instrumentExceptionalExits = instrumentExceptionalExits;
            return this;
        }

        public MethodExitHook build() {
            return new MethodExitHook(this);
        }
    }
}
