package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.utill.Opcode.*;

/**
 * Represents the ALOAD instructions (0x19, 0x2A-0x2D).
 */
@Getter
public class ALoadInstruction extends Instruction {
    /**
     * -- GETTER --
     *  Returns the local variable index being loaded.
     *
     * @return The local variable index.
     */
    private final int varIndex;

    /**
     * Constructs an ALoadInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to load. For ALOAD_0-3, this is 0-3 respectively.
     */
    public ALoadInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, isShortForm(opcode) ? 1 : 2);
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    private static boolean isShortForm(int opcode) {
        return opcode >= ALOAD_0.getCode() && opcode <= ALOAD_3.getCode();
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        if (!isShortForm(opcode)) {
            dos.writeByte(varIndex);
        }
    }

    @Override
    public int getStackChange() {
        return 1;
    }

    @Override
    public int getLocalChange() {
        return 0;
    }

    @Override
    public String toString() {
        if (opcode == ALOAD.getCode()) {
            return String.format("ALOAD %d", varIndex);
        } else {
            int index = opcode - ALOAD_0.getCode();
            return String.format("ALOAD_%d", index);
        }
    }
}
