package com.tonic.analysis.instruction;

import com.tonic.utill.Opcode;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the IINC instruction (0x84).
 */
public class IIncInstruction extends Instruction {
    private final int varIndex;
    private final int constValue;

    /**
     * Constructs an IIncInstruction.
     *
     * @param opcode     The opcode of the instruction.
     * @param offset     The bytecode offset of the instruction.
     * @param varIndex   The index of the local variable to increment.
     * @param constValue The constant value to add to the local variable.
     */
    public IIncInstruction(int opcode, int offset, int varIndex, int constValue) {
        super(opcode, offset, 3); // opcode + modifiedOpcode + varIndex (2 bytes) + constValue (2 bytes)
        this.varIndex = varIndex;
        this.constValue = constValue;
    }

    /**
     * Writes the IINC opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(Opcode.WIDE.getCode()); // Assuming WIDE is used here for IINC with wider indices
        dos.writeShort(varIndex);
        dos.writeShort(constValue);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (none).
     */
    @Override
    public int getStackChange() {
        return 0; // IINC modifies a local variable; no stack change
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
     * Returns the local variable index being incremented.
     *
     * @return The local variable index.
     */
    public int getVarIndex() {
        return varIndex;
    }

    /**
     * Returns the constant value to add to the local variable.
     *
     * @return The constant value.
     */
    public int getConstValue() {
        return constValue;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, variable index, and constant value.
     */
    @Override
    public String toString() {
        return String.format("IINC %d %d", varIndex, constValue);
    }
}
