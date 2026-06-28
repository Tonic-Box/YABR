package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for exception interception.
 * Hooks are called in exception handlers.
 */
public class ExceptionHook implements Hook {

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final String exceptionType;
    private final boolean passException;
    private final boolean passMethodName;
    private final boolean passClassName;
    private final boolean canSuppress;

    private ExceptionHook(Builder builder) {
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

    /** Returns the exception type to intercept (internal name), or null for all types. */
    public String getExceptionType() {
        return exceptionType;
    }

    /** Returns whether the exception object is passed to the hook. */
    public boolean isPassException() {
        return passException;
    }

    /** Returns whether the name of the method where the exception occurred is passed to the hook. */
    public boolean isPassMethodName() {
        return passMethodName;
    }

    /** Returns whether the name of the class where the exception occurred is passed to the hook. */
    public boolean isPassClassName() {
        return passClassName;
    }

    /** Returns whether the hook can suppress the exception (by returning true). */
    public boolean isCanSuppress() {
        return canSuppress;
    }

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.EXCEPTION_HANDLER;
    }

    /**
     * Creates a simple exception hook.
     */
    public static ExceptionHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return ExceptionHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }

    /**
     * Creates a hook for a specific exception type.
     */
    public static ExceptionHook forType(String exceptionType, String hookOwner, String hookName, String hookDescriptor) {
        return ExceptionHook.builder()
                .exceptionType(exceptionType)
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
        private String exceptionType;
        private boolean passException = true;
        private boolean passMethodName = false;
        private boolean passClassName = false;
        private boolean canSuppress = false;

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

        public Builder exceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
            return this;
        }

        public Builder passException(boolean passException) {
            this.passException = passException;
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

        public Builder canSuppress(boolean canSuppress) {
            this.canSuppress = canSuppress;
            return this;
        }

        public ExceptionHook build() {
            return new ExceptionHook(this);
        }
    }
}
