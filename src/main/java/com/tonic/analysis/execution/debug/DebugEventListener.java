package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.heap.ObjectInstance;

public interface DebugEventListener {

    default void onSessionStart(DebugSession session) {}

    default void onSessionStop(DebugSession session, BytecodeResult result) {}

    default void onBreakpointHit(DebugSession session, Breakpoint breakpoint) {}

    default void onStepComplete(DebugSession session, DebugState state) {}

    default void onException(DebugSession session, ObjectInstance exception) {}

    default void onStateChange(DebugSession session, DebugSessionState oldState, DebugSessionState newState) {}
}
