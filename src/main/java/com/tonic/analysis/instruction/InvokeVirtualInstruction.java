package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.MethodRefItem;
import lombok.Getter;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKEVIRTUAL instruction (0xB6).
 */
public class InvokeVirtualInstruction extends Instruction {
    @Getter
    private final int methodIndex;
    private final ConstPool constPool;

    /**
     * Constructs an InvokeVirtualInstruction.
     *
     * @param constPool  The constant pool associated with the class.
     * @param opcode     The opcode of the instruction.
     * @param offset     The bytecode offset of the instruction.
     * @param methodIndex The constant pool index for the method reference.
     */
    public InvokeVirtualInstruction(ConstPool constPool, int opcode, int offset, int methodIndex) {
        super(opcode, offset, 3); // opcode + two bytes method index
        if (opcode != 0xB6) {
            throw new IllegalArgumentException("Invalid opcode for InvokeVirtualInstruction: " + opcode);
        }
        this.methodIndex = methodIndex;
        this.constPool = constPool;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the INVOKEVIRTUAL opcode and its operand to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(methodIndex);
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (depends on method signature).
     */
    @Override
    public int getStackChange() {
        MethodRefItem method = (MethodRefItem) constPool.getItem(methodIndex);
        int params = method.getParameterCount();
        int returnSlots = method.getReturnTypeSlots();
        return -params + returnSlots; // Pops object and parameters, pushes return value
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
     * Resolves and returns a string representation of the method.
     *
     * @return The method as a string.
     */
    public String resolveMethod() {
        MethodRefItem method = (MethodRefItem) constPool.getItem(methodIndex);
        return method.toString();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, method index, and resolved method.
     */
    @Override
    public String toString() {
        return String.format("INVOKEVIRTUAL #%d // %s", methodIndex, resolveMethod());
    }
}
