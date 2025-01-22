package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents shift instructions (ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR).
 */
public class ArithmeticShiftInstruction extends Instruction {
    private final ShiftType type;

    /**
     * Enum representing the types of arithmetic shift operations.
     */
    public enum ShiftType {
        ISHL(0x78, "ishl"),
        LSHL(0x79, "lshl"),
        ISHR(0x7A, "ishr"),
        LSHR(0x7B, "lshr"),
        IUSHR(0x7C, "iushr"),
        LUSHR(0x7D, "lushr");

        private final int opcode;
        private final String mnemonic;

        ShiftType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static ShiftType fromOpcode(int opcode) {
            for (ShiftType type : ShiftType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs an ArithmeticShiftInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ArithmeticShiftInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = ShiftType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Shift opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the shift opcode to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops two operands and pushes one).
     */
    @Override
    public int getStackChange() {
        // Pops two operands and pushes one shifted value
        switch (type) {
            case ISHL:
            case ISHR:
            case IUSHR:
                return -1; // Pops two ints, pushes one int
            case LSHL:
            case LSHR:
            case LUSHR:
                return -2; // Pops two longs, pushes one long
            default:
                return 0;
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
     * Returns the type of shift operation.
     *
     * @return The ShiftType enum value.
     */
    public ShiftType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the shift instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
