package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the ALOAD instructions (0x19, 0x2A-0x2D).
 */
@Getter
public class ALoadInstruction extends Instruction {
    /**
     * -- GETTER --
     *  Returns the local variable index being loaded.
     *
     * @return The local variable index.
     */
    private final int varIndex;

    /**
     * Constructs an ALoadInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to load. For ALOAD_0-3, this is 0-3 respectively.
     */
    public ALoadInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, isShortForm(opcode) ? 1 : 2); // Short-form ALOAD_0-3 have no operands, regular ALOAD has one operand byte
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Determines if the opcode is a short-form ALOAD instruction.
     *
     * @param opcode The opcode to check.
     * @return True if the opcode is ALOAD_0-3, false otherwise.
     */
    private static boolean isShortForm(int opcode) {
        return opcode >= 0x2A && opcode <= 0x2D;
    }

    /**
     * Writes the ALOAD opcode and its operand (if any) to the DataOutputStream.
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
     * @return The stack size change (pushes a reference).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes a reference onto the stack
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
        if (opcode == 0x19) { // ALOAD with operand
            return String.format("ALOAD %d", varIndex);
        } else { // ALOAD_0-3
            int index = opcode - 0x2A;
            return String.format("ALOAD_%d", index);
        }
    }
}
