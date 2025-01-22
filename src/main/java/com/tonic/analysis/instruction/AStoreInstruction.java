package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

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
        super(opcode, offset, isShortForm(opcode) ? 1 : 2); // Short-form ASTORE_0-3 have no operands, regular ASTORE has one operand byte
        this.varIndex = varIndex;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Determines if the opcode is a short-form ASTORE instruction.
     *
     * @param opcode The opcode to check.
     * @return True if the opcode is ASTORE_0-3, false otherwise.
     */
    private static boolean isShortForm(int opcode) {
        return opcode >= 0x4B && opcode <= 0x4E;
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
        // ASTORE consumes one reference from the stack
        return -1;
    }

    @Override
    public int getLocalChange() {
        // No change in the number of local variables
        return 0;
    }

    @Override
    public String toString() {
        String mnemonic;
        switch (opcode) {
            case 0x4B:
                mnemonic = "astore_0";
                break;
            case 0x4C:
                mnemonic = "astore_1";
                break;
            case 0x4D:
                mnemonic = "astore_2";
                break;
            case 0x4E:
                mnemonic = "astore_3";
                break;
            case 0x3B:
            case 0x3C:
            case 0x3D:
            case 0x3E:
                mnemonic = "astore";
                break;
            default:
                mnemonic = "astore_unknown";
        }
        if (opcode >= 0x4B && opcode <= 0x4E) {
            return String.format("%s", mnemonic);
        } else {
            return String.format("%s %d", mnemonic, varIndex);
        }
    }
}
