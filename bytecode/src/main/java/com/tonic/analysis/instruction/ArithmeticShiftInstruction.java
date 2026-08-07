package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents shift instructions (ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR).
 */
public class ArithmeticShiftInstruction extends Instruction
{
    private final ShiftType type;

    /**
     * The shift operation kinds, each pairing a JVM opcode with its mnemonic.
     */
    public enum ShiftType
    {
        /**
         * Left shift of an int, using the low five bits of the shift count.
         */
        ISHL(0x78, "ishl"),
        /**
         * Left shift of a long, using the low six bits of the int shift count.
         */
        LSHL(0x79, "lshl"),
        /**
         * Arithmetic right shift of an int, propagating the sign bit.
         */
        ISHR(0x7A, "ishr"),
        /**
         * Arithmetic right shift of a long, propagating the sign bit.
         */
        LSHR(0x7B, "lshr"),
        /**
         * Logical right shift of an int, filling the vacated high bits with zero.
         */
        IUSHR(0x7C, "iushr"),
        /**
         * Logical right shift of a long, filling the vacated high bits with zero.
         */
        LUSHR(0x7D, "lushr");

        private final int opcode;
        private final String mnemonic;

        ShiftType(int opcode, String mnemonic)
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
         * Looks up the shift type for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching type, or null if the opcode is not a shift opcode
         */
        public static ShiftType fromOpcode(int opcode)
        {
            for (ShiftType type : ShiftType.values())
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
     * Constructs an ArithmeticShiftInstruction.
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @throws IllegalArgumentException if the opcode is not a shift opcode
     */
    public ArithmeticShiftInstruction(int opcode, int offset)
    {
        super(opcode, offset, 1);
        this.type = ShiftType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid Shift opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeByte(opcode);
    }

    @Override
    public int getStackChange()
    {
        switch (type)
        {
            case ISHL:
            case ISHR:
            case IUSHR:
                return -1;
            case LSHL:
            case LSHR:
            case LUSHR:
                return -2;
            default:
                return 0;
        }
    }

    @Override
    public int getLocalChange()
    {
        return 0;
    }

    /**
     * @return the type
     */
    public ShiftType getType()
    {
        return type;
    }

    @Override
    public String toString()
    {
        return type.getMnemonic().toUpperCase();
    }
}
