package com.tonic.analysis.source.decompile;

import com.tonic.analysis.ssa.transform.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Preset configurations for IR transform pipelines during decompilation.
 * Each preset defines a list of additional transforms to run after the baseline
 * transforms (ControlFlowReducibility, DuplicateBlockMerging).
 */
public enum TransformPreset {

    /**
     * No additional transforms - only baseline transforms are applied.
     */
    NONE {
        @Override
        public List<IRTransform> getTransforms() {
            return Collections.emptyList();
        }
    },

    /**
     * Minimal cleanup - safe transforms with low risk of issues.
     */
    MINIMAL {
        @Override
        public List<IRTransform> getTransforms() {
            List<IRTransform> transforms = new ArrayList<>();
            transforms.add(new ConstantFolding());
            transforms.add(new CopyPropagation());
            return transforms;
        }
    },

    /**
     * Standard optimization - balanced cleanup for better readability.
     */
    STANDARD {
        @Override
        public List<IRTransform> getTransforms() {
            List<IRTransform> transforms = new ArrayList<>();
            transforms.add(new ConstantFolding());
            transforms.add(new AlgebraicSimplification());
            transforms.add(new CopyPropagation());
            transforms.add(new RedundantCopyElimination());
            transforms.add(new DeadCodeElimination());
            return transforms;
        }
    },

    /**
     * Aggressive optimization - maximum simplification for cleanest output.
     */
    AGGRESSIVE {
        @Override
        public List<IRTransform> getTransforms() {
            List<IRTransform> transforms = new ArrayList<>();
            transforms.add(new ConstantFolding());
            transforms.add(new AlgebraicSimplification());
            transforms.add(new StrengthReduction());
            transforms.add(new Reassociate());
            transforms.add(new CopyPropagation());
            transforms.add(new CommonSubexpressionElimination());
            transforms.add(new RedundantCopyElimination());
            transforms.add(new PhiConstantPropagation());
            transforms.add(new DeadCodeElimination());
            return transforms;
        }
    };

    /**
     * Returns the list of transforms for this preset.
     * Each call returns a new list instance that can be safely modified.
     *
     * @return list of IR transforms for this preset
     */
    public abstract List<IRTransform> getTransforms();
}
