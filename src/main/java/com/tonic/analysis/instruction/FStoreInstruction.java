package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the FSTORE instructions (0x38, 0x43-0x46).
 */
@Getter
public class FStoreInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs an FStoreInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to store. For FSTORE_0-3, this is 0-3 respectively.
     */
    public FStoreInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, isShortForm(opcode) ? 1 : 2); // Short-form FSTORE_0-3 have no operands, regular FSTORE has one operand byte
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Determines if the opcode is a short-form FSTORE instruction.
     *
     * @param opcode The opcode to check.
     * @return True if the opcode is FSTORE_0-3, false otherwise.
     */
    private static boolean isShortForm(int opcode) {
        return opcode >= 0x43 && opcode <= 0x46;
    }

    /**
     * Writes the FSTORE opcode and its operand (if any) to the DataOutputStream.
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
     * @return The stack size change (pops a float).
     */
    @Override
    public int getStackChange() {
        return -1; // Pops a float from the stack
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
        if (opcode == 0x38) { // FSTORE with operand
            return String.format("FSTORE %d", varIndex);
        } else { // FSTORE_0-3
            int index = opcode - 0x43;
            return String.format("FSTORE_%d", index);
        }
    }
}
