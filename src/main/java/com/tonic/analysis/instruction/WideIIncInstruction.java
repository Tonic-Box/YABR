package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the JVM WIDE IINC instruction.
 */
public class WideIIncInstruction extends Instruction {
    private final int varIndex;
    private final int constValue;

    /**
     * Constructs a WideIIncInstruction.
     *
     * @param opcode      The WIDE opcode (0xC4).
     * @param offset      The bytecode offset where WIDE starts.
     * @param varIndex    The two-byte local variable index.
     * @param constValue  The two-byte constant increment.
     */
    public WideIIncInstruction(int opcode, int offset, int varIndex, int constValue) {
        super(opcode, offset, 6);
        this.varIndex = varIndex;
        this.constValue = constValue;
    }

    /**
     * Returns the local variable index to be incremented.
     *
     * @return The variable index.
     */
    public int getVarIndex() {
        return varIndex;
    }

    /**
     * Returns the constant value by which the local variable is incremented.
     *
     * @return The constant increment value.
     */
    public int getConstValue() {
        return constValue;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the WIDE IINC opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(0x84);
        dos.writeShort(varIndex);
        dos.writeShort(constValue);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (none).
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
     * Returns a string representation of the instruction.
     *
     * @return The instruction details.
     */
    @Override
    public String toString() {
        return String.format("WideIIncInstruction{opcode=0x%02X, offset=%d, varIndex=%d, constValue=%d}",
                opcode, offset, varIndex, constValue);
    }
}
