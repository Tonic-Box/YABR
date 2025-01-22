package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INSTANCEOF instruction (0xC1).
 */
public class InstanceOfInstruction extends Instruction {
    private final int classIndex;
    private final ConstPool constPool;

    /**
     * Constructs an InstanceOfInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param classIndex The constant pool index for the class reference.
     */
    public InstanceOfInstruction(ConstPool constPool, int opcode, int offset, int classIndex) {
        super(opcode, offset, 3); // opcode + two bytes class index
        if (opcode != 0xC1) {
            throw new IllegalArgumentException("Invalid opcode for InstanceOfInstruction: " + opcode);
        }
        this.classIndex = classIndex;
        this.constPool = constPool;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the INSTANCEOF opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(classIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (pops one object reference, pushes one int).
     */
    @Override
    public int getStackChange() {
        return 0; // Pops one reference, pushes one int (net change: 0)
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
     * Resolves and returns a string representation of the class.
     *
     * @return The class as a string.
     */
    public String resolveClass() {
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(classIndex);
        return classRef.getClassName();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, class index, and resolved class.
     */
    @Override
    public String toString() {
        return String.format("INSTANCEOF #%d // %s", classIndex, resolveClass());
    }
}
