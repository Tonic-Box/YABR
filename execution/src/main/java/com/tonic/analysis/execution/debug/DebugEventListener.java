package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.heap.ObjectInstance;

/**
 * Observer of debug session lifecycle, breakpoint, step, and exception events; every callback
 * defaults to a no-op.
 */
public interface DebugEventListener
{

    /**
     * Fired once the session is ready to run.
     *
     * @param session the session that started
     */
    default void onSessionStart(DebugSession session) {}

    /**
     * Fired when the session finishes or is terminated.
     *
     * @param session the session that stopped
     * @param result the execution outcome: completion, exception, or interruption
     */
    default void onSessionStop(DebugSession session, BytecodeResult result) {}

    /**
     * Fired when execution stops at a breakpoint.
     *
     * @param session the paused session
     * @param breakpoint the breakpoint that triggered
     */
    default void onBreakpointHit(DebugSession session, Breakpoint breakpoint) {}

    /**
     * Fired after a step request has run to its stopping point.
     *
     * @param session the paused session
     * @param state the captured state at the new position
     */
    default void onStepComplete(DebugSession session, DebugState state) {}

    /**
     * Fired when an exception is raised inside the debugged code.
     *
     * @param session the session that raised it
     * @param exception the thrown exception object on the engine heap
     */
    default void onException(DebugSession session, ObjectInstance exception) {}

    /**
     * Fired on every session state transition.
     *
     * @param session the session that changed
     * @param oldState the state left behind
     * @param newState the state now in effect
     */
    default void onStateChange(DebugSession session, DebugSessionState oldState, DebugSessionState newState) {}
}
