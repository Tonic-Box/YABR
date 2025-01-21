package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.MethodRefItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKESTATIC instruction (0xB8).
 */
public class InvokeStaticInstruction extends Instruction {
    private final int methodIndex;
    private final ConstPool constPool;

    /**
     * Constructs an InvokeStaticInstruction.
     *
     * @param constPool  The constant pool associated with the class.
     * @param opcode     The opcode of the instruction.
     * @param offset     The bytecode offset of the instruction.
     * @param methodIndex The constant pool index for the method reference.
     */
    public InvokeStaticInstruction(ConstPool constPool, int opcode, int offset, int methodIndex) {
        super(opcode, offset, 3); // opcode + two bytes method index
        if (opcode != 0xB8) {
            throw new IllegalArgumentException("Invalid opcode for InvokeStaticInstruction: " + opcode);
        }
        this.methodIndex = methodIndex;
        this.constPool = constPool;
    }

    /**
     * Writes the INVOKESTATIC opcode and its operand to the DataOutputStream.
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
        return -params + returnSlots; // Pops parameters, pushes return value
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
     * Returns the method index used by this instruction.
     *
     * @return The constant pool index for the method reference.
     */
    public int getMethodIndex() {
        return methodIndex;
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
        return String.format("INVOKESTATIC #%d // %s", methodIndex, resolveMethod());
    }
}
