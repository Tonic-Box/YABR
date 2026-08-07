package com.tonic.util;

/**
 * Encoded length of a single instruction in a raw bytecode array, for scanners that walk code without
 * parsing it into instruction objects.
 */
public final class InstructionLength
{

    private InstructionLength()
    {
    }

    /**
     * The encoded length of the instruction at an offset, including its opcode byte. All fixed-length
     * instructions derive from {@link Opcode#getOperandCount()}, so the only cases spelled out here are
     * the three whose length depends on the code around them.
     * @param code the raw bytecode
     * @param offset the offset of the instruction's opcode byte
     * @return the length in bytes, or -1 if the instruction is truncated or malformed
     */
    public static int at(byte[] code, int offset)
    {
        if (code == null || offset < 0 || offset >= code.length)
        {
            return -1;
        }

        int opcode = Byte.toUnsignedInt(code[offset]);

        if (opcode == Opcode.TABLESWITCH.getCode())
        {
            int padding = padding(offset);
            int base = offset + 1 + padding;
            if (base + 12 > code.length)
            {
                return -1;
            }
            int low = readInt(code, base + 4);
            int high = readInt(code, base + 8);
            if (low > high || (long) high - low + 1 > code.length)
            {
                return -1;
            }
            return 1 + padding + 12 + (high - low + 1) * 4;
        }

        if (opcode == Opcode.LOOKUPSWITCH.getCode())
        {
            int padding = padding(offset);
            int base = offset + 1 + padding;
            if (base + 8 > code.length)
            {
                return -1;
            }
            int pairs = readInt(code, base + 4);
            if (pairs < 0 || (long) pairs * 8 > code.length)
            {
                return -1;
            }
            return 1 + padding + 8 + pairs * 8;
        }

        if (opcode == Opcode.WIDE.getCode())
        {
            if (offset + 1 >= code.length)
            {
                return -1;
            }
            return Byte.toUnsignedInt(code[offset + 1]) == Opcode.IINC.getCode() ? 6 : 4;
        }

        return 1 + Opcode.fromCode(opcode).getOperandCount();
    }

    private static int padding(int offset)
    {
        return (4 - ((offset + 1) % 4)) % 4;
    }

    private static int readInt(byte[] code, int offset)
    {
        return ((code[offset] & 0xFF) << 24)
                | ((code[offset + 1] & 0xFF) << 16)
                | ((code[offset + 2] & 0xFF) << 8)
                | (code[offset + 3] & 0xFF);
    }
}
