package com.tonic.analysis.pattern;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

/**
 * Functional interface for pattern matching on IR instructions.
 */
@FunctionalInterface
public interface PatternMatcher {

    /**
     * Tests if the given instruction matches this pattern.
     *
     * @param instruction the instruction to test
     * @param method the containing IR method
     * @param sourceMethod the source method entry
     * @param classFile the containing class file
     * @return true if the instruction matches the pattern
     */
    boolean matches(IRInstruction instruction, IRMethod method, MethodEntry sourceMethod, ClassFile classFile);
}
