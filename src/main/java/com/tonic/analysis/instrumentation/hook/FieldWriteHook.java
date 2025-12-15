package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for field write instrumentation.
 * Hooks are called before PUTFIELD/PUTSTATIC instructions.
 */
@Getter
@Builder
public class FieldWriteHook implements Hook {

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

    /** Whether to pass the new value being written (boxed if primitive) */
    @Builder.Default
    private final boolean passNewValue = false;

    /** Whether to pass the old/current value (requires additional load) */
    @Builder.Default
    private final boolean passOldValue = false;

    /** Whether the hook can modify the value being written (hook returns new value) */
    @Builder.Default
    private final boolean canModifyValue = false;

    /** Whether to instrument static fields */
    @Builder.Default
    private final boolean instrumentStatic = true;

    /** Whether to instrument instance fields */
    @Builder.Default
    private final boolean instrumentInstance = true;

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
}
