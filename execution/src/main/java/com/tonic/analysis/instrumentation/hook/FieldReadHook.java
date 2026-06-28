package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for field read instrumentation.
 * Hooks are called after GETFIELD/GETSTATIC instructions.
 */
public class FieldReadHook implements Hook {

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passOwner;
    private final boolean passFieldName;
    private final boolean passReadValue;
    private final boolean instrumentStatic;
    private final boolean instrumentInstance;

    private FieldReadHook(Builder builder) {
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

    /** Returns whether the field's owning object (null for static fields) is passed to the hook. */
    public boolean isPassOwner() {
        return passOwner;
    }

    /** Returns whether the field name is passed to the hook. */
    public boolean isPassFieldName() {
        return passFieldName;
    }

    /** Returns whether the value that was read (boxed if primitive) is passed to the hook. */
    public boolean isPassReadValue() {
        return passReadValue;
    }

    /** Returns whether static field reads are instrumented. */
    public boolean isInstrumentStatic() {
        return instrumentStatic;
    }

    /** Returns whether instance field reads are instrumented. */
    public boolean isInstrumentInstance() {
        return instrumentInstance;
    }

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.FIELD_READ;
    }

    /**
     * Creates a simple field read hook.
     */
    public static FieldReadHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return FieldReadHook.builder()
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
        private boolean passOwner = false;
        private boolean passFieldName = false;
        private boolean passReadValue = false;
        private boolean instrumentStatic = true;
        private boolean instrumentInstance = true;

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

        public Builder passOwner(boolean passOwner) {
            this.passOwner = passOwner;
            return this;
        }

        public Builder passFieldName(boolean passFieldName) {
            this.passFieldName = passFieldName;
            return this;
        }

        public Builder passReadValue(boolean passReadValue) {
            this.passReadValue = passReadValue;
            return this;
        }

        public Builder instrumentStatic(boolean instrumentStatic) {
            this.instrumentStatic = instrumentStatic;
            return this;
        }

        public Builder instrumentInstance(boolean instrumentInstance) {
            this.instrumentInstance = instrumentInstance;
            return this;
        }

        public FieldReadHook build() {
            return new FieldReadHook(this);
        }
    }
}
