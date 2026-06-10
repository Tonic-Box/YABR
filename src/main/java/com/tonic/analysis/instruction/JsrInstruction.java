package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.utill.Opcode.JSR;
import static com.tonic.utill.Opcode.JSR_W;

/**
 * Represents the JSR (0xA8) and JSR_W (0xC9) instructions.
 */
@Getter
public class JsrInstruction extends Instruction {
    private final int branchOffset;

    /**
     * Constructs a JsrInstruction.
     *
     * @param opcode        The opcode of the instruction (JSR or JSR_W).
     * @param offset        The bytecode offset of the instruction.
     * @param branchOffset  The branch target offset relative to current instruction.
     */
    public JsrInstruction(int opcode, int offset, int branchOffset) {
        super(opcode, offset, (opcode == JSR.getCode()) ? 3 : 5);
        if (opcode != JSR.getCode() && opcode != JSR_W.getCode()) {
            throw new IllegalArgumentException("Invalid opcode for JsrInstruction: " + opcode);
        }
        this.branchOffset = branchOffset;
    }

    /**
     * Returns whether this is the wide JSR_W form.
     *
     * @return {@code true} for JSR_W, {@code false} for JSR.
     */
    public boolean isWide() {
        return opcode == JSR_W.getCode();
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
        if (isWide()) {
            dos.writeInt(branchOffset);
        } else {
            dos.writeShort(branchOffset);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pushes the return address).
     */
    @Override
    public int getStackChange() {
        return 1;
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
