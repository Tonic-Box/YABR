package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for array load instrumentation.
 * Hooks are called after array load instructions (*ALOAD).
 */
public class ArrayLoadHook implements Hook {

    private final HookDescriptor hookDescriptor;
    private final List<InstrumentationFilter> filters;
    private final boolean enabled;
    private final int priority;
    private final boolean passArray;
    private final boolean passIndex;
    private final boolean passValue;
    private final String arrayTypeFilter;

    private ArrayLoadHook(Builder builder) {
        this.hookDescriptor = builder.hookDescriptor;
        this.filters = builder.filters;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
        this.passArray = builder.passArray;
        this.passIndex = builder.passIndex;
        this.passValue = builder.passValue;
        this.arrayTypeFilter = builder.arrayTypeFilter;
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

    /** Returns whether the array reference is passed to the hook. */
    public boolean isPassArray() {
        return passArray;
    }

    /** Returns whether the array index is passed to the hook. */
    public boolean isPassIndex() {
        return passIndex;
    }

    /** Returns whether the loaded value (boxed if primitive) is passed to the hook. */
    public boolean isPassValue() {
        return passValue;
    }

    /** Returns the array element type filter (e.g. {@code "[Ljava/lang/Object;"}), or null for no filter. */
    public String getArrayTypeFilter() {
        return arrayTypeFilter;
    }

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.ARRAY_LOAD;
    }

    /**
     * Creates a simple array load hook.
     */
    public static ArrayLoadHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return ArrayLoadHook.builder()
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
        private boolean passArray = false;
        private boolean passIndex = false;
        private boolean passValue = false;
        private String arrayTypeFilter;

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

        public Builder passArray(boolean passArray) {
            this.passArray = passArray;
            return this;
        }

        public Builder passIndex(boolean passIndex) {
            this.passIndex = passIndex;
            return this;
        }

        public Builder passValue(boolean passValue) {
            this.passValue = passValue;
            return this;
        }

        public Builder arrayTypeFilter(String arrayTypeFilter) {
            this.arrayTypeFilter = arrayTypeFilter;
            return this;
        }

        public ArrayLoadHook build() {
            return new ArrayLoadHook(this);
        }
    }
}
