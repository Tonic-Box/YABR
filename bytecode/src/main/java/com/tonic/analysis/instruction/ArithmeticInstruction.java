package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents arithmetic instructions (IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM).
 */
public class ArithmeticInstruction extends Instruction
{
    private final ArithmeticType type;

    /**
     * The arithmetic operation kinds, each pairing a JVM opcode with its mnemonic.
     */
    public enum ArithmeticType
    {
        /**
         * Adds two ints, wrapping silently on overflow.
         */
        IADD(0x60, "iadd"),
        /**
         * Adds two longs, wrapping silently on overflow; each operand is two stack words.
         */
        LADD(0x61, "ladd"),
        /**
         * Adds two floats, rounding to the nearest representable float.
         */
        FADD(0x62, "fadd"),
        /**
         * Adds two doubles, rounding to the nearest representable double.
         */
        DADD(0x63, "dadd"),
        /**
         * Subtracts the topmost int from the one beneath it, wrapping on overflow.
         */
        ISUB(0x64, "isub"),
        /**
         * Subtracts the topmost long from the one beneath it, wrapping on overflow.
         */
        LSUB(0x65, "lsub"),
        /**
         * Subtracts the topmost float from the one beneath it.
         */
        FSUB(0x66, "fsub"),
        /**
         * Subtracts the topmost double from the one beneath it.
         */
        DSUB(0x67, "dsub"),
        /**
         * Multiplies two ints, keeping only the low 32 bits of the product.
         */
        IMUL(0x68, "imul"),
        /**
         * Multiplies two longs, keeping only the low 64 bits of the product.
         */
        LMUL(0x69, "lmul"),
        /**
         * Multiplies two floats, rounding to the nearest representable float.
         */
        FMUL(0x6A, "fmul"),
        /**
         * Multiplies two doubles, rounding to the nearest representable double.
         */
        DMUL(0x6B, "dmul"),
        /**
         * Divides two ints, truncating toward zero and throwing on a zero divisor.
         */
        IDIV(0x6C, "idiv"),
        /**
         * Divides two longs, truncating toward zero and throwing on a zero divisor.
         */
        LDIV(0x6D, "ldiv"),
        /**
         * Divides two floats; a zero divisor yields infinity or NaN instead of throwing.
         */
        FDIV(0x6E, "fdiv"),
        /**
         * Divides two doubles; a zero divisor yields infinity or NaN instead of throwing.
         */
        DDIV(0x6F, "ddiv"),
        /**
         * Int remainder, taking the sign of the dividend and throwing on a zero divisor.
         */
        IREM(0x70, "irem"),
        /**
         * Long remainder, taking the sign of the dividend and throwing on a zero divisor.
         */
        LREM(0x71, "lrem"),
        /**
         * Float remainder, taking the sign of the dividend; a zero divisor yields NaN.
         */
        FREM(0x72, "frem"),
        /**
         * Double remainder, taking the sign of the dividend; a zero divisor yields NaN.
         */
        DREM(0x73, "drem");

        private final int opcode;
        private final String mnemonic;

        ArithmeticType(int opcode, String mnemonic)
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
         * Looks up the arithmetic type for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching type, or null if the opcode is not an arithmetic opcode
         */
        public static ArithmeticType fromOpcode(int opcode)
        {
            for (ArithmeticType type : ArithmeticType.values())
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
     * Constructs an ArithmeticInstruction.
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @throws IllegalArgumentException if the opcode is not an arithmetic opcode
     */
    public ArithmeticInstruction(int opcode, int offset)
    {
        super(opcode, offset, 1);
        this.type = ArithmeticType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid Arithmetic opcode: " + opcode);
        }
    }

    /**
     * @return the arithmetic operation type
     */
    public ArithmeticType getType()
    {
        return type;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the arithmetic opcode to the DataOutputStream.
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
     * @return The stack size change based on the arithmetic operation type.
     */
    @Override
    public int getStackChange()
    {
        switch (type)
        {
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
                return -1;
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
                return -2;
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return -1;
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
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
     * Returns a string representation of the instruction.
     * @return The mnemonic of the arithmetic instruction.
     */
    @Override
    public String toString()
    {
        return type.getMnemonic().toUpperCase();
    }
}
