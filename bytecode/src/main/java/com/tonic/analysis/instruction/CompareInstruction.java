package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the compare instructions (LCMP, FCMPL, FCMPG, DCMPL, DCMPG).
 */
public class CompareInstruction extends Instruction
{
    private final CompareType type;

    /**
     * The compare operation kinds, each pairing a JVM opcode with its mnemonic.
     */
    public enum CompareType
    {
        /**
         * Compares two longs, pushing -1, 0, or 1; longs have no NaN, so there is
         * only one variant.
         */
        LCMP(0x94, "lcmp"),
        /**
         * Compares two floats, pushing -1 when either operand is NaN.
         */
        FCMPL(0x95, "fcmpl"),
        /**
         * Compares two floats, pushing 1 when either operand is NaN.
         */
        FCMPG(0x96, "fcmpg"),
        /**
         * Compares two doubles, pushing -1 when either operand is NaN.
         */
        DCMPL(0x97, "dcmpl"),
        /**
         * Compares two doubles, pushing 1 when either operand is NaN.
         */
        DCMPG(0x98, "dcmpg");

        private final int opcode;
        private final String mnemonic;

        CompareType(int opcode, String mnemonic)
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
         * Looks up the compare type for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching type, or null if the opcode is not a compare opcode
         */
        public static CompareType fromOpcode(int opcode)
        {
            for (CompareType type : CompareType.values())
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
     * Constructs a CompareInstruction.
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @throws IllegalArgumentException if the opcode is not a compare opcode
     */
    public CompareInstruction(int opcode, int offset)
    {
        super(opcode, offset, 1);
        this.type = CompareType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid Compare opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the compare opcode to the DataOutputStream.
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeByte(opcode);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     * @return The stack size change (pops two values, pushes one int).
     */
    @Override
    public int getStackChange()
    {
        switch (type)
        {
            case LCMP:
            case DCMPL:
            case DCMPG:
            case FCMPL:
            case FCMPG:
                return -1;
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
     * Returns the type of compare operation.
     * @return The CompareType enum value.
     */
    public CompareType getType()
    {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     * @return The mnemonic of the compare instruction.
     */
    @Override
    public String toString()
    {
        return type.getMnemonic().toUpperCase();
    }
}
