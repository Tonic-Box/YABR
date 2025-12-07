package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.cfg.IRMethod;

/**
 * Interface for IR transformation passes.
 */
public interface IRTransform {

    /**
     * Gets the name of this transformation.
     *
     * @return the transformation name
     */
    String getName();

    /**
     * Runs the transformation on the specified method.
     *
     * @param method the method to transform
     * @return true if the method was modified
     */
    boolean run(IRMethod method);
}
