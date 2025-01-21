package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.DoubleItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.LongItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LDC2_W instruction (0x14).
 */
public class Ldc2WInstruction extends Instruction {
    private final int cpIndex;
    private final ConstPool constPool;

    /**
     * Constructs an Ldc2WInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param cpIndex   The constant pool index.
     */
    public Ldc2WInstruction(ConstPool constPool, int opcode, int offset, int cpIndex) {
        super(opcode, offset, 3);
        this.constPool = constPool;
        this.cpIndex = cpIndex;
    }

    /**
     * Writes the LDC2_W opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(cpIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a double or long).
     */
    @Override
    public int getStackChange() {
        // LDC2_W is used for Long and Double, which occupy two stack slots
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof DoubleItem || item instanceof LongItem) {
            return 2;
        }
        return 0; // Invalid usage
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
     * Returns the constant pool index used by this instruction.
     *
     * @return The constant pool index.
     */
    public int getCpIndex() {
        return cpIndex;
    }

    /**
     * Resolves and returns a string representation of the constant.
     *
     * @return The constant as a string.
     */
    public String resolveConstant() {
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof DoubleItem || item instanceof LongItem) {
            return String.valueOf(((Number) item.getValue()).doubleValue());
        }
        return "UnknownConstant";
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, constant pool index, and resolved constant.
     */
    @Override
    public String toString() {
        return String.format("LDC2_W #%d // %s", cpIndex, resolveConstant());
    }
}
