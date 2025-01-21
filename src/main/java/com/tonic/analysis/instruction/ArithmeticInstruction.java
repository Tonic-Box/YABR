package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents arithmetic instructions (IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM).
 */
public class ArithmeticInstruction extends Instruction {
    private final ArithmeticType type;

    /**
     * Enum representing the types of arithmetic operations.
     */
    public enum ArithmeticType {
        IADD(0x60, "iadd"),
        LADD(0x61, "ladd"),
        FADD(0x62, "fadd"),
        DADD(0x63, "dadd"),
        ISUB(0x64, "isub"),
        LSUB(0x65, "lsub"),
        FSUB(0x66, "fsub"),
        DSUB(0x67, "dsub"),
        IMUL(0x68, "imul"),
        LMUL(0x69, "lmul"),
        FMUL(0x6A, "fmul"),
        DMUL(0x6B, "dmul"),
        IDIV(0x6C, "idiv"),
        LDIV(0x6D, "ldiv"),
        FDIV(0x6E, "fdiv"),
        DDIV(0x6F, "ddiv"),
        IREM(0x70, "irem"),
        LREM(0x71, "lrem"),
        FREM(0x72, "frem"),
        DREM(0x73, "drem");

        private final int opcode;
        private final String mnemonic;

        ArithmeticType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static ArithmeticType fromOpcode(int opcode) {
            for (ArithmeticType type : ArithmeticType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs an ArithmeticInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ArithmeticInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = ArithmeticType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Arithmetic opcode: " + opcode);
        }
    }

    /**
     * Writes the arithmetic opcode to the DataOutputStream.
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
     * @return The stack size change based on the arithmetic operation type.
     */
    @Override
    public int getStackChange() {
        // All arithmetic operations pop two operands and push one result
        // For longs and doubles, each operand/result occupies two stack slots
        switch (type) {
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
                return -1; // Pops two ints, pushes one int
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
                return -2; // Pops two longs, pushes one long
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return -1; // Pops two floats, pushes one float
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                return -2; // Pops two doubles, pushes one double
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
     * Returns the type of arithmetic operation.
     *
     * @return The ArithmeticType enum value.
     */
    public ArithmeticType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the arithmetic instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
