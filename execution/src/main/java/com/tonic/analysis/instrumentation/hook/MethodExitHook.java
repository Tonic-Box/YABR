package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method exit instrumentation.
 * Hooks are called before each return statement in the method.
 */
@Getter
@Builder
public class MethodExitHook implements Hook {

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

    /** Whether to pass return value (boxed if primitive, null for void) */
    @Builder.Default
    private final boolean passReturnValue = false;

    /** Whether the hook can modify the return value (hook returns new value) */
    @Builder.Default
    private final boolean canModifyReturn = false;

    /** Whether to instrument exceptional exits (in finally blocks) */
    @Builder.Default
    private final boolean instrumentExceptionalExits = false;

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
}
