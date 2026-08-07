package com.tonic.analysis.execution.listener;

/**
 * Capabilities that listeners can request for performance-gated event notifications.
 */
public enum ListenerCapability
{
    /**
     * Stack push/pop notifications (very high frequency).
     */
    STACK_OPERATIONS,

    /**
     * Local variable load/store notifications (very high frequency).
     */
    LOCAL_OPERATIONS,

    /**
     * Array read/write notifications (high frequency).
     */
    ARRAY_OPERATIONS,

    /**
     * Field read notifications (high frequency).
     */
    FIELD_OPERATIONS,

    /**
     * Branch taken/not-taken notifications (medium frequency).
     */
    BRANCH_OPERATIONS,

    /**
     * Method entry/exit notifications.
     */
    METHOD_OPERATIONS,

    /**
     * Exception throw/catch notifications.
     */
    EXCEPTION_OPERATIONS,

    /**
     * Request all available notifications regardless of frequency.
     */
    ALL_OPERATIONS
}
