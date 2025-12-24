package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.FieldRefItem;
import com.tonic.parser.constpool.NameAndTypeRefItem;
import com.tonic.parser.constpool.Utf8Item;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the PUTFIELD and PUTSTATIC instructions (0xB5, 0xB3).
 */
public class PutFieldInstruction extends Instruction {
    private final FieldType type;
    private final int fieldIndex;
    private final ConstPool constPool;

    /**
     * Enum representing the types of put field operations.
     */
    public enum FieldType {
        PUTFIELD(0xB5, "putfield"),
        PUTSTATIC(0xB3, "putstatic");

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
     * Constructs a PutFieldInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param fieldIndex The constant pool index for the field reference.
     */
    public PutFieldInstruction(ConstPool constPool, int opcode, int offset, int fieldIndex) {
        super(opcode, offset, 3);
        this.type = FieldType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid PutField opcode: " + opcode);
        }
        this.fieldIndex = fieldIndex;
        this.constPool = constPool;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the put field opcode and its operand to the DataOutputStream.
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
     * @return The stack size change (pops the value to store and possibly the object reference).
     */
    @Override
    public int getStackChange() {
        FieldRefItem field = (FieldRefItem) constPool.getItem(fieldIndex);
        switch (field.getDescriptor()) {
            case "J":
            case "D":
                return -3;
            default:
                return -2;
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
     * Returns the field name.
     *
     * @return The field name.
     */
    public String getFieldName() {
        FieldRefItem field = (FieldRefItem) constPool.getItem(fieldIndex);
        int nameAndTypeIndex = field.getValue().getNameAndTypeIndex();
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(nameAndTypeIndex);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getNameIndex());
        return utf8.getValue();
    }

    /**
     * Returns the field descriptor.
     *
     * @return The field descriptor.
     */
    public String getFieldDescriptor() {
        FieldRefItem field = (FieldRefItem) constPool.getItem(fieldIndex);
        int nameAndTypeIndex = field.getValue().getNameAndTypeIndex();
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(nameAndTypeIndex);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getDescriptorIndex());
        return utf8.getValue();
    }

    /**
     * Returns the owner class name.
     *
     * @return The owner class internal name.
     */
    public String getOwnerClass() {
        FieldRefItem field = (FieldRefItem) constPool.getItem(fieldIndex);
        int classIndex = field.getValue().getClassIndex();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(classIndex);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        return utf8.getValue();
    }

    /**
     * Returns whether this is a static field access.
     *
     * @return true if PUTSTATIC, false if PUTFIELD.
     */
    public boolean isStatic() {
        return type == FieldType.PUTSTATIC;
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
