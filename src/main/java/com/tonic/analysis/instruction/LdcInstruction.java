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

    public enum ConstantType {
        INTEGER,
        FLOAT,
        LONG,
        DOUBLE,
        STRING,
        CLASS,
        METHOD_HANDLE,
        METHOD_TYPE,
        DYNAMIC,
        UNKNOWN
    }

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
        if (item instanceof ConstantDynamicItem) {
            String desc = ((ConstantDynamicItem) item).getDescriptor();
            if ("J".equals(desc) || "D".equals(desc)) {
                return 2;
            }
        }
        return 1;
    }

    public ConstantType getConstantType() {
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof IntegerItem) {
            return ConstantType.INTEGER;
        } else if (item instanceof FloatItem) {
            return ConstantType.FLOAT;
        } else if (item instanceof LongItem) {
            return ConstantType.LONG;
        } else if (item instanceof DoubleItem) {
            return ConstantType.DOUBLE;
        } else if (item instanceof StringRefItem) {
            return ConstantType.STRING;
        } else if (item instanceof ClassRefItem) {
            return ConstantType.CLASS;
        } else if (item instanceof MethodHandleItem) {
            return ConstantType.METHOD_HANDLE;
        } else if (item instanceof MethodTypeItem) {
            return ConstantType.METHOD_TYPE;
        } else if (item instanceof ConstantDynamicItem) {
            return ConstantType.DYNAMIC;
        }
        return ConstantType.UNKNOWN;
    }

    public ConstPool getConstPool() {
        return constPool;
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
        } else if (item instanceof MethodHandleItem) {
            return "MethodHandle#" + cpIndex;
        } else if (item instanceof MethodTypeItem) {
            MethodTypeItem mtItem = (MethodTypeItem) item;
            Item<?> descItem = constPool.getItem(mtItem.getValue());
            if (descItem instanceof Utf8Item) {
                return "MethodType[" + ((Utf8Item) descItem).getValue() + "]";
            }
            return "MethodType#" + cpIndex;
        } else if (item instanceof ConstantDynamicItem) {
            ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
            return "ConstantDynamic[" + cdItem.getName() + ":" + cdItem.getDescriptor() + "]";
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
