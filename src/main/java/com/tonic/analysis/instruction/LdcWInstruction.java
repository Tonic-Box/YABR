package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LDC_W instruction (0x13).
 */
public class LdcWInstruction extends Instruction {
    @Getter
    private final int cpIndex;
    private final ConstPool constPool;

    /**
     * Constructs an LdcWInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param cpIndex   The constant pool index.
     */
    public LdcWInstruction(ConstPool constPool, int opcode, int offset, int cpIndex) {
        super(opcode, offset, 3);
        this.constPool = constPool;
        this.cpIndex = cpIndex;
        Logger.info("Initialized LdcWInstruction at offset " + offset + " with cpIndex=" + cpIndex + " and length=" + length);
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the LDC_W opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(cpIndex);
        Logger.info("Writing LDC_W: Opcode=0x" + Integer.toHexString(opcode) +
                ", cpIndex=" + cpIndex + " (" + String.format("0x%04X", cpIndex) + ")");
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a reference or primitive).
     */
    @Override
    public int getStackChange() {
        // Depending on the constant type, stack change can vary
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof DoubleItem || item instanceof LongItem) {
            return 2;
        }
        return 1;
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
     * Resolves and returns a string representation of the constant.
     *
     * @return The constant as a string.
     */
    public String resolveConstant() {
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof StringRefItem) {
            return "\"" + ((StringRefItem) item).getValue() + "\"";
        } else if (item instanceof IntegerItem) {
            return String.valueOf(((IntegerItem) item).getValue());
        } else if (item instanceof FloatItem) {
            return String.valueOf(((FloatItem) item).getValue());
        } else if (item instanceof ClassRefItem) {
            return ((ClassRefItem) item).getClassName();
        } else {
            return "UnknownConstant";
        }
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, constant pool index, and resolved constant.
     */
    @Override
    public String toString() {
        return String.format("LDC_W #%d // %s", cpIndex, resolveConstant());
    }
}
