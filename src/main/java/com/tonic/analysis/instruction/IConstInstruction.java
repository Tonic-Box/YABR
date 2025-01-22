package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the ICONST_* instructions (0x02 to 0x08).
 */
@Getter
public class IConstInstruction extends Instruction {
    private final int value;

    /**
     * Constructs an IConstInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     * @param value  The integer constant value.
     */
    public IConstInstruction(int opcode, int offset, int value) {
        super(opcode, offset, 1);
        this.value = value;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the ICONST_* opcode to the DataOutputStream.
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
     * @return The stack size change (pushes an int).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes an int onto the stack
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
     * @return The mnemonic and value of the instruction.
     */
    @Override
    public String toString() {
        return String.format("ICONST_%d", value);
    }
}
