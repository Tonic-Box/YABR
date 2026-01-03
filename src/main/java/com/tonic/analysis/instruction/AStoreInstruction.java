package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.utill.Opcode.*;

/**
 * Represents the ASTORE instruction and its variants (ASTORE, ASTORE_0-3) (0x4B-0x4E).
 */
@Getter
public class AStoreInstruction extends Instruction {
    private final int varIndex;

    /**
     * Constructs an AStoreInstruction.
     *
     * @param opcode   The opcode of the instruction.
     * @param offset   The bytecode offset of the instruction.
     * @param varIndex The index of the local variable to store. For ASTORE_0-3, this is 0-3 respectively.
     */
    public AStoreInstruction(int opcode, int offset, int varIndex) {
        super(opcode, offset, isShortForm(opcode) ? 1 : 2);
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    private static boolean isShortForm(int opcode) {
        return opcode >= ASTORE_0.getCode() && opcode <= ASTORE_3.getCode();
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
        return -1;
    }

    @Override
    public int getLocalChange() {
        return 0;
    }

    @Override
    public String toString() {
        if (opcode == ASTORE_0.getCode()) {
            return "astore_0";
        } else if (opcode == ASTORE_1.getCode()) {
            return "astore_1";
        } else if (opcode == ASTORE_2.getCode()) {
            return "astore_2";
        } else if (opcode == ASTORE_3.getCode()) {
            return "astore_3";
        } else {
            return String.format("astore %d", varIndex);
        }
    }
}
