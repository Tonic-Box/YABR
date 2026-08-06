package com.tonic.analysis.instruction;

/**
 * Implemented by instructions that reference a local-variable slot: the load/store family, {@code iinc},
 * {@code ret}, and the wide forms.
 */
public interface LocalVarInstruction
{
    /**
     * @return the local-variable slot this instruction reads or writes
     */
    int getVarIndex();
}
