package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LDC2_W instruction (0x14).
 */
public class Ldc2WInstruction extends Instruction {
    @Getter
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

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
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
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof DoubleItem || item instanceof LongItem) {
            return 2;
        }
        return 0;
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

    public LdcInstruction.ConstantType getConstantType() {
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof LongItem) {
            return LdcInstruction.ConstantType.LONG;
        } else if (item instanceof DoubleItem) {
            return LdcInstruction.ConstantType.DOUBLE;
        } else if (item instanceof ConstantDynamicItem) {
            return LdcInstruction.ConstantType.DYNAMIC;
        }
        return LdcInstruction.ConstantType.UNKNOWN;
    }

    public ConstPool getConstPool() {
        return constPool;
    }

    public String resolveConstant() {
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof LongItem) {
            return String.valueOf(((LongItem) item).getValue()) + "L";
        } else if (item instanceof DoubleItem) {
            return String.valueOf(((DoubleItem) item).getValue());
        } else if (item instanceof ConstantDynamicItem) {
            ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
            return "ConstantDynamic[" + cdItem.getName() + ":" + cdItem.getDescriptor() + "]";
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
