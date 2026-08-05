package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.*;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the LDC instruction (0x12).
 */
public class LdcInstruction extends Instruction
{

    /**
     * The kinds of constant-pool entry an ldc-family instruction can load.
     */
    public enum ConstantType
    {
        /**
         * A {@code CONSTANT_Integer} entry, occupying one stack word.
         */
        INTEGER,
        /**
         * A {@code CONSTANT_Float} entry, occupying one stack word.
         */
        FLOAT,
        /**
         * A {@code CONSTANT_Long} entry, occupying two stack words.
         */
        LONG,
        /**
         * A {@code CONSTANT_Double} entry, occupying two stack words.
         */
        DOUBLE,
        /**
         * A {@code CONSTANT_String} entry, loaded as an interned string reference.
         */
        STRING,
        /**
         * A {@code CONSTANT_Class} entry, loaded as a class literal reference.
         */
        CLASS,
        /**
         * A {@code CONSTANT_MethodHandle} entry, loaded as a direct handle to a field or method.
         */
        METHOD_HANDLE,
        /**
         * A {@code CONSTANT_MethodType} entry, loaded as an erased method signature.
         */
        METHOD_TYPE,
        /**
         * A {@code CONSTANT_Dynamic} entry, whose value and stack width are decided by its
         * bootstrap method and descriptor.
         */
        DYNAMIC,
        /**
         * The index is out of range or names an entry kind the ldc family cannot load.
         */
        UNKNOWN
    }

    /**
     * {@code ldc} - single-byte constant-pool index.
     */
    private static final int LDC_OPCODE = 0x12;
    /**
     * {@code ldc_w} - two-byte index; required once the index exceeds 255.
     */
    private static final int LDC_W_OPCODE = 0x13;

    private final int cpIndex;
    private final ConstPool constPool;

    /**
     * Constructs an LdcInstruction. The physical encoding (narrow {@code ldc} vs wide {@code ldc_w}) is
     * derived from {@code cpIndex}: a one-byte {@code ldc} cannot hold an index above 255, so such an
     * index transparently widens to {@code ldc_w}. This makes index truncation impossible no matter how
     * large the target constant pool has grown.
     * @param constPool The constant pool associated with the class.
     * @param opcode    The nominal opcode (ignored; the form is derived from {@code cpIndex}).
     * @param offset    The bytecode offset of the instruction.
     * @param cpIndex   The constant pool index.
     */
    public LdcInstruction(ConstPool constPool, int opcode, int offset, int cpIndex)
    {
        super(cpIndex > 0xFF ? LDC_W_OPCODE : LDC_OPCODE, offset, cpIndex > 0xFF ? 3 : 2);
        this.constPool = constPool;
        this.cpIndex = cpIndex;
    }

    /**
     * @return the cp index
     */
    public int getCpIndex()
    {
        return cpIndex;
    }

    /**
     * @return the const pool
     */
    public ConstPool getConstPool()
    {
        return constPool;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the LDC opcode and its operand to the DataOutputStream.
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        if (cpIndex > 0xFF)
        {
            dos.writeByte(LDC_W_OPCODE);
            dos.writeShort(cpIndex);
        }
        else
        {
            dos.writeByte(LDC_OPCODE);
            dos.writeByte(cpIndex);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     * @return The stack size change (pushes a reference or primitive).
     */
    @Override
    public int getStackChange()
    {
        if (cpIndex <= 0)
        {
            return 1;
        }
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof DoubleItem || item instanceof LongItem)
        {
            return 2;
        }
        if (item instanceof ConstantDynamicItem)
        {
            String desc = ((ConstantDynamicItem) item).getDescriptor();
            if ("J".equals(desc) || "D".equals(desc))
            {
                return 2;
            }
        }
        return 1;
    }

    /**
     * Classifies the referenced constant-pool entry.
     * @return the constant kind, or UNKNOWN for an invalid index or unrecognized entry
     */
    public ConstantType getConstantType()
    {
        if (cpIndex <= 0)
        {
            return ConstantType.UNKNOWN;
        }
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof IntegerItem)
        {
            return ConstantType.INTEGER;
        }
        else if (item instanceof FloatItem)
        {
            return ConstantType.FLOAT;
        }
        else if (item instanceof LongItem)
        {
            return ConstantType.LONG;
        }
        else if (item instanceof DoubleItem)
        {
            return ConstantType.DOUBLE;
        }
        else if (item instanceof StringRefItem)
        {
            return ConstantType.STRING;
        }
        else if (item instanceof ClassRefItem)
        {
            return ConstantType.CLASS;
        }
        else if (item instanceof MethodHandleItem)
        {
            return ConstantType.METHOD_HANDLE;
        }
        else if (item instanceof MethodTypeItem)
        {
            return ConstantType.METHOD_TYPE;
        }
        else if (item instanceof ConstantDynamicItem)
        {
            return ConstantType.DYNAMIC;
        }
        return ConstantType.UNKNOWN;
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
     * Resolves and returns a string representation of the constant.
     * @return The constant as a string.
     */
    public String resolveConstant()
    {
        if (cpIndex <= 0)
        {
            return "INVALID_CP_INDEX(" + cpIndex + ")";
        }
        Item<?> item = constPool.getItem(cpIndex);
        if (item instanceof StringRefItem)
        {
            return "\"" + ((StringRefItem) item).getValue() + "\"";
        }
        else if (item instanceof IntegerItem)
        {
            return String.valueOf(((IntegerItem) item).getValue());
        }
        else if (item instanceof FloatItem)
        {
            return String.valueOf(((FloatItem) item).getValue());
        }
        else if (item instanceof ClassRefItem)
        {
            return ((ClassRefItem) item).getClassName();
        }
        else if (item instanceof MethodHandleItem)
        {
            return "MethodHandle#" + cpIndex;
        }
        else if (item instanceof MethodTypeItem)
        {
            MethodTypeItem mtItem = (MethodTypeItem) item;
            Item<?> descItem = constPool.getItem(mtItem.getValue());
            if (descItem instanceof Utf8Item)
            {
                return "MethodType[" + ((Utf8Item) descItem).getValue() + "]";
            }
            return "MethodType#" + cpIndex;
        }
        else if (item instanceof ConstantDynamicItem)
        {
            ConstantDynamicItem cdItem = (ConstantDynamicItem) item;
            return "ConstantDynamic[" + cdItem.getName() + ":" + cdItem.getDescriptor() + "]";
        }
        else
        {
            return "UnknownConstant";
        }
    }

    /**
     * Returns a string representation of the instruction.
     * @return The mnemonic, constant pool index, and resolved constant.
     */
    @Override
    public String toString()
    {
        return String.format("LDC #%d // %s", cpIndex, resolveConstant());
    }
}
