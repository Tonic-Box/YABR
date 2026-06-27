package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method entry instrumentation.
 * Hooks are called at the beginning of methods, before any user code executes.
 */
@Getter
@Builder
public class MethodEntryHook implements Hook {

    private final HookDescriptor hookDescriptor;

    @Builder.Default
    private final List<InstrumentationFilter> filters = new ArrayList<>();

    @Builder.Default
    private final boolean enabled = true;

    @Builder.Default
    private final int priority = 100;

    /** Whether to pass 'this' reference (null for static methods) */
    @Builder.Default
    private final boolean passThis = false;

    /** Whether to pass method name as String */
    @Builder.Default
    private final boolean passMethodName = false;

    /** Whether to pass class name as String */
    @Builder.Default
    private final boolean passClassName = false;

    /** Whether to pass all parameters as Object[] */
    @Builder.Default
    private final boolean passAllParameters = false;

    /** Specific parameter indices to pass (boxed if primitive) */
    @Builder.Default
    private final List<Integer> parameterIndices = new ArrayList<>();

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
}
