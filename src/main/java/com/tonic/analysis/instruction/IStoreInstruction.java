package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.utill.Opcode.*;

/**
 * Represents the ISTORE instructions (0x36, 0x3B-0x3E).
 */
@Getter
public class IStoreInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs an IStoreInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to store.
     */
    public IStoreInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, isShortForm(opcode) ? 1 : 2);
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Determines if the opcode is a short-form ISTORE instruction.
     *
     * @param opcode The opcode to check.
     * @return True if the opcode is ISTORE_0-3, false otherwise.
     */
    private static boolean isShortForm(int opcode) {
        return opcode >= ISTORE_0.getCode() && opcode <= ISTORE_3.getCode();
    }

    /**
     * Writes the ISTORE opcode and its operand (if any) to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        if (!isShortForm(opcode)) {
            dos.writeByte(varIndex);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops an int).
     */
    @Override
    public int getStackChange() {
        return -1;
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
        if (opcode == ISTORE.getCode()) {
            return String.format("ISTORE %d", varIndex);
        } else {
            int index = opcode - ISTORE_0.getCode();
            return String.format("ISTORE_%d", index);
        }
    }
}
