package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for exception interception.
 * Hooks are called in exception handlers.
 */
@Getter
@Builder
public class ExceptionHook implements Hook {

    private final HookDescriptor hookDescriptor;

    @Builder.Default
    private final List<InstrumentationFilter> filters = new ArrayList<>();

    @Builder.Default
    private final boolean enabled = true;

    @Builder.Default
    private final int priority = 100;

    /** Exception type to intercept (internal name, null for all) */
    private final String exceptionType;

    /** Whether to pass the exception object */
    @Builder.Default
    private final boolean passException = true;

    /** Whether to pass the method name where exception occurred */
    @Builder.Default
    private final boolean passMethodName = false;

    /** Whether to pass the class name where exception occurred */
    @Builder.Default
    private final boolean passClassName = false;

    /** Whether the hook can suppress the exception (return true to suppress) */
    @Builder.Default
    private final boolean canSuppress = false;

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
}
