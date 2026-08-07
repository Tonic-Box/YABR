package com.tonic.analysis.execution.debug;

/**
 * Lifecycle state of a debug session: not yet started, executing, suspended at a breakpoint, or finished.
 */
public enum DebugSessionState
{
    /**
     * Created but never started; no frame exists yet and only starting is allowed.
     */
    IDLE,
    /**
     * Executing instructions freely until a breakpoint, a step limit, or completion.
     */
    RUNNING,
    /**
     * Suspended with a live call stack that can be inspected and resumed.
     */
    PAUSED,
    /**
     * Finished, terminated, or failed to start; the session cannot be resumed.
     */
    STOPPED
}
