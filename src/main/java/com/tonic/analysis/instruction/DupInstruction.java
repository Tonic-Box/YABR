package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the DUP instruction and its variants (0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E).
 */
public class DupInstruction extends Instruction {
    private final DupType type;

    /**
     * Enum representing the types of DUP instructions.
     */
    public enum DupType {
        DUP(0x59, "dup"),
        DUP_X1(0x5A, "dup_x1"),
        DUP_X2(0x5B, "dup_x2"),
        DUP2(0x5C, "dup2"),
        DUP2_X1(0x5D, "dup2_x1"),
        DUP2_X2(0x5E, "dup2_x2");

        private final int opcode;
        private final String mnemonic;

        DupType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static DupType fromOpcode(int opcode) {
            for (DupType type : DupType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a DupInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public DupInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = DupType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid DUP opcode: " + opcode);
        }
    }

    /**
     * Writes the DUP opcode to the DataOutputStream.
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
     * @return The stack size change based on the DUP type.
     */
    @Override
    public int getStackChange() {
        // DUP variants generally do not change the stack size
        return 0;
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
     * Returns the type of DUP instruction.
     *
     * @return The DupType enum value.
     */
    public DupType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the DUP instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
