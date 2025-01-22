package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the compare instructions (LCMP, FCMPL, FCMPG, DCMPL, DCMPG).
 */
public class CompareInstruction extends Instruction {
    private final CompareType type;

    /**
     * Enum representing the types of compare operations.
     */
    @Getter
    public enum CompareType {
        LCMP(0x94, "lcmp"),
        FCMPL(0x95, "fcmpl"),
        FCMPG(0x96, "fcmpg"),
        DCMPL(0x97, "dcmpl"),
        DCMPG(0x98, "dcmpg");

        private final int opcode;
        private final String mnemonic;

        CompareType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public static CompareType fromOpcode(int opcode) {
            for (CompareType type : CompareType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a CompareInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public CompareInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = CompareType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Compare opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the compare opcode to the DataOutputStream.
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
     * @return The stack size change (pops two values, pushes one int).
     */
    @Override
    public int getStackChange() {
        // Pops two values and pushes one int result
        switch (type) {
            case LCMP:
            case DCMPL:
            case DCMPG:
                return -1; // Pops two longs/doubles (occupying two slots each), pushes one int
            case FCMPL:
            case FCMPG:
                return -1; // Pops two floats (one slot each), pushes one int
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
     * Returns the type of compare operation.
     *
     * @return The CompareType enum value.
     */
    public CompareType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the compare instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
