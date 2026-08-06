package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the conditional branch instructions.
 */
public class ConditionalBranchInstruction extends Instruction
{
    private final BranchType type;
    private final short branchOffset;

    /**
     * The conditional branch kinds, each pairing a JVM opcode with its mnemonic.
     */
    public enum BranchType
    {
        /**
         * Branches when the popped int is zero, the usual test for a false boolean.
         */
        IFEQ(0x99, "ifeq"),
        /**
         * Branches when the popped int is non-zero, the usual test for a true boolean.
         */
        IFNE(0x9A, "ifne"),
        /**
         * Branches when the popped int is {@code < 0}.
         */
        IFLT(0x9B, "iflt"),
        /**
         * Branches when the popped int is {@code >= 0}.
         */
        IFGE(0x9C, "ifge"),
        /**
         * Branches when the popped int is {@code > 0}.
         */
        IFGT(0x9D, "ifgt"),
        /**
         * Branches when the popped int is {@code <= 0}.
         */
        IFLE(0x9E, "ifle"),
        /**
         * Branches when two popped ints are equal.
         */
        IF_ICMPEQ(0x9F, "if_icmpeq"),
        /**
         * Branches when two popped ints differ.
         */
        IF_ICMPNE(0xA0, "if_icmpne"),
        /**
         * Branches when the first of two popped ints is {@code <} the second.
         */
        IF_ICMPLT(0xA1, "if_icmplt"),
        /**
         * Branches when the first of two popped ints is {@code >=} the second.
         */
        IF_ICMPGE(0xA2, "if_icmpge"),
        /**
         * Branches when the first of two popped ints is {@code >} the second.
         */
        IF_ICMPGT(0xA3, "if_icmpgt"),
        /**
         * Branches when the first of two popped ints is {@code <=} the second.
         */
        IF_ICMPLE(0xA4, "if_icmple"),
        /**
         * Branches when two popped references point at the same object.
         */
        IF_ACMPEQ(0xA5, "if_acmpeq"),
        /**
         * Branches when two popped references do not point at the same object.
         */
        IF_ACMPNE(0xA6, "if_acmpne"),
        /**
         * Branches when the popped reference is null.
         */
        IFNULL(0xC6, "ifnull"),
        /**
         * Branches when the popped reference is not null.
         */
        IFNONNULL(0xC7, "ifnonnull");

        private final int opcode;
        private final String mnemonic;

        BranchType(int opcode, String mnemonic)
        {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        /**
         * @return the opcode
         */
        public int getOpcode()
        {
            return opcode;
        }

        /**
         * @return the mnemonic
         */
        public String getMnemonic()
        {
            return mnemonic;
        }

        /**
         * Looks up the branch type for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching type, or null if the opcode is not a conditional branch opcode
         */
        public static BranchType fromOpcode(int opcode)
        {
            for (BranchType type : BranchType.values())
            {
                if (type.opcode == opcode)
                {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a ConditionalBranchInstruction.
     * @param opcode       The opcode of the instruction.
     * @param offset       The bytecode offset of the instruction.
     * @param branchOffset The branch target offset relative to current instruction.
     * @throws IllegalArgumentException if the opcode is not a conditional branch opcode
     */
    public ConditionalBranchInstruction(int opcode, int offset, short branchOffset)
    {
        super(opcode, offset, 3);
        this.type = BranchType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid Conditional Branch opcode: " + opcode);
        }
        this.branchOffset = branchOffset;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the conditional branch opcode and its operands to the DataOutputStream.
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeByte(opcode);
        dos.writeShort(branchOffset);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     * @return The stack size change based on the branch type.
     */
    @Override
    public int getStackChange()
    {
        switch (type)
        {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IFNULL:
            case IFNONNULL:
                return -1;
            case IF_ACMPEQ:
            case IF_ACMPNE:
                return -2;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
                return -2;
            default:
                return 0;
        }
    }

    /**
     * Returns the change in local variables caused by this instruction.
     * @return The local variables size change (none).
     */
    @Override
    public int getLocalChange()
    {
        return 0;
    }

    /**
     * Returns the type of branch operation.
     * @return The BranchType enum value.
     */
    public BranchType getType()
    {
        return type;
    }

    /**
     * Returns the branch offset.
     * @return The branch target offset.
     */
    public int getBranchOffset()
    {
        return branchOffset;
    }

    /**
     * Returns a string representation of the instruction.
     * @return The mnemonic and branch target of the instruction.
     */
    @Override
    public String toString()
    {
        return String.format("%s %d", type.getMnemonic().toUpperCase(), branchOffset);
    }
}
