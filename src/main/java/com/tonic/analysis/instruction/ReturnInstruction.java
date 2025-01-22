package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.utill.ReturnType;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the return instructions (IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN).
 */
public class ReturnInstruction extends Instruction {
    private final ReturnType type;

    /**
     * Constructs a ReturnInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ReturnInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = ReturnType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Return opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the return opcode to the DataOutputStream.
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
     * @return The stack size change (pops the return value from the stack).
     */
    @Override
    public int getStackChange() {
        switch (type) {
            case IRETURN:
            case FRETURN:
            case ARETURN:
                return -1; // Pops one int, float, or reference
            case LRETURN:
            case DRETURN:
                return -2; // Pops one long or double
            case RETURN:
                return 0; // Pops nothing
            default:
                return 0;
        }
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
     * Returns the type of return operation.
     *
     * @return The ReturnType enum value.
     */
    public ReturnType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the return instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
