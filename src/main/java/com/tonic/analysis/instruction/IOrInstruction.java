package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the IOR instruction (0x80).
 */
public class IOrInstruction extends Instruction {

    /**
     * Constructs an IOrInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public IOrInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the IOR opcode to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops two ints, pushes one int).
     */
    @Override
    public int getStackChange() {
        return -1; // Pops two ints, pushes one int
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
     * @return The mnemonic of the instruction.
     */
    @Override
    public String toString() {
        return "IOR";
    }
}
