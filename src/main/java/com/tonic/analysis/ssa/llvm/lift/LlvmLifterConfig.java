package com.tonic.analysis.ssa.llvm.lift;

/**
 * Configuration for {@link LlvmLifter}. Currently a placeholder for future options; exists to keep
 * the API symmetric with {@link com.tonic.analysis.ssa.llvm.LlvmLoweringConfig}.
 */
public final class LlvmLifterConfig {

    private LlvmLifterConfig() {
    }

    public static LlvmLifterConfig defaults() {
        return new LlvmLifterConfig();
    }
}
