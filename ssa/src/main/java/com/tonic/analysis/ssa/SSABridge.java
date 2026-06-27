package com.tonic.analysis.ssa;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.ssa.cfg.IRMethod;

/**
 * Bridges {@link CodeWriter} (the bytecode layer) and the SSA IR pipeline. This lives in the SSA
 * module so that the bytecode layer does not depend on SSA; the dependency runs ssa -&gt; bytecode.
 */
public final class SSABridge {

    private SSABridge() {
    }

    /**
     * Lifts the given writer's method bytecode to SSA-form IR.
     *
     * @return the IRMethod in SSA form
     */
    public static IRMethod toSSA(CodeWriter writer) {
        SSA ssa = new SSA(writer.getConstPool());
        return ssa.lift(writer.getMethodEntry());
    }

    /**
     * Lowers an SSA-form IRMethod back to bytecode and refreshes the writer to reflect it.
     *
     * @param irMethod the IRMethod to lower
     */
    public static void fromSSA(CodeWriter writer, IRMethod irMethod) {
        SSA ssa = new SSA(writer.getConstPool());
        ssa.lower(irMethod, writer.getMethodEntry());
        writer.reload();
    }

    /**
     * Lifts to SSA, runs the standard optimizations, and lowers back to bytecode.
     */
    public static void optimizeSSA(CodeWriter writer) {
        SSA ssa = new SSA(writer.getConstPool()).withStandardOptimizations();
        IRMethod irMethod = ssa.lift(writer.getMethodEntry());
        ssa.runTransforms(irMethod);
        ssa.lower(irMethod, writer.getMethodEntry());
        writer.reload();
    }
}
