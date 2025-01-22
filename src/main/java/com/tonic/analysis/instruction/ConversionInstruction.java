package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the type conversion instructions (I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F).
 */
public class ConversionInstruction extends Instruction {
    private final ConversionType type;

    /**
     * Enum representing the types of conversion operations.
     */
    public enum ConversionType {
        I2F(0x86, "i2f"),
        I2D(0x87, "i2d"),
        L2I(0x88, "l2i"),
        L2F(0x89, "l2f"),
        L2D(0x8A, "l2d"),
        F2I(0x8B, "f2i"),
        F2L(0x8C, "f2l"),
        F2D(0x8D, "f2d"),
        D2I(0x8E, "d2i"),
        D2L(0x8F, "d2l"),
        D2F(0x90, "d2f");

        private final int opcode;
        private final String mnemonic;

        ConversionType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public int getOpcode() {
            return opcode;
        }

        public String getMnemonic() {
            return mnemonic;
        }

        public static ConversionType fromOpcode(int opcode) {
            for (ConversionType type : ConversionType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a ConversionInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public ConversionInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = ConversionType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Conversion opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the conversion opcode to the DataOutputStream.
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
     * @return The stack size change based on the conversion type.
     */
    @Override
    public int getStackChange() {
        // Conversion generally pops one value and pushes one value of different type
        // Stack size remains the same except for types occupying different slots
        switch (type) {
            case I2F:
            case I2D:
            case L2I:
            case L2F:
            case L2D:
            case F2I:
            case F2L:
            case F2D:
            case D2I:
            case D2L:
            case D2F:
                if (type == ConversionType.I2D || type == ConversionType.L2I || type == ConversionType.L2F ||
                        type == ConversionType.L2D || type == ConversionType.D2I || type == ConversionType.D2L ||
                        type == ConversionType.D2F) {
                    // Some conversions might change stack slots
                    return 0;
                }
                return 0;
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
     * Returns the type of conversion operation.
     *
     * @return The ConversionType enum value.
     */
    public ConversionType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the conversion instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
