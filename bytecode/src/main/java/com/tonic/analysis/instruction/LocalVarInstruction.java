package com.tonic.analysis.instruction;

/**
 * Implemented by instructions that reference a local-variable slot: the load/store family,
 * {@code iinc}, {@code ret}, and the wide forms. Lets callers read the slot index without a
 * per-opcode {@code instanceof} chain.
 */
public interface LocalVarInstruction {
    int getVarIndex();
}
