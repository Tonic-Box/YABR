package com.tonic.analysis.execution.listener;

/**
 * Capabilities that listeners can request for performance-gated event notifications.
 * High-frequency events (stack, local, array operations) are only dispatched to
 * listeners that explicitly request them via CapableListener.getCapabilities().
 */
public enum ListenerCapability {
    /**
     * Stack push/pop notifications (very high frequency).
     * Events: onStackPush, onStackPop
     */
    STACK_OPERATIONS,

    /**
     * Local variable load/store notifications (very high frequency).
     * Events: onLocalLoad, onLocalStore
     */
    LOCAL_OPERATIONS,

    /**
     * Array read/write notifications (high frequency).
     * Events: onArrayRead, onArrayWrite
     */
    ARRAY_OPERATIONS,

    /**
     * Field read notifications (high frequency).
     * Note: onFieldWrite is always dispatched; this gates onFieldRead only.
     * Events: onFieldRead
     */
    FIELD_OPERATIONS,

    /**
     * Branch taken/not-taken notifications (medium frequency).
     * Events: onBranch
     */
    BRANCH_OPERATIONS,

    /**
     * Method entry/exit notifications.
     * Events: onMethodCall, onMethodReturn
     */
    METHOD_OPERATIONS,

    /**
     * Exception throw/catch notifications.
     * Events: onExceptionThrow, onExceptionCatch
     */
    EXCEPTION_OPERATIONS,

    /**
     * Request all available notifications regardless of frequency.
     * Use with caution - significant performance impact.
     */
    ALL_OPERATIONS
}
