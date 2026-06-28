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
public class FieldWriteHook implements Hook {

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

    private FieldWriteHook(Builder builder) {
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

    /** Returns whether the new value being written (boxed if primitive) is passed to the hook. */
    public boolean isPassNewValue() {
        return passNewValue;
    }

    /** Returns whether the old value (requires an additional load) is passed to the hook. */
    public boolean isPassOldValue() {
        return passOldValue;
    }

    /** Returns whether the hook can replace the value being written (by returning a new one). */
    public boolean isCanModifyValue() {
        return canModifyValue;
    }

    /** Returns whether static field writes are instrumented. */
    public boolean isInstrumentStatic() {
        return instrumentStatic;
    }

    /** Returns whether instance field writes are instrumented. */
    public boolean isInstrumentInstance() {
        return instrumentInstance;
    }

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.FIELD_WRITE;
    }

    /**
     * Creates a simple field write hook.
     */
    public static FieldWriteHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return FieldWriteHook.builder()
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
        private boolean passNewValue = false;
        private boolean passOldValue = false;
        private boolean canModifyValue = false;
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

        public Builder passNewValue(boolean passNewValue) {
            this.passNewValue = passNewValue;
            return this;
        }

        public Builder passOldValue(boolean passOldValue) {
            this.passOldValue = passOldValue;
            return this;
        }

        public Builder canModifyValue(boolean canModifyValue) {
            this.canModifyValue = canModifyValue;
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

        public FieldWriteHook build() {
            return new FieldWriteHook(this);
        }
    }
}
