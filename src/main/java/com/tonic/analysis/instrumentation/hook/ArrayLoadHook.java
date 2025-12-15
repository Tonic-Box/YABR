package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for array load instrumentation.
 * Hooks are called after array load instructions (*ALOAD).
 */
@Getter
@Builder
public class ArrayLoadHook implements Hook {

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

    /** Whether to pass the value that was loaded (boxed if primitive) */
    @Builder.Default
    private final boolean passValue = false;

    /** Array element type filter (e.g., "[Ljava/lang/Object;" for Object[]) */
    private final String arrayTypeFilter;

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
}
