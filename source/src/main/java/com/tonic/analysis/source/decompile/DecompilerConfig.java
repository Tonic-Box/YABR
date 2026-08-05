package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.emit.SourceEmitterConfig;
import com.tonic.analysis.source.recovery.NameRecoveryStrategy;
import com.tonic.analysis.ssa.transform.IRTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the ClassDecompiler: source emission settings, the additional
 * transform pipeline, and the name recovery strategy.
 */
public class DecompilerConfig
{

    private final SourceEmitterConfig emitterConfig;
    private final List<IRTransform> additionalTransforms;
    /**
     * How variable names are recovered: from debug info where present, or synthetic regardless.
     */
    private final NameRecoveryStrategy nameRecoveryStrategy;

    private DecompilerConfig(Builder builder)
    {
        this.emitterConfig = builder.emitterConfig;
        this.additionalTransforms = Collections.unmodifiableList(new ArrayList<>(builder.transforms));
        this.nameRecoveryStrategy = builder.nameRecoveryStrategy;
    }

    /**
     * @return the emitter config
     */
    public SourceEmitterConfig getEmitterConfig()
    {
        return emitterConfig;
    }

    /**
     * @return the unmodifiable list of transforms applied after the baseline transforms
     */
    public List<IRTransform> getAdditionalTransforms()
    {
        return additionalTransforms;
    }

    /**
     * @return how variable names are recovered; the default prefers a name recorded in the
     *         {@code LocalVariableTable}, the other modes name identically with or without
     *         debug info, which output compared across obfuscated builds needs
     */
    public NameRecoveryStrategy getNameRecoveryStrategy()
    {
        return nameRecoveryStrategy;
    }

    /**
     * @return a new builder with default settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * @return a default configuration with no additional transforms
     */
    public static DecompilerConfig defaults()
    {
        return builder().build();
    }

    /**
     * Builder for DecompilerConfig.
     */
    public static class Builder
    {
        private SourceEmitterConfig emitterConfig = SourceEmitterConfig.defaults();
        private final List<IRTransform> transforms = new ArrayList<>();
        private NameRecoveryStrategy nameRecoveryStrategy =
                NameRecoveryStrategy.PREFER_DEBUG_INFO;

        private Builder() {}

        /**
         * Sets the source emitter configuration.
         * @param config the emitter configuration; defaults are used when null
         * @return this builder
         */
        public Builder emitterConfig(SourceEmitterConfig config)
        {
            this.emitterConfig = config != null ? config : SourceEmitterConfig.defaults();
            return this;
        }

        /**
         * Adds all transforms from a preset to the pipeline; presets combine across calls.
         * @param preset the transform preset to apply; ignored when null
         * @return this builder
         */
        public Builder preset(TransformPreset preset)
        {
            if (preset != null)
            {
                transforms.addAll(preset.getTransforms());
            }
            return this;
        }

        /**
         * Adds a single transform to the pipeline; transforms apply in insertion order.
         * @param transform the transform to add; ignored when null
         * @return this builder
         */
        public Builder addTransform(IRTransform transform)
        {
            if (transform != null)
            {
                transforms.add(transform);
            }
            return this;
        }

        /**
         * Adds multiple transforms to the pipeline in list order.
         * @param transforms the transforms to add; null entries and a null list are ignored
         * @return this builder
         */
        public Builder addTransforms(List<IRTransform> transforms)
        {
            if (transforms != null)
            {
                for (IRTransform t : transforms)
                {
                    if (t != null)
                    {
                        this.transforms.add(t);
                    }
                }
            }
            return this;
        }

        /**
         * Clears all transforms from the pipeline, for example to reset after a preset.
         * @return this builder
         */
        public Builder clearTransforms()
        {
            transforms.clear();
            return this;
        }

        /**
         * Sets how variable names are recovered.
         * @param strategy the strategy; PREFER_DEBUG_INFO is used when null
         * @return this builder
         */
        public Builder nameRecoveryStrategy(NameRecoveryStrategy strategy)
        {
            this.nameRecoveryStrategy = strategy == null
                    ? NameRecoveryStrategy.PREFER_DEBUG_INFO
                    : strategy;
            return this;
        }

        /**
         * Builds the immutable configuration.
         * @return the built configuration
         */
        public DecompilerConfig build()
        {
            return new DecompilerConfig(this);
        }
    }
}
