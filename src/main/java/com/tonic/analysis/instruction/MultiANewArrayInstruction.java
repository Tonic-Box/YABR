package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the MULTIANEWARRAY instruction (0xC5).
 */
public class MultiANewArrayInstruction extends Instruction {
    private final int classIndex;
    private final int dimensions;
    private final ConstPool constPool;

    /**
     * Constructs a MultiANewArrayInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param classIndex The constant pool index for the class reference.
     * @param dimensions The number of dimensions for the new array.
     */
    public MultiANewArrayInstruction(ConstPool constPool, int opcode, int offset, int classIndex, int dimensions) {
        super(opcode, offset, 4); // opcode + two bytes class index + one byte dimensions
        if (opcode != 0xC5) {
            throw new IllegalArgumentException("Invalid opcode for MultiANewArrayInstruction: " + opcode);
        }
        this.classIndex = classIndex;
        this.dimensions = dimensions;
        this.constPool = constPool;
    }

    /**
     * Writes the MULTIANEWARRAY opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(classIndex);
        dos.writeByte(dimensions);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops dimensions count from stack, pushes array reference).
     */
    @Override
    public int getStackChange() {
        return 1 - dimensions; // Pops 'dimensions' count, pushes one array reference
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
     * Returns the class index used by this instruction.
     *
     * @return The constant pool index for the class reference.
     */
    public int getClassIndex() {
        return classIndex;
    }

    /**
     * Returns the number of dimensions for the new array.
     *
     * @return The number of dimensions.
     */
    public int getDimensions() {
        return dimensions;
    }

    /**
     * Resolves and returns a string representation of the array class.
     *
     * @return The array class as a string.
     */
    public String resolveClass() {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(classIndex);
        return classRef.getClassName();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, class index, dimensions, and resolved class.
     */
    @Override
    public String toString() {
        return String.format("MULTIANEWARRAY #%d %d // %s", classIndex, dimensions, resolveClass());
    }
}
