package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the narrowing conversion instructions (I2B, I2C, I2S).
 */
public class NarrowingConversionInstruction extends Instruction
{
    private final NarrowingType type;

    /**
     * The narrowing conversion kinds, each pairing a JVM opcode with its mnemonic.
     */
    public enum NarrowingType
    {
        /**
         * Truncates an int to 8 bits and sign-extends it back, the byte conversion.
         */
        I2B(0x91, "i2b"),
        /**
         * Truncates an int to 16 bits and zero-extends it back, the char conversion.
         */
        I2C(0x92, "i2c"),
        /**
         * Truncates an int to 16 bits and sign-extends it back, the short conversion.
         */
        I2S(0x93, "i2s");

        private final int opcode;
        private final String mnemonic;

        NarrowingType(int opcode, String mnemonic)
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
         * Looks up the narrowing type for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching type, or null if the opcode is not I2B, I2C, or I2S
         */
        public static NarrowingType fromOpcode(int opcode)
        {
            for (NarrowingType type : NarrowingType.values())
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
     * Constructs a NarrowingConversionInstruction.
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @throws IllegalArgumentException if the opcode is not a narrowing conversion opcode
     */
    public NarrowingConversionInstruction(int opcode, int offset)
    {
        super(opcode, offset, 1);
        this.type = NarrowingType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid Narrowing Conversion opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the narrowing conversion opcode to the DataOutputStream.
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
     * @return The stack size change (no net change).
     */
    @Override
    public int getStackChange()
    {
        return 0;
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
     * Returns the type of narrowing conversion operation.
     * @return The NarrowingType enum value.
     */
    public NarrowingType getType()
    {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     * @return The mnemonic of the narrowing conversion instruction.
     */
    @Override
    public String toString()
    {
        return type.getMnemonic().toUpperCase();
    }
}
