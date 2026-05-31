package com.tonic.analysis.ssa.llvm.lift;

/**
 * Thrown when the lifter encounters LLVM IR it cannot parse or lift to SSA form.
 */
public class LlvmLiftException extends RuntimeException {

    public LlvmLiftException(String message) {
        super(message);
    }

    public LlvmLiftException(String message, Throwable cause) {
        super(message, cause);
    }
}
