package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the JVM DUP instruction and its variants.
 */
public class DupInstruction extends Instruction
{
    private final DupType type;

    /**
     * The stack-duplication variants (DUP through DUP2_X2), each pairing a JVM opcode with its mnemonic.
     */
    public enum DupType
    {
        /**
         * Copies the top one-word value and pushes the copy on top of it.
         */
        DUP(0x59, "dup"),
        /**
         * Copies the top one-word value and inserts it beneath the one word below.
         */
        DUP_X1(0x5A, "dup_x1"),
        /**
         * Copies the top one-word value and inserts it beneath the two words below, as used
         * to stash an array reference under a long index or value.
         */
        DUP_X2(0x5B, "dup_x2"),
        /**
         * Copies the top two words, which is either one long or double or a pair of one-word values.
         */
        DUP2(0x5C, "dup2"),
        /**
         * Copies the top two words and inserts them beneath the one word below.
         */
        DUP2_X1(0x5D, "dup2_x1"),
        /**
         * Copies the top two words and inserts them beneath the two words below.
         */
        DUP2_X2(0x5E, "dup2_x2");

        private final int opcode;
        private final String mnemonic;

        DupType(int opcode, String mnemonic)
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
         * Looks up the duplication variant for a JVM opcode.
         * @param opcode the JVM opcode
         * @return the matching variant, or null if the opcode is not a DUP-family opcode
         */
        public static DupType fromOpcode(int opcode)
        {
            for (DupType type : DupType.values())
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
     * @return the duplication variant
     */
    public DupType getType()
    {
        return type;
    }

    /**
     * Constructs a DupInstruction.
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @throws IllegalArgumentException if the opcode is not a DUP-family opcode
     */
    public DupInstruction(int opcode, int offset)
    {
        super(opcode, offset, 1);
        this.type = DupType.fromOpcode(opcode);
        if (this.type == null)
        {
            throw new IllegalArgumentException("Invalid DUP opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the DUP opcode to the DataOutputStream.
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
     * @return The stack size change based on the DUP type.
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
     * Returns a string representation of the instruction.
     * @return The mnemonic of the DUP instruction.
     */
    @Override
    public String toString()
    {
        return type.getMnemonic().toUpperCase();
    }
}
