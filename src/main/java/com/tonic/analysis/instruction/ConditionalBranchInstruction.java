package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the conditional branch instructions (IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
 * IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE).
 */
public class ConditionalBranchInstruction extends Instruction {
    private final BranchType type;
    private final short branchOffset;

    /**
     * Enum representing the types of conditional branch operations.
     */
    public enum BranchType {
        IFEQ(0x99, "ifeq"),
        IFNE(0x9A, "ifne"),
        IFLT(0x9B, "iflt"),
        IFGE(0x9C, "ifge"),
        IFGT(0x9D, "ifgt"),
        IFLE(0x9E, "ifle"),
        IF_ICMPEQ(0x9F, "if_icmpeq"),
        IF_ICMPNE(0xA0, "if_icmpne"),
        IF_ICMPLT(0xA1, "if_icmplt"),
        IF_ICMPGE(0xA2, "if_icmpge"),
        IF_ICMPGT(0xA3, "if_icmpgt"),
        IF_ICMPLE(0xA4, "if_icmple"),
        IF_ACMPEQ(0xA5, "if_acmpeq"),
        IF_ACMPNE(0xA6, "if_acmpne");

        private final int opcode;
        private final String mnemonic;

        BranchType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static BranchType fromOpcode(int opcode) {
            for (BranchType type : BranchType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a ConditionalBranchInstruction.
     *
     * @param opcode       The opcode of the instruction.
     * @param offset       The bytecode offset of the instruction.
     * @param branchOffset The branch target offset relative to current instruction.
     */
    public ConditionalBranchInstruction(int opcode, int offset, short branchOffset) {
        super(opcode, offset, 3); // opcode + two bytes branch offset
        this.type = BranchType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Conditional Branch opcode: " + opcode);
        }
        this.branchOffset = branchOffset;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the conditional branch opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(branchOffset);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change based on the branch type.
     */
    @Override
    public int getStackChange() {
        // Conditional branches pop one or two values based on the instruction
        switch (type) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
                return -1; // Pops one int
            case IF_ACMPEQ:
            case IF_ACMPNE:
                return -2; // Pops two references
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
                return -2; // Pops two ints
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
     * Returns the type of branch operation.
     *
     * @return The BranchType enum value.
     */
    public BranchType getType() {
        return type;
    }

    /**
     * Returns the branch offset.
     *
     * @return The branch target offset.
     */
    public int getBranchOffset() {
        return branchOffset;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and branch target of the instruction.
     */
    @Override
    public String toString() {
        return String.format("%s %d", type.getMnemonic().toUpperCase(), branchOffset);
    }
}
