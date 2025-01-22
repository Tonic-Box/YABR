package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the JSR instruction (0xA8).
 */
@Getter
public class JsrInstruction extends Instruction {
    private final int branchOffset;

    /**
     * Constructs a JsrInstruction.
     *
     * @param opcode        The opcode of the instruction.
     * @param offset        The bytecode offset of the instruction.
     * @param branchOffset  The branch target offset relative to current instruction.
     */
    public JsrInstruction(int opcode, int offset, int branchOffset) {
        super(opcode, offset, 3); // opcode + two bytes branch offset
        if (opcode != 0xA8) {
            throw new IllegalArgumentException("Invalid opcode for JsrInstruction: " + opcode);
        }
        this.branchOffset = branchOffset;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the JSR opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(branchOffset);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes the return address).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes the return address onto the stack
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
     * @return The mnemonic and branch target of the instruction.
     */
    @Override
    public String toString() {
        return String.format("JSR %d", branchOffset);
    }
}
