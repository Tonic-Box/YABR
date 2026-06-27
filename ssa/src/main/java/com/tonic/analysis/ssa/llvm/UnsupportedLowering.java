package com.tonic.analysis.ssa.llvm;

/**
 * The single extension seam for everything outside the v1 computational subset.
 *
 * <p>Object/heap/dispatch/exception constructs (field and array access, allocation, virtual /
 * interface / dynamic dispatch, type checks, athrow, monitors, reference values) are not lowered
 * by v1. They all funnel through {@link #reject(String)} so the subset boundary is explicit and
 * localized: when a managed-runtime backend is added, these throw-sites become intrinsic emission.
 */
final class UnsupportedLowering {

    private UnsupportedLowering() {
    }

    static RuntimeException reject(String op) {
        return new UnsupportedOperationException("LLVM lowering: " + op + " not yet supported");
    }
}
