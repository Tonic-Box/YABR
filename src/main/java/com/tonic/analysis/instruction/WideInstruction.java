package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.utill.Opcode;
import lombok.Getter;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the WIDE instruction (0xC4).
 */
@Getter
public class WideInstruction extends Instruction {
    private final Opcode modifiedOpcode;
    private final int varIndex;
    private final int constValue; // For IINC

    /**
     * Constructs a WideInstruction for variable instructions.
     *
     * @param opcode         The opcode of the instruction.
     * @param offset         The bytecode offset of the instruction.
     * @param modifiedOpcode The opcode of the instruction being modified.
     * @param varIndex       The local variable index.
     */
    public WideInstruction(int opcode, int offset, Opcode modifiedOpcode, int varIndex) {
        super(opcode, offset, 4); // opcode + modifiedOpcode + two bytes varIndex
        this.modifiedOpcode = modifiedOpcode;
        this.varIndex = varIndex;
        this.constValue = 0;
    }

    /**
     * Constructs a WideInstruction for IINC.
     *
     * @param opcode         The opcode of the instruction.
     * @param offset         The bytecode offset of the instruction.
     * @param modifiedOpcode The opcode of the instruction being modified.
     * @param varIndex       The local variable index.
     * @param constValue     The constant value for IINC.
     */
    public WideInstruction(int opcode, int offset, Opcode modifiedOpcode, int varIndex, int constValue) {
        super(opcode, offset, 6); // opcode + modifiedOpcode + two bytes varIndex + two bytes constValue
        this.modifiedOpcode = modifiedOpcode;
        this.varIndex = varIndex;
        this.constValue = constValue;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the WIDE opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeByte(modifiedOpcode.getCode());
        dos.writeShort(varIndex);
        if (modifiedOpcode == Opcode.IINC) {
            dos.writeShort(constValue);
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change based on the modified opcode.
     */
    @Override
    public int getStackChange() {
        // Shift operations or variable instructions may have specific stack changes
        return switch (modifiedOpcode) {
            case ILOAD, FLOAD, ALOAD -> 1; // Pushes one value
            case ISTORE, FSTORE, ASTORE -> -1; // Pops one value
            case IINC -> 0; // No stack change
            default -> 0;
        };
    }

    /**
     * Returns the change in local variables caused by this instruction.
     *
     * @return The local variables size change based on the modified opcode.
     */
    @Override
    public int getLocalChange() {
        return 0;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic and operands of the instruction.
     */
    @Override
    public String toString() {
        if (modifiedOpcode == Opcode.IINC) {
            return String.format("WIDE %s %d %d", modifiedOpcode.name(), varIndex, constValue);
        } else {
            return String.format("WIDE %s %d", modifiedOpcode.name(), varIndex);
        }
    }
}
