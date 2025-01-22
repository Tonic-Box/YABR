package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the NEWARRAY instruction (0xBC).
 */
@Getter
public class NewArrayInstruction extends Instruction {
    private final ArrayType arrayType;
    private final int count;

    /**
     * Enum representing the types of array creation.
     */
    @Getter
    public enum ArrayType {
        T_BOOLEAN(4, "newarray [boolean]"),
        T_CHAR(5, "newarray [char]"),
        T_FLOAT(6, "newarray [float]"),
        T_DOUBLE(7, "newarray [double]"),
        T_BYTE(8, "newarray [byte]"),
        T_SHORT(9, "newarray [short]"),
        T_INT(10, "newarray [int]"),
        T_LONG(11, "newarray [long]");

        private final int code;
        private final String description;

        ArrayType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public static ArrayType fromCode(int code) {
            for (ArrayType type : ArrayType.values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a NewArrayInstruction.
     *
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param typeCode  The type code of the array elements.
     * @param count     The number of elements in the array.
     */
    public NewArrayInstruction(int opcode, int offset, int typeCode, int count) {
        super(opcode, offset, 2); // opcode + one byte type code
        if (opcode != 0xBC) {
            throw new IllegalArgumentException("Invalid opcode for NewArrayInstruction: " + opcode);
        }
        this.arrayType = ArrayType.fromCode(typeCode);
        if (this.arrayType == null) {
            throw new IllegalArgumentException("Invalid array type code for NEWARRAY: " + typeCode);
        }
        this.count = count;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the NEWARRAY opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(arrayType.getCode());
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes a new array reference).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes a new array reference onto the stack
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
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and array type of the instruction.
     */
    @Override
    public String toString() {
        return String.format("NEWARRAY %s", arrayType.getDescription());
    }
}
