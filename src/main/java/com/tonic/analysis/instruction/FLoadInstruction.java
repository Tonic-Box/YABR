package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the FLOAD instruction (0x17).
 */
public class FLoadInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs an FLoadInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to load.
     */
    public FLoadInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, 2);
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the FLOAD opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(varIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a float).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes a float onto the stack
    }

    /**
     * Returns the change in local variables caused by this instruction.
     *
     * @return The local variables size change (none).
     */
    @Override
    public int getLocalChange() {
        return 0;
    }

    /**
     * Returns the local variable index being loaded.
     *
     * @return The local variable index.
     */
    public int getVarIndex() {
        return varIndex;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and variable index of the instruction.
     */
    @Override
    public String toString() {
        return String.format("FLOAD %d", varIndex);
    }
}
