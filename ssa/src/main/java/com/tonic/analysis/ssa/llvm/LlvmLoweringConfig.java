package com.tonic.analysis.ssa.llvm;

/**
 * Configuration for {@link LlvmLowering}, following the project's builder-plus-presets convention.
 */
public final class LlvmLoweringConfig
{

    /**
     * How Java's object/reference/runtime model is lowered.
     */
    public enum ObjectModel
    {
        /**
         * Computational subset only.
         */
        NONE,
        /**
         * Full construct set.
         */
        RUNTIME_ABI
    }

    private final String targetTriple;
    private final String dataLayout;
    private final boolean emitDivisionGuards;
    private final ObjectModel objectModel;

    private LlvmLoweringConfig(Builder b)
    {
        this.targetTriple = b.targetTriple;
        this.dataLayout = b.dataLayout;
        this.emitDivisionGuards = b.emitDivisionGuards;
        this.objectModel = b.objectModel;
    }

    /**
     * @return the target triple emitted as a module header, or null to omit
     */
    public String getTargetTriple()
    {
        return targetTriple;
    }

    /**
     * @return the target datalayout emitted as a module header, or null to omit
     */
    public String getDataLayout()
    {
        return dataLayout;
    }

    /**
     * @return whether JVM-faithful guards are emitted around integer division cases that are
     *         undefined behavior in raw LLVM sdiv/srem; off by default
     */
    public boolean isEmitDivisionGuards()
    {
        return emitDivisionGuards;
    }

    /**
     * @return how the object/reference/runtime model is lowered; {@link ObjectModel#NONE} by default
     */
    public ObjectModel getObjectModel()
    {
        return objectModel;
    }

    /**
     * Creates the default preset: no target header, raw LLVM arithmetic, computational subset only.
     * @return the default configuration
     */
    public static LlvmLoweringConfig defaults()
    {
        return builder().build();
    }

    /**
     * Creates a preset enabling the full object/runtime-model lowering via the jvm_* runtime ABI.
     * @return the configuration with {@link ObjectModel#RUNTIME_ABI}
     */
    public static LlvmLoweringConfig fullObjectModel()
    {
        return builder().objectModel(ObjectModel.RUNTIME_ABI).build();
    }

    /**
     * @return a new builder with default settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Mutable builder for {@link LlvmLoweringConfig}.
     */
    public static final class Builder
    {
        private String targetTriple = null;
        private String dataLayout = null;
        private boolean emitDivisionGuards = false;
        private ObjectModel objectModel = ObjectModel.NONE;

        private Builder()
        {
        }

        /**
         * Sets the target triple emitted as a module header.
         * @param triple the target triple, or null to omit
         * @return this builder
         */
        public Builder targetTriple(String triple)
        {
            this.targetTriple = triple;
            return this;
        }

        /**
         * Sets the target datalayout emitted as a module header.
         * @param dataLayout the datalayout string, or null to omit
         * @return this builder
         */
        public Builder dataLayout(String dataLayout)
        {
            this.dataLayout = dataLayout;
            return this;
        }

        /**
         * Sets whether JVM-faithful guards are emitted around integer division.
         * @param emit true to emit the guards
         * @return this builder
         */
        public Builder emitDivisionGuards(boolean emit)
        {
            this.emitDivisionGuards = emit;
            return this;
        }

        /**
         * Sets how the object/reference/runtime model is lowered.
         * @param objectModel the object model; null selects {@link ObjectModel#NONE}
         * @return this builder
         */
        public Builder objectModel(ObjectModel objectModel)
        {
            this.objectModel = objectModel != null ? objectModel : ObjectModel.NONE;
            return this;
        }

        /**
         * @return the immutable configuration
         */
        public LlvmLoweringConfig build()
        {
            return new LlvmLoweringConfig(this);
        }
    }
}
