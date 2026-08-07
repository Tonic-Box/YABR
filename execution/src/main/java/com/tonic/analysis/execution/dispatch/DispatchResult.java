package com.tonic.analysis.execution.dispatch;

/**
 * Outcome of dispatching a single instruction, directing the interpreter's next action.
 */
public enum DispatchResult
{
    /**
     * The instruction finished in place and the program counter already advanced; nothing
     * more is owed by the interpreter.
     */
    CONTINUE,
    /**
     * Control transfers to the branch target the dispatcher left on the context.
     */
    BRANCH,
    /**
     * A call was reached; the interpreter must resolve the target and push a new frame.
     */
    INVOKE,
    /**
     * The current frame completes and hands its return value, if any, to the caller.
     */
    RETURN,
    /**
     * The interpreter must raise an exception it detected itself, such as a division by zero,
     * rather than one the code threw explicitly.
     */
    THROW,
    /**
     * An {@code athrow} was executed; the thrown reference is on the stack and needs handler lookup.
     */
    ATHROW,
    /**
     * A field read is pending and must be serviced against the heap before execution resumes.
     */
    FIELD_GET,
    /**
     * A field write is pending and must be serviced against the heap before execution resumes.
     */
    FIELD_PUT,
    /**
     * An instance must be allocated for the pending {@code new}, before its constructor runs.
     */
    NEW_OBJECT,
    /**
     * An array must be allocated, covering the primitive, reference, and multi-dimensional forms.
     */
    NEW_ARRAY,
    /**
     * A cast check was performed; the dispatcher already applied it, so no follow-up is needed.
     */
    CHECKCAST,
    /**
     * A type test was performed; the dispatcher already pushed its result.
     */
    INSTANCEOF
}
