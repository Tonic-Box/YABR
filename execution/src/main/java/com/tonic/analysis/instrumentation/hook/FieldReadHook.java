package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for field read instrumentation.
 * Hooks are called after GETFIELD/GETSTATIC instructions.
 */
@Getter
@Builder
public class FieldReadHook implements Hook {

    private final HookDescriptor hookDescriptor;

    @Builder.Default
    private final List<InstrumentationFilter> filters = new ArrayList<>();

    @Builder.Default
    private final boolean enabled = true;

    @Builder.Default
    private final int priority = 100;

    /** Whether to pass the object owning the field (null for static) */
    @Builder.Default
    private final boolean passOwner = false;

    /** Whether to pass the field name as String */
    @Builder.Default
    private final boolean passFieldName = false;

    /** Whether to pass the value that was read (boxed if primitive) */
    @Builder.Default
    private final boolean passReadValue = false;

    /** Whether to instrument static fields */
    @Builder.Default
    private final boolean instrumentStatic = true;

    /** Whether to instrument instance fields */
    @Builder.Default
    private final boolean instrumentInstance = true;

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
}
