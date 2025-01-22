package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the NEW instruction (0xBB).
 */
public class NewInstruction extends Instruction {
    /**
     * -- GETTER --
     *  Returns the class index used by this instruction.
     *
     * @return The constant pool index for the class reference.
     */
    @Getter
    private final int classIndex;
    private final ConstPool constPool;

    /**
     * Constructs a NewInstruction.
     *
     * @param constPool The constant pool associated with the class.
     * @param opcode    The opcode of the instruction.
     * @param offset    The bytecode offset of the instruction.
     * @param classIndex The constant pool index for the class reference.
     */
    public NewInstruction(ConstPool constPool, int opcode, int offset, int classIndex) {
        super(opcode, offset, 3); // opcode + two bytes class index
        if (opcode != 0xBB) {
            throw new IllegalArgumentException("Invalid opcode for NewInstruction: " + opcode);
        }
        this.classIndex = classIndex;
        this.constPool = constPool;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the NEW opcode and its operand to the DataOutputStream.
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
     * @return The stack size change (pushes a reference).
     */
    @Override
    public int getStackChange() {
        return 1; // Pushes a reference onto the stack
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
        return String.format("NEW #%d // %s", classIndex, resolveClass());
    }
}
