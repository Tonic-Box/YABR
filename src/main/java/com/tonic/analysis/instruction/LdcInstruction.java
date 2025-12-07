package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LDC instruction (0x12).
 */
public class LdcInstruction extends Instruction {
    @Getter
    private final int cpIndex;
    private final ConstPool constPool;

    /**
     * Constructs an LdcInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param cpIndex   The constant pool index.
     */
    public LdcInstruction(ConstPool constPool, int opcode, int offset, int cpIndex) {
        super(opcode, offset, 2);
        this.constPool = constPool;
        this.cpIndex = cpIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the LDC opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(cpIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a reference or primitive).
     */
    @Override
    public int getStackChange() {
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
        return String.format("LDC #%d // %s", cpIndex, resolveConstant());
    }
}
