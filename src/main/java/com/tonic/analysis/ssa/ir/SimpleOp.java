package com.tonic.analysis.ssa.ir;

public enum SimpleOp {
    ARRAYLENGTH,
    MONITORENTER,
    MONITOREXIT,
    ATHROW,
    GOTO,
    /** Captures the caught exception that the JVM places on the stack at an exception-handler entry into
     * the instruction's result. Emits no opcode itself - the surrounding result-store turns it into the
     * astore (or the value is consumed); without it the on-stack exception would leak past the handler. */
    CATCH
}
