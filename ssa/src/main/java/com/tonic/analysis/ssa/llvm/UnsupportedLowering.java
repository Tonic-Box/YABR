package com.tonic.analysis.ssa.llvm;

/**
 * The single extension seam for everything outside the v1 computational subset.
 */
final class UnsupportedLowering
{

    private UnsupportedLowering()
    {
    }

    static RuntimeException reject(String op)
    {
        return new UnsupportedOperationException("LLVM lowering: " + op + " not yet supported");
    }
}
