package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the ATHROW instruction (0xBF).
 */
public class ATHROWInstruction extends Instruction {

    /**
     * Constructs an ATHROWInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ATHROWInstruction(int opcode, int offset) {
        super(opcode, offset, 1); // opcode only
        if (opcode != 0xBF) {
            throw new IllegalArgumentException("Invalid opcode for ATHROWInstruction: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the ATHROW opcode to the DataOutputStream.
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
     * @return The stack size change (pops one throwable reference, does not push).
     */
    @Override
    public int getStackChange() {
        return -1; // Pops one throwable reference from the stack
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
        return "ATHROW";
    }
}
