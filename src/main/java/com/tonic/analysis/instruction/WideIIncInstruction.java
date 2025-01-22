package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents a WIDE IINC instruction in Java bytecode.
 * This instruction increments a local variable by a constant with extended index and constant size.
 */
public class WideIIncInstruction extends Instruction {
    private final int varIndex;    // Two-byte local variable index
    private final int constValue;  // Two-byte constant increment

    /**
     * Constructs a WideIIncInstruction.
     *
     * @param opcode      The WIDE opcode (0xC4)
     * @param offset      The bytecode offset where WIDE starts
     * @param varIndex    The two-byte local variable index
     * @param constValue  The two-byte constant increment
     */
    public WideIIncInstruction(int opcode, int offset, int varIndex, int constValue) {
        super(opcode, offset, 6); // Total length is 6 bytes
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
     * Writes this instruction to the given DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);        // WIDE opcode (0xC4)
        dos.writeByte(0x84);          // Modified opcode for IINC (0x84)
        dos.writeShort(varIndex);     // Two-byte variable index
        dos.writeShort(constValue);   // Two-byte constant value
    }

    /**
     * Returns the change in the operand stack caused by this instruction.
     * For IINC, the stack remains unchanged.
     *
     * @return The stack size change (0).
     */
    @Override
    public int getStackChange() {
        return 0;
    }

    /**
     * Returns the change in local variables caused by this instruction.
     * IINC modifies an existing local variable without altering the total count.
     *
     * @return The local variable size change (0).
     */
    @Override
    public int getLocalChange() {
        return 0;
    }

    /**
     * Provides a string representation of the instruction for debugging purposes.
     *
     * @return A string describing the instruction.
     */
    @Override
    public String toString() {
        return String.format("WideIIncInstruction{opcode=0x%02X, offset=%d, varIndex=%d, constValue=%d}",
                opcode, offset, varIndex, constValue);
    }
}
