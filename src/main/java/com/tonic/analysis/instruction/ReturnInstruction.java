package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the return instructions (IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN).
 */
public class ReturnInstruction extends Instruction {
    private final ReturnType type;

    /**
     * Enum representing the types of return operations.
     */
    public enum ReturnType {
        IRETURN(0xAC, "ireturn"),
        LRETURN(0xAD, "lreturn"),
        FRETURN(0xAE, "freturn"),
        DRETURN(0xAF, "dreturn"),
        ARETURN(0xB0, "areturn"),
        RETURN_(0xB1, "return");

        private final int opcode;
        private final String mnemonic;

        ReturnType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static ReturnType fromOpcode(int opcode) {
            for (ReturnType type : ReturnType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

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
            case RETURN_:
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
