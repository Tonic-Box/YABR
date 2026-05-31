package com.tonic.analysis.ssa.llvm;

/**
 * An LLVM operand: a typed register reference ({@code %vN}), synthesized temporary ({@code %tN}),
 * or inline constant literal. Pairs the rendered operand text with its {@link LlvmType}.
 */
final class LlvmValue {

    final LlvmType type;
    final String text;

    private LlvmValue(LlvmType type, String text) {
        this.type = type;
        this.text = text;
    }

    /** A named SSA register {@code %v{id}} (used for every {@code SSAValue}, parameters included). */
    static LlvmValue register(LlvmType type, int ssaId) {
        return new LlvmValue(type, "%v" + ssaId);
    }

    /** A synthesized temporary {@code %tN} (disjoint from {@code %v} ids). */
    static LlvmValue temp(LlvmType type, String name) {
        return new LlvmValue(type, name);
    }

    /** An inline constant literal (decimal int/long, hex IEEE float/double, etc.). */
    static LlvmValue constant(LlvmType type, String literal) {
        return new LlvmValue(type, literal);
    }

    /** The "{@code <type> <operand>}" form used wherever LLVM wants a typed operand. */
    String typed() {
        return type.render() + " " + text;
    }

    @Override
    public String toString() {
        return text;
    }
}
