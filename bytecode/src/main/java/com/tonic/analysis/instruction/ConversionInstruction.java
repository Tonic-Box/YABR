package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the type conversion instructions (I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F).
 */
public class ConversionInstruction extends Instruction
{
    private final ConversionType type;

    /**
     * The widening conversion kinds, each pairing a JVM opcode with its mnemonic.
     */
    public enum ConversionType
    {
        /**
         * Converts an int to a float, rounding to nearest when the magnitude
         * exceeds 24 bits of precision.
         */
        I2F(0x86, "i2f"),
        /**
         * Widens an int to a double without loss of value.
         */
        I2D(0x87, "i2d"),
        /**
         * Narrows a long to an int by keeping only the low 32 bits.
         */
        L2I(0x88, "l2i"),
        /**
         * Converts a long to a float, rounding to nearest when the magnitude
         * exceeds 24 bits of precision.
         */
        L2F(0x89, "l2f"),
        /**
         * Converts a long to a double, rounding to nearest when the magnitude
         * exceeds 53 bits of precision.
         */
        L2D(0x8A, "l2d"),
        /**
         * Converts a float to an int, truncating toward zero and clamping to the
         * int range; NaN becomes zero.
         */
        F2I(0x8B, "f2i"),
        /**
         * Converts a float to a long, truncating toward zero and clamping to the
         * long range; NaN becomes zero.
         */
        F2L(0x8C, "f2l"),
        /**
         * Widens a float to a double without loss of value.
         */
        F2D(0x8D, "f2d"),
        /**
         * Converts a double to an int, truncating toward zero and clamping to the
         * int range; NaN becomes zero.
         */
        D2I(0x8E, "d2i"),
        /**
         * Converts a double to a long, truncating toward zero and clamping to the
         * long range; NaN becomes zero.
         */
        D2L(0x8F, "d2l"),
        /**
         * Narrows a double to a float, rounding to nearest and overflowing to
         * infinity.
         */
        D2F(0x90, "d2f");

        private final int opcode;
        private final String mnemonic;

        ConversionType(int opcode, String mnemonic)
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
         * Looks up the conversion type for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching type, or null if the opcode is not a conversion opcode
         */
        public static ConversionType fromOpcode(int opcode)
        {
            for (ConversionType type : ConversionType.values())
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
     * Constructs a ConversionInstruction.
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @throws IllegalArgumentException if the opcode is not a conversion opcode
     */
    public ConversionInstruction(int opcode, int offset)
    {
        super(opcode, offset, 1);
        this.type = ConversionType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid Conversion opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the conversion opcode to the DataOutputStream.
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
     * @return The stack size change based on the conversion type.
     */
    @Override
    public int getStackChange()
    {
        switch (type)
        {
            case I2F:
            case I2D:
            case L2I:
            case L2F:
            case L2D:
            case F2I:
            case F2L:
            case F2D:
            case D2I:
            case D2L:
            case D2F:
                if (type == ConversionType.I2D || type == ConversionType.L2I || type == ConversionType.L2F ||
                        type == ConversionType.L2D || type == ConversionType.D2I || type == ConversionType.D2L ||
                        type == ConversionType.D2F)
                        {
                    return 0;
                }
                return 0;
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
     * Returns the type of conversion operation.
     * @return The ConversionType enum value.
     */
    public ConversionType getType()
    {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     * @return The mnemonic of the conversion instruction.
     */
    @Override
    public String toString()
    {
        return type.getMnemonic().toUpperCase();
    }
}
