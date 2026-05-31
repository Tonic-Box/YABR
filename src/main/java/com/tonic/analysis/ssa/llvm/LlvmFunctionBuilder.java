package com.tonic.analysis.ssa.llvm;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the body lines of one LLVM function and vends synthesized temporary names.
 *
 * <p>SSA values are named {@code %v{id}} directly from their {@code SSAValue} id (so phi
 * forward-references and loop back-edges resolve symbolically with no ordering pass). Synthesized
 * intermediates (3-way compare expansion, multi-step conversions) use {@code %t{n}} from
 * {@link #freshTemp()} — a namespace disjoint from {@code %v} ids, so they can never collide.
 */
final class LlvmFunctionBuilder {

    private final List<String> lines = new ArrayList<>();
    private int tempCounter = 0;
    private int labelCounter = 0;

    /** Emits a block label line, e.g. {@code B3:}. */
    void label(String blockLabel) {
        lines.add(blockLabel + ":");
    }

    /** Emits an indented instruction line. */
    void emit(String instruction) {
        lines.add("  " + instruction);
    }

    /** A fresh synthesized temporary register name, e.g. {@code %t0}. */
    String freshTemp() {
        return "%t" + (tempCounter++);
    }

    /**
     * A fresh synthesized block label name, e.g. {@code L0} (referenced as {@code %L0}). Used for
     * {@code invoke} normal-destination continuations and landingpad blocks — disjoint from the
     * {@code B{id}} labels minted from IR block ids.
     */
    String freshLabel() {
        return "L" + (labelCounter++);
    }

    List<String> lines() {
        return lines;
    }
}
