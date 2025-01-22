package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the DSTORE instructions (0x39, 0x47-0x4A).
 */
@Getter
public class DStoreInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs a DStoreInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to store. For DSTORE_0-3, this is 0-3 respectively.
     */
    public DStoreInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, (opcode == 0x39) ? 2 : 1); // DSTORE has 1 operand byte, DSTORE_0-3 have no operands
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the DSTORE opcode and its operand (if any) to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        if (opcode == 0x39) { // DSTORE with operand
            dos.writeByte(varIndex);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops a double).
     */
    @Override
    public int getStackChange() {
        return -2; // Pops a double from the stack (occupies two stack slots)
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
     * @return The mnemonic and variable index of the instruction.
     */
    @Override
    public String toString() {
        if (opcode == 0x39) { // DSTORE with operand
            return String.format("DSTORE %d", varIndex);
        } else { // DSTORE_0-3
            int index = opcode - 0x47;
            return String.format("DSTORE_%d", index);
        }
    }
}
