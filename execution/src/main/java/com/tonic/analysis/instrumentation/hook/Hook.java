package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;

import java.util.List;

/**
 * Base interface for all hook configurations.
 */
public interface Hook {

    /**
     * Gets the target type of this hook.
     */
    InstrumentationTarget getTarget();

    /**
     * Gets the hook method descriptor.
     */
    HookDescriptor getHookDescriptor();

    /**
     * Gets the filters determining where this hook applies.
     */
    List<InstrumentationFilter> getFilters();

    /**
     * Checks if this hook is enabled.
     */
    boolean isEnabled();

    /**
     * Gets the priority for ordering multiple hooks at the same point.
     * Lower values execute first.
     */
    default int getPriority() {
        return 100;
    }
}
