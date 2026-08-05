package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.NameRecoveryStrategy;
import com.tonic.analysis.ssa.transform.IRTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the ClassDecompiler.
 * Combines source emission settings with transform pipeline configuration.
 */
public class DecompilerConfig {

    /**
     * -- GETTER --
     *  Returns the source emitter configuration.
     */
    private final SourceEmitterConfig emitterConfig;
    /**
     * -- GETTER --
     *  Returns the list of additional transforms to apply after baseline transforms.
     *  The returned list is unmodifiable.
     */
    private final List<IRTransform> additionalTransforms;
    /** How variable names are recovered: from debug info where present, or synthetic regardless. */
    private final NameRecoveryStrategy nameRecoveryStrategy;

    private DecompilerConfig(Builder builder) {
        this.emitterConfig = builder.emitterConfig;
        this.additionalTransforms = Collections.unmodifiableList(new ArrayList<>(builder.transforms));
        this.nameRecoveryStrategy = builder.nameRecoveryStrategy;
    }

    public SourceEmitterConfig getEmitterConfig() {
        return emitterConfig;
    }

    public List<IRTransform> getAdditionalTransforms() {
        return additionalTransforms;
    }

    /**
     * How variable names are recovered. The default prefers a name recorded in the {@code LocalVariableTable};
     * the other modes produce the same names whether or not a class was compiled with debug info, which is what
     * output compared across obfuscated and non-obfuscated builds needs.
     */
    public NameRecoveryStrategy getNameRecoveryStrategy() {
        return nameRecoveryStrategy;
    }

    /**
     * Creates a new builder for DecompilerConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a default configuration with no additional transforms.
     */
    public static DecompilerConfig defaults() {
        return builder().build();
    }

    /**
     * Builder for DecompilerConfig.
     */
    public static class Builder {
        private SourceEmitterConfig emitterConfig = SourceEmitterConfig.defaults();
        private final List<IRTransform> transforms = new ArrayList<>();
        private NameRecoveryStrategy nameRecoveryStrategy =
                NameRecoveryStrategy.PREFER_DEBUG_INFO;

        private Builder() {}

        /**
         * Sets the source emitter configuration.
         */
        public Builder emitterConfig(SourceEmitterConfig config) {
            this.emitterConfig = config != null ? config : SourceEmitterConfig.defaults();
            return this;
        }

        /**
         * Applies a preset, adding all transforms from the preset to the pipeline.
         * Can be called multiple times to combine presets, or combined with addTransform().
         *
         * @param preset the transform preset to apply
         */
        public Builder preset(TransformPreset preset) {
            if (preset != null) {
                transforms.addAll(preset.getTransforms());
            }
            return this;
        }

        /**
         * Adds a single transform to the pipeline.
         * Transforms are applied in the order they are added.
         *
         * @param transform the transform to add
         */
        public Builder addTransform(IRTransform transform) {
            if (transform != null) {
                transforms.add(transform);
            }
            return this;
        }

        /**
         * Adds multiple transforms to the pipeline.
         * Transforms are applied in the order they appear in the list.
         *
         * @param transforms the transforms to add
         */
        public Builder addTransforms(List<IRTransform> transforms) {
            if (transforms != null) {
                for (IRTransform t : transforms) {
                    if (t != null) {
                        this.transforms.add(t);
                    }
                }
            }
            return this;
        }

        /**
         * Clears all transforms from the pipeline.
         * Useful if you want to reset after applying a preset.
         */
        public Builder clearTransforms() {
            transforms.clear();
            return this;
        }

        /** Sets how variable names are recovered. */
        public Builder nameRecoveryStrategy(
                NameRecoveryStrategy strategy) {
            this.nameRecoveryStrategy = strategy == null
                    ? NameRecoveryStrategy.PREFER_DEBUG_INFO
                    : strategy;
            return this;
        }

        public DecompilerConfig build() {
            return new DecompilerConfig(this);
        }
    }
}
