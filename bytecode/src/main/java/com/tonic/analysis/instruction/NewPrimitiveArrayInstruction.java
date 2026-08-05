package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the NEWARRAY instruction (0xBC).
 */
public class NewPrimitiveArrayInstruction extends Instruction
{
    private final ArrayType arrayType;
    private final int typeCode;
    private final int count;

    /**
     * The primitive element types NEWARRAY can allocate, each pairing an atype code with a description.
     */
    public enum ArrayType
    {
        /**
         * Boolean elements, atype 4; the JVM stores them one byte apiece.
         */
        T_BOOLEAN(4, "newarray [boolean]"),
        /**
         * Unsigned 16-bit character elements, atype 5.
         */
        T_CHAR(5, "newarray [char]"),
        /**
         * 32-bit floating point elements, atype 6.
         */
        T_FLOAT(6, "newarray [float]"),
        /**
         * 64-bit floating point elements, atype 7.
         */
        T_DOUBLE(7, "newarray [double]"),
        /**
         * Signed 8-bit elements, atype 8; boolean arrays share this storage width.
         */
        T_BYTE(8, "newarray [byte]"),
        /**
         * Signed 16-bit elements, atype 9.
         */
        T_SHORT(9, "newarray [short]"),
        /**
         * Signed 32-bit elements, atype 10.
         */
        T_INT(10, "newarray [int]"),
        /**
         * Signed 64-bit elements, atype 11.
         */
        T_LONG(11, "newarray [long]");

        private final int code;
        private final String description;

        ArrayType(int code, String description)
        {
            this.code = code;
            this.description = description;
        }

        /**
         * @return the code
         */
        public int getCode()
        {
            return code;
        }

        /**
         * @return the description
         */
        public String getDescription()
        {
            return description;
        }

        /**
         * Looks up the element type for a NEWARRAY atype code.
         * @param code the atype operand (4-11)
         * @return the matching element type, or null if the code is not a valid atype
         */
        public static ArrayType fromCode(int code)
        {
            for (ArrayType type : ArrayType.values())
            {
                if (type.code == code)
                {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a NewPrimitiveArrayInstruction.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param typeCode  The type code of the array elements.
     * @param count     The number of elements in the array.
     * @throws IllegalArgumentException if the opcode is not NEWARRAY (0xBC)
     */
    public NewPrimitiveArrayInstruction(int opcode, int offset, int typeCode, int count)
    {
        super(opcode, offset, 2);
        if (opcode != 0xBC)
        {
            throw new IllegalArgumentException("Invalid opcode for NewPrimitiveArrayInstruction: " + opcode);
        }
        this.typeCode = typeCode;
        this.arrayType = ArrayType.fromCode(typeCode);
        this.count = count;
    }

    /**
     * @return the array type
     */
    public ArrayType getArrayType()
    {
        return arrayType;
    }

    /**
     * @return the type code
     */
    public int getTypeCode()
    {
        return typeCode;
    }

    /**
     * @return the count
     */
    public int getCount()
    {
        return count;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor)
    {
        visitor.visit(this);
    }

    /**
     * Writes the NEWARRAY opcode and its operand to the DataOutputStream.
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeByte(opcode);
        dos.writeByte(typeCode);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     * @return The stack size change: pops the element count and pushes the array reference (net 0).
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
     * @return The mnemonic and array type of the instruction.
     */
    @Override
    public String toString()
    {
        return String.format("NEWARRAY %s",
                arrayType != null ? arrayType.getDescription() : "unknown_atype_" + typeCode);
    }
}
