package com.tonic.analysis.instruction;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the MONITOREXIT instruction (0xC3).
 */
public class MonitorExitInstruction extends Instruction {

    /**
     * Constructs a MonitorExitInstruction.
     *
     * @param opcode The opcode of the instruction.
     * @param offset The bytecode offset of the instruction.
     */
    public MonitorExitInstruction(int opcode, int offset) {
        super(opcode, offset, 1); // opcode only
        if (opcode != 0xC3) {
            throw new IllegalArgumentException("Invalid opcode for MonitorExitInstruction: " + opcode);
        }
    }

    /**
     * Writes the MONITOREXIT opcode to the DataOutputStream.
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
     * @return The stack size change (pops one reference).
     */
    @Override
    public int getStackChange() {
        return -1; // Pops one reference (object to monitor)
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
     * @return The mnemonic of the instruction.
     */
    @Override
    public String toString() {
        return "MONITOREXIT";
    }
}
