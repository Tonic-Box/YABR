package com.tonic.analysis.execution.debug;

/**
 * What an interceptor tells the interpreter to do after a callback: keep going, pause, or abort.
 */
public enum InterceptorAction
{
    /**
     * Proceed as if the interceptor had not been called; the default for every callback.
     */
    CONTINUE,
    /**
     * Suspend at this point, as a breakpoint would, leaving the run resumable.
     */
    PAUSE,
    /**
     * Stop the run outright rather than resuming later.
     */
    ABORT
}
