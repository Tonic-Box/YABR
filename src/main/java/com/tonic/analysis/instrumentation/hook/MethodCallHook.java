package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for method call interception.
 * Hooks can be called before or after calls to specific methods.
 */
@Getter
@Builder
public class MethodCallHook implements Hook {

    private final HookDescriptor hookDescriptor;

    @Builder.Default
    private final List<InstrumentationFilter> filters = new ArrayList<>();

    @Builder.Default
    private final boolean enabled = true;

    @Builder.Default
    private final int priority = 100;

    /** The target class being called (internal name, e.g., "java/io/PrintStream") */
    private final String targetClass;

    /** The target method being called (null for any method on targetClass) */
    private final String targetMethod;

    /** The target method descriptor (null for any descriptor) */
    private final String targetDescriptor;

    /** Whether to instrument before the call */
    @Builder.Default
    private final boolean before = true;

    /** Whether to instrument after the call */
    @Builder.Default
    private final boolean after = false;

    /** Whether to pass the receiver object */
    @Builder.Default
    private final boolean passReceiver = false;

    /** Whether to pass all arguments as Object[] */
    @Builder.Default
    private final boolean passArguments = false;

    /** Whether to pass the result (after hooks only) */
    @Builder.Default
    private final boolean passResult = false;

    /** Whether to pass the target method name */
    @Builder.Default
    private final boolean passMethodName = false;

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
}
