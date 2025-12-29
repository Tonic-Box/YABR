package com.tonic.analysis.execution.listener;

import java.util.Set;

/**
 * Extended listener interface that declares which high-frequency event
 * capabilities this listener requires. The engine uses these declarations
 * to skip expensive notification calls for listeners that don't need them.
 *
 * Listeners implementing this interface should return only the capabilities
 * they actually use, as each capability has a performance cost.
 */
public interface CapableListener extends BytecodeListener {

    /**
     * Returns the set of capabilities this listener requires.
     * The engine will only dispatch gated events to listeners that
     * request the corresponding capability.
     *
     * @return set of required capabilities, never null
     */
    Set<ListenerCapability> getCapabilities();

    /**
     * Checks if this listener has a specific capability.
     *
     * @param capability the capability to check
     * @return true if this listener requests the capability
     */
    default boolean hasCapability(ListenerCapability capability) {
        Set<ListenerCapability> caps = getCapabilities();
        return caps.contains(ListenerCapability.ALL_OPERATIONS) || caps.contains(capability);
    }
}
