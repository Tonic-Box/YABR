package com.tonic.analysis.execution.debug;

/**
 * How far the debugger runs before pausing again.
 */
public enum StepMode
{
    /**
     * Run freely; only a breakpoint or an explicit pause request stops
     * execution.
     */
    RUN,
    /**
     * Pause after every instruction, including the first instruction of any
     * method that gets called.
     */
    STEP_INTO,
    /**
     * Execute one instruction of the current frame, running any call it makes to
     * completion rather than pausing inside the callee.
     */
    STEP_OVER,
    /**
     * Run until the call stack becomes shallower than it was when the step
     * began, that is, until the current frame returns.
     */
    STEP_OUT,
    /**
     * Run until the current method reaches a caller-supplied offset; both the
     * offset and the method signature must match before pausing.
     */
    RUN_TO_CURSOR
}
