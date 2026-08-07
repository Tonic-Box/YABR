package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;

/**
 * Interface for class-level transformation passes.
 */
public interface ClassTransform
{

    /**
     * Gets the name of this transformation.
     * @return the transformation name
     */
    String getName();

    /**
     * Runs the transformation on the specified class.
     * @param classFile the class file to transform
     * @param ssa the SSA processor for lifting/lowering methods
     * @return true if the class was modified
     */
    boolean run(ClassFile classFile, SSA ssa);
}
