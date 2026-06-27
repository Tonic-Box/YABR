package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

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

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
    }

    @Override
    public int getStackChange() {
        switch (type) {
            case ISHL:
            case ISHR:
            case IUSHR:
                return -1;
            case LSHL:
            case LSHR:
            case LUSHR:
                return -2;
            default:
                return 0;
        }
    }

    @Override
    public int getLocalChange() {
        return 0;
    }

    public ShiftType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
