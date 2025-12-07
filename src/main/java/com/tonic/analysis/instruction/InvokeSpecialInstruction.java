package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.analysis.visitor.Visitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.MethodRefItem;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKESPECIAL instruction (0xB7).
 */
public class InvokeSpecialInstruction extends Instruction {
    @Getter
    private final int methodIndex;
    private final ConstPool constPool;

    /**
     * Constructs an InvokeSpecialInstruction.
     *
     * @param constPool  The constant pool associated with the class.
     * @param opcode     The opcode of the instruction.
     * @param offset     The bytecode offset of the instruction.
     * @param methodIndex The constant pool index for the method reference.
     */
    public InvokeSpecialInstruction(ConstPool constPool, int opcode, int offset, int methodIndex) {
        super(opcode, offset, 3); // opcode + two bytes method index
        if (opcode != 0xB7) {
            throw new IllegalArgumentException("Invalid opcode for InvokeSpecialInstruction: " + opcode);
        }
        this.methodIndex = methodIndex;
        this.constPool = constPool;
    }

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {
        visitor.visit(this);
    }

    /**
     * Writes the INVOKESPECIAL opcode and its operand to the DataOutputStream.
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
        return -params + returnSlots;
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
     * Returns the method name.
     *
     * @return The method name.
     */
    public String getMethodName() {
        MethodRefItem method = (MethodRefItem) constPool.getItem(methodIndex);
        return method.getName();
    }

    /**
     * Returns the class name that owns the method.
     *
     * @return The owner class name.
     */
    public String getOwnerName() {
        MethodRefItem method = (MethodRefItem) constPool.getItem(methodIndex);
        return method.getClassName();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, method index, and resolved method.
     */
    @Override
    public String toString() {
        return String.format("INVOKESPECIAL #%d // %s", methodIndex, resolveMethod());
    }
}
