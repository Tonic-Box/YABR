package com.tonic.analysis.ssa.llvm;

import lombok.Getter;

/**
 * Configuration for {@link LlvmLowering}, following the project's builder + presets convention
 * (see {@code DecompilerConfig}).
 */
@Getter
public final class LlvmLoweringConfig {

    /**
     * Selects how Java's object/reference/runtime model is lowered.
     *
     * <ul>
     *   <li>{@link #NONE} — computational subset only (v1 behavior): references, fields, arrays,
     *       object allocation, dispatch, casts, exceptions, and monitors route to
     *       {@link UnsupportedLowering}. Output is self-contained and {@code lli}-runnable.</li>
     *   <li>{@link #RUNTIME_ABI} — full construct set: references map to opaque {@code ptr}, and
     *       object operations lower to {@code call}s into a documented {@code jvm_*} runtime ABI
     *       (declared externs). Static fields lower to LLVM globals. Output is valid IR but not
     *       runnable standalone — a runtime library must implement the ABI + EH personality.</li>
     * </ul>
     */
    public enum ObjectModel {
        NONE,
        RUNTIME_ABI
    }

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
    /** How the object/reference/runtime model is lowered. Defaults to {@link ObjectModel#NONE}. */
    private final ObjectModel objectModel;

    private LlvmLoweringConfig(Builder b) {
        this.targetTriple = b.targetTriple;
        this.dataLayout = b.dataLayout;
        this.emitDivisionGuards = b.emitDivisionGuards;
        this.objectModel = b.objectModel;
    }

    /** No target header; raw LLVM arithmetic (no division guards); computational subset only. */
    public static LlvmLoweringConfig defaults() {
        return builder().build();
    }

    /** Enables the full object/reference/runtime-model lowering via the {@code jvm_*} runtime ABI. */
    public static LlvmLoweringConfig fullObjectModel() {
        return builder().objectModel(ObjectModel.RUNTIME_ABI).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String targetTriple = null;
        private String dataLayout = null;
        private boolean emitDivisionGuards = false;
        private ObjectModel objectModel = ObjectModel.NONE;

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

        public Builder objectModel(ObjectModel objectModel) {
            this.objectModel = objectModel != null ? objectModel : ObjectModel.NONE;
            return this;
        }

        public LlvmLoweringConfig build() {
            return new LlvmLoweringConfig(this);
        }
    }
}
