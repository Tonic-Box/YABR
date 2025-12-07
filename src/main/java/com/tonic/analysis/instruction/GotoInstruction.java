package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the JVM GOTO and GOTO_W instructions.
 */
public class GotoInstruction extends Instruction {
    @Getter
    private final GotoType type;
    private final int branchOffsetInt;
    private final short branchOffsetShort;

    @Getter
    public enum GotoType {
        GOTO(0xA7, "goto"),
        GOTO_W(0xC8, "goto_w");

        private final int opcode;
        private final String mnemonic;

        GotoType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public static GotoType fromOpcode(int opcode) {
            for (GotoType type : GotoType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a GotoInstruction.
     *
     * @param opcode       The opcode of the instruction.
     * @param offset       The bytecode offset of the instruction.
     * @param branchOffset The branch target offset relative to current instruction.
     */
    public GotoInstruction(int opcode, int offset, int branchOffset) {
        super(opcode, offset, (opcode == 0xA7) ? 3 : 5);
        this.type = GotoType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid GOTO opcode: " + opcode);
        }
        this.branchOffsetInt = branchOffset;
        this.branchOffsetShort = -1;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Constructs a GotoInstruction.
     *
     * @param opcode       The opcode of the instruction.
     * @param offset       The bytecode offset of the instruction.
     * @param branchOffset The branch target offset relative to current instruction.
     */
    public GotoInstruction(int opcode, int offset, short branchOffset) {
        super(opcode, offset, (opcode == 0xA7) ? 3 : 5);
        this.type = GotoType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid GOTO opcode: " + opcode);
        }
        this.branchOffsetShort = branchOffset;
        this.branchOffsetInt = -1;
    }

    /**
     * Writes the GOTO opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        if (type == GotoType.GOTO) {
            dos.writeShort(getBranchOffset());
        } else if (type == GotoType.GOTO_W) {
            dos.writeInt(getBranchOffsetWide());
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (none).
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
     * Returns the branch offset.
     *
     * @return The branch target offset.
     */
    public short getBranchOffset() {
        return branchOffsetShort;
    }

    /**
     * Returns the wide branch offset.
     *
     * @return The wide branch target offset.
     */
    public int getBranchOffsetWide() {
        return branchOffsetInt;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and branch target of the instruction.
     */
    @Override
    public String toString() {
        return String.format("%s %d", type.getMnemonic().toUpperCase(), getBranchOffset());
    }
}
