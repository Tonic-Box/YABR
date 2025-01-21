package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Represents the TABLESWITCH instruction (0xAA).
 */
public class TableSwitchInstruction extends Instruction {
    private final int padding;
    private final int defaultOffset;
    private final int low;
    private final int high;
    private final Map<Integer, Integer> jumpOffsets;

    /**
     * Constructs a TableSwitchInstruction.
     *
     * @param opcode        The opcode of the instruction.
     * @param offset        The bytecode offset of the instruction.
     * @param padding       The number of padding bytes to align to 4-byte boundary.
     * @param defaultOffset The branch target offset if no key matches.
     * @param low           The lowest key value.
     * @param high          The highest key value.
     * @param jumpOffsets   The map of key to branch target offsets.
     */
    public TableSwitchInstruction(int opcode, int offset, int padding, int defaultOffset, int low, int high, Map<Integer, Integer> jumpOffsets) {
        super(opcode, offset, 12 + ((high - low + 1) * 4)); // opcode + padding + default + low + high + jumpOffsets
        if (opcode != 0xAA) {
            throw new IllegalArgumentException("Invalid opcode for TableSwitchInstruction: " + opcode);
        }
        this.padding = padding;
        this.defaultOffset = defaultOffset;
        this.low = low;
        this.high = high;
        this.jumpOffsets = jumpOffsets;
    }

    /**
     * Writes the TABLESWITCH opcode and its operands to the DataOutputStream.
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
        dos.writeInt(low);
        dos.writeInt(high);
        for (int key = low; key <= high; key++) {
            dos.writeInt(jumpOffsets.getOrDefault(key, defaultOffset));
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
     * Returns the default branch offset.
     *
     * @return The default branch target offset.
     */
    public int getDefaultOffset() {
        return defaultOffset;
    }

    /**
     * Returns the low key value.
     *
     * @return The low key value.
     */
    public int getLow() {
        return low;
    }

    /**
     * Returns the high key value.
     *
     * @return The high key value.
     */
    public int getHigh() {
        return high;
    }

    /**
     * Returns the map of jump offsets.
     *
     * @return The map of keys to branch target offsets.
     */
    public Map<Integer, Integer> getJumpOffsets() {
        return jumpOffsets;
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, default offset, low, high, and jump offsets.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TABLESWITCH default=").append(defaultOffset).append(", low=").append(low).append(", high=").append(high).append(" {");
        for (int key = low; key <= high; key++) {
            sb.append(key).append("->").append(jumpOffsets.getOrDefault(key, defaultOffset)).append(", ");
        }
        if (!jumpOffsets.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove last comma and space
        }
        sb.append("}");
        return sb.toString();
    }
}
