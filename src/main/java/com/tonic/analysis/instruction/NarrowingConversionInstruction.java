package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the narrowing conversion instructions (I2B, I2C, I2S).
 */
public class NarrowingConversionInstruction extends Instruction {
    private final NarrowingType type;

    /**
     * Enum representing the types of narrowing conversion operations.
     */
    @Getter
    public enum NarrowingType {
        I2B(0x91, "i2b"),
        I2C(0x92, "i2c"),
        I2S(0x93, "i2s");

        private final int opcode;
        private final String mnemonic;

        NarrowingType(int opcode, String mnemonic) {
            this.opcode = opcode;
            this.mnemonic = mnemonic;
        }

        public static NarrowingType fromOpcode(int opcode) {
            for (NarrowingType type : NarrowingType.values()) {
                if (type.opcode == opcode) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Constructs a NarrowingConversionInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public NarrowingConversionInstruction(int opcode, int offset) {
        super(opcode, offset, 1);
        this.type = NarrowingType.fromOpcode(opcode);
        if (this.type == null) {
            throw new IllegalArgumentException("Invalid Narrowing Conversion opcode: " + opcode);
        }
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the narrowing conversion opcode to the DataOutputStream.
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
     * @return The stack size change (no net change).
     */
    @Override
    public int getStackChange() {
        return 0;
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
     * Returns the type of narrowing conversion operation.
     *
     * @return The NarrowingType enum value.
     */
    public NarrowingType getType() {
        return type;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic of the narrowing conversion instruction.
     */
    @Override
    public String toString() {
        return type.getMnemonic().toUpperCase();
    }
}
