package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Represents the LOOKUPSWITCH instruction (0xAB).
 */
public class LookupSwitchInstruction extends Instruction {
    private final int padding;
    @Getter
    private final int defaultOffset;
    @Getter
    private final int npairs;
    @Getter
    private final Map<Integer, Integer> matchOffsets;

    /**
     * Constructs a LookupSwitchInstruction.
     *
     * @param opcode        The opcode of the instruction.
     * @param offset        The bytecode offset of the instruction.
     * @param padding       The number of padding bytes to align to 4-byte boundary.
     * @param defaultOffset The branch target offset if no key matches.
     * @param npairs        The number of key-offset pairs.
     * @param matchOffsets  The map of keys to branch target offsets.
     */
    public LookupSwitchInstruction(int opcode, int offset, int padding, int defaultOffset, int npairs, Map<Integer, Integer> matchOffsets) {
        super(opcode, offset, 12 + (npairs * 8)); // opcode + padding + defaultOffset + npairs + match-offset pairs
        if (opcode != 0xAB) {
            throw new IllegalArgumentException("Invalid opcode for LookupSwitchInstruction: " + opcode);
        }
        this.padding = padding;
        this.defaultOffset = defaultOffset;
        this.npairs = npairs;
        this.matchOffsets = matchOffsets;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the LOOKUPSWITCH opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        for (int i = 0; i < padding; i++) {
            dos.writeByte(0);
        }
        dos.writeInt(defaultOffset);
        dos.writeInt(npairs);
        for (Map.Entry<Integer, Integer> entry : matchOffsets.entrySet()) {
            dos.writeInt(entry.getKey());
            dos.writeInt(entry.getValue());
        }
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops one int).
     */
    @Override
    public int getStackChange() {
        return -1; // Pops one int (the key to match)
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
     * @return The mnemonic, default offset, number of pairs, and key-offset mappings.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LOOKUPSWITCH default=").append(defaultOffset).append(", npairs=").append(npairs).append(" {");
        for (Map.Entry<Integer, Integer> entry : matchOffsets.entrySet()) {
            sb.append(entry.getKey()).append("->").append(entry.getValue()).append(", ");
        }
        if (!matchOffsets.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove last comma and space
        }
        sb.append("}");
        return sb.toString();
    }
}
