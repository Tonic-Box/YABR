package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method call interception.
 * Hooks can be called before or after calls to specific methods.
 */
public class MethodCallHook implements Hook {

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

    private MethodCallHook(Builder builder) {
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

    /** Returns the target class being called (internal name), or null for any class. */
    public String getTargetClass() {
        return targetClass;
    }

    /** Returns the target method being called, or null for any method on the target class. */
    public String getTargetMethod() {
        return targetMethod;
    }

    /** Returns the target method descriptor, or null for any descriptor. */
    public String getTargetDescriptor() {
        return targetDescriptor;
    }

    /** Returns whether the hook runs before the call. */
    public boolean isBefore() {
        return before;
    }

    /** Returns whether the hook runs after the call. */
    public boolean isAfter() {
        return after;
    }

    /** Returns whether the receiver object is passed to the hook. */
    public boolean isPassReceiver() {
        return passReceiver;
    }

    /** Returns whether all call arguments are passed to the hook as an {@code Object[]}. */
    public boolean isPassArguments() {
        return passArguments;
    }

    /** Returns whether the call result is passed to the hook (after-hooks only). */
    public boolean isPassResult() {
        return passResult;
    }

    /** Returns whether the target method name is passed to the hook. */
    public boolean isPassMethodName() {
        return passMethodName;
    }

    @Override
    public InstrumentationTarget getTarget() {
        if (before && !after) return InstrumentationTarget.METHOD_CALL_BEFORE;
        if (!before && after) return InstrumentationTarget.METHOD_CALL_AFTER;
        return InstrumentationTarget.METHOD_CALL_BEFORE;  // Default to before if both
    }

    /**
     * Creates a simple before-call hook.
     */
    public static MethodCallHook beforeCall(String targetClass, String targetMethod,
                                            String hookOwner, String hookName, String hookDescriptor) {
        return MethodCallHook.builder()
                .targetClass(targetClass)
                .targetMethod(targetMethod)
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .before(true)
                .after(false)
                .build();
    }

    /**
     * Creates a simple after-call hook.
     */
    public static MethodCallHook afterCall(String targetClass, String targetMethod,
                                           String hookOwner, String hookName, String hookDescriptor) {
        return MethodCallHook.builder()
                .targetClass(targetClass)
                .targetMethod(targetMethod)
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .before(false)
                .after(true)
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
        private String targetClass;
        private String targetMethod;
        private String targetDescriptor;
        private boolean before = true;
        private boolean after = false;
        private boolean passReceiver = false;
        private boolean passArguments = false;
        private boolean passResult = false;
        private boolean passMethodName = false;

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

        public Builder targetClass(String targetClass) {
            this.targetClass = targetClass;
            return this;
        }

        public Builder targetMethod(String targetMethod) {
            this.targetMethod = targetMethod;
            return this;
        }

        public Builder targetDescriptor(String targetDescriptor) {
            this.targetDescriptor = targetDescriptor;
            return this;
        }

        public Builder before(boolean before) {
            this.before = before;
            return this;
        }

        public Builder after(boolean after) {
            this.after = after;
            return this;
        }

        public Builder passReceiver(boolean passReceiver) {
            this.passReceiver = passReceiver;
            return this;
        }

        public Builder passArguments(boolean passArguments) {
            this.passArguments = passArguments;
            return this;
        }

        public Builder passResult(boolean passResult) {
            this.passResult = passResult;
            return this;
        }

        public Builder passMethodName(boolean passMethodName) {
            this.passMethodName = passMethodName;
            return this;
        }

        public MethodCallHook build() {
            return new MethodCallHook(this);
        }
    }
}
