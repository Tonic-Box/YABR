package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.utill.Opcode.*;

/**
 * Represents the JVM FLOAD instruction.
 */
@Getter
public class FLoadInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs an FLoadInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to load. For FLOAD_0-3, this is 0-3 respectively.
     */
    public FLoadInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, isShortForm(opcode) ? 1 : 2);
        this.varIndex = varIndex;
    }

    /**
     * Determines if the opcode is a short-form FLOAD instruction.
     *
     * @param opcode The opcode to check.
     * @return True if the opcode is FLOAD_0-3, false otherwise.
     */
    private static boolean isShortForm(int opcode) {
        return opcode >= FLOAD_0.getCode() && opcode <= FLOAD_3.getCode();
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the FLOAD opcode and its operand (if any) to the DataOutputStream.
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
     * @return The stack size change (pushes a float).
     */
    @Override
    public int getStackChange() {
        return 1;
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
        return String.format("FLOAD %d", varIndex);
    }
}
