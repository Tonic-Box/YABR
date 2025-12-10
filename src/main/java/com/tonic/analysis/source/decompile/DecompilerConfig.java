package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.ssa.transform.IRTransform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the ClassDecompiler.
 * Combines source emission settings with transform pipeline configuration.
 */
public class DecompilerConfig {

    private final SourceEmitterConfig emitterConfig;
    private final List<IRTransform> additionalTransforms;

    private DecompilerConfig(Builder builder) {
        this.emitterConfig = builder.emitterConfig;
        this.additionalTransforms = Collections.unmodifiableList(new ArrayList<>(builder.transforms));
    }

    /**
     * Returns the source emitter configuration.
     */
    public SourceEmitterConfig getEmitterConfig() {
        return emitterConfig;
    }

    /**
     * Returns the list of additional transforms to apply after baseline transforms.
     * The returned list is unmodifiable.
     */
    public List<IRTransform> getAdditionalTransforms() {
        return additionalTransforms;
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

        /**
         * Builds the DecompilerConfig.
         */
        public DecompilerConfig build() {
            return new DecompilerConfig(this);
        }
    }
}
