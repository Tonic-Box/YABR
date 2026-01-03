package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.utill.Opcode.*;

/**
 * Represents the LSTORE instructions (0x37, 0x3F-0x42).
 */
@Getter
public class LStoreInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs an LStoreInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to store. For LSTORE_0-3, this is 0-3 respectively.
     */
    public LStoreInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, (opcode == LSTORE.getCode()) ? 2 : 1);
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the LSTORE opcode and its operand (if any) to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        if (opcode == LSTORE.getCode()) {
            dos.writeByte(varIndex);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops a long).
     */
    @Override
    public int getStackChange() {
        return -2;
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
        if (opcode == LSTORE.getCode()) {
            return String.format("LSTORE %d", varIndex);
        } else {
            int index = opcode - LSTORE_0.getCode();
            return String.format("LSTORE_%d", index);
        }
    }
}
