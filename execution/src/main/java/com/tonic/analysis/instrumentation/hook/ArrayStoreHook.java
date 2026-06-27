package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for array store instrumentation.
 * Hooks are called before array store instructions (*ASTORE).
 */
@Getter
@Builder
public class ArrayStoreHook implements Hook {

    private final HookDescriptor hookDescriptor;

    @Builder.Default
    private final List<InstrumentationFilter> filters = new ArrayList<>();

    @Builder.Default
    private final boolean enabled = true;

    @Builder.Default
    private final int priority = 100;

    /** Whether to pass the array reference */
    @Builder.Default
    private final boolean passArray = false;

    /** Whether to pass the index */
    @Builder.Default
    private final boolean passIndex = false;

    /** Whether to pass the value being stored (boxed if primitive) */
    @Builder.Default
    private final boolean passValue = false;

    /** Whether the hook can modify the value (hook returns new value) */
    @Builder.Default
    private final boolean canModifyValue = false;

    /** Array element type filter (e.g., "[Ljava/lang/Object;" for Object[]) */
    private final String arrayTypeFilter;

    @Override
    public InstrumentationTarget getTarget() {
        return InstrumentationTarget.ARRAY_STORE;
    }

    /**
     * Creates a simple array store hook.
     */
    public static ArrayStoreHook simple(String hookOwner, String hookName, String hookDescriptor) {
        return ArrayStoreHook.builder()
                .hookDescriptor(HookDescriptor.staticHook(hookOwner, hookName, hookDescriptor))
                .build();
    }
}
