package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the JVM IINC instruction.
 */
@Getter
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
        super(opcode, offset, 3);
        this.varIndex = varIndex;
        this.constValue = constValue;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
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
        dos.writeByte(varIndex);
        dos.writeByte(constValue);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (none).
     */
    @Override
    public int getStackChange() {
        return 0;
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
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, variable index, and constant value.
     */
    @Override
    public String toString() {
        return String.format("IINC %d %d", varIndex, constValue);
    }
}
