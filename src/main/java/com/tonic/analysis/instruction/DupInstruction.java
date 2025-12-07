package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the JVM DUP instruction and its variants.
 */
@Getter
public class DupInstruction extends Instruction {
    private final DupType type;

    @Getter
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
     * Returns the type of DUP instruction.
     *
     * @return The DupType enum value.
     */
    public DupType getType() {
        return type;
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

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
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
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the DUP instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
