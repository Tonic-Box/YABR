package com.tonic.analysis.ssa.llvm;

import lombok.Getter;

/**
 * Configuration for {@link LlvmLowering}, following the project's builder + presets convention
 * (see {@code DecompilerConfig}).
 */
@Getter
public final class LlvmLoweringConfig {

    /** Target triple emitted as a module header, or null to omit. */
    private final String targetTriple;
    /** Target datalayout emitted as a module header, or null to omit. */
    private final String dataLayout;
    /**
     * When true, emit JVM-faithful guards around integer division (divide-by-zero / INT_MIN/-1),
     * which are undefined behavior in raw LLVM {@code sdiv}/{@code srem}. Off by default in v1 —
     * the divergence is documented and this flag is the designed extension point.
     */
    private final boolean emitDivisionGuards;

    private LlvmLoweringConfig(Builder b) {
        this.targetTriple = b.targetTriple;
        this.dataLayout = b.dataLayout;
        this.emitDivisionGuards = b.emitDivisionGuards;
    }

    /** No target header; raw LLVM arithmetic (no division guards). */
    public static LlvmLoweringConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String targetTriple = null;
        private String dataLayout = null;
        private boolean emitDivisionGuards = false;

        private Builder() {
        }

        public Builder targetTriple(String triple) {
            this.targetTriple = triple;
            return this;
        }

        public Builder dataLayout(String dataLayout) {
            this.dataLayout = dataLayout;
            return this;
        }

        public Builder emitDivisionGuards(boolean emit) {
            this.emitDivisionGuards = emit;
            return this;
        }

        public LlvmLoweringConfig build() {
            return new LlvmLoweringConfig(this);
        }
    }
}
