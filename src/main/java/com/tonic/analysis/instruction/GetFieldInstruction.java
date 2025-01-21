package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.FieldRefItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the GETFIELD and GETSTATIC instructions (0xB4, 0xB2).
 */
public class GetFieldInstruction extends Instruction {
    private final FieldType type;
    private final int fieldIndex;
    private final ConstPool constPool;

    /**
     * Enum representing the types of get field operations.
     */
    public enum FieldType {
        GETFIELD(0xB4, "getfield"),
        GETSTATIC(0xB2, "getstatic");

        private final int opcode;
        private final String mnemonic;

        FieldType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static FieldType fromOpcode(int opcode) {
            for (FieldType type : FieldType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a GetFieldInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param fieldIndex The constant pool index for the field reference.
     */
    public GetFieldInstruction(ConstPool constPool, int opcode, int offset, int fieldIndex) {
        super(opcode, offset, 3); // opcode + two bytes field index
        this.type = FieldType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid GetField opcode: " + opcode);
        }
        this.fieldIndex = fieldIndex;
        this.constPool = constPool;
    }

    /**
     * Writes the get field opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(fieldIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (depends on the field type).
     */
    @Override
    public int getStackChange() {
        // Pushes the field type onto the stack
        FieldRefItem field = (FieldRefItem) constPool.getItem(fieldIndex);
        switch (field.getDescriptor()) {
            case "J": // long
            case "D": // double
                return 2;
            default:
                return 1;
        }
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
     * Returns the field index used by this instruction.
     *
     * @return The constant pool index for the field reference.
     */
    public int getFieldIndex() {
        return fieldIndex;
    }

    /**
     * Resolves and returns a string representation of the field.
     *
     * @return The field as a string.
     */
    public String resolveField() {
        FieldRefItem field = (FieldRefItem) constPool.getItem(fieldIndex);
        return field.toString();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, field index, and resolved field.
     */
    @Override
    public String toString() {
        return String.format("%s #%d // %s", type.getMnemonic().toUpperCase(), fieldIndex, resolveField());
    }
}
