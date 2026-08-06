package com.tonic.analysis.execution.listener;

import java.util.Set;

/**
 * Extended listener interface that declares which high-frequency event capabilities this listener requires.
 */
public interface CapableListener extends BytecodeListener
{

    /**
     * Returns the set of capabilities this listener requires.
     * @return set of required capabilities, never null
     */
    Set<ListenerCapability> getCapabilities();

    /**
     * Checks if this listener has a specific capability.
     * @param capability the capability to check
     * @return true if this listener requests the capability
     */
    default boolean hasCapability(ListenerCapability capability)
    {
        Set<ListenerCapability> caps = getCapabilities();
        return caps.contains(ListenerCapability.ALL_OPERATIONS) || caps.contains(capability);
    }
}
