package com.tonic.analysis.instruction;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.InterfaceRefItem;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKEINTERFACE instruction (0xB9).
 */
public class InvokeInterfaceInstruction extends Instruction {
    private final int methodIndex;
    private final int count;
    private final ConstPool constPool;

    /**
     * Constructs an InvokeInterfaceInstruction.
     *
     * @param constPool  The constant pool associated with the class.
     * @param opcode     The opcode of the instruction.
     * @param offset     The bytecode offset of the instruction.
     * @param methodIndex The constant pool index for the interface method reference.
     * @param count      The number of arguments the method takes (including the object reference).
     */
    public InvokeInterfaceInstruction(ConstPool constPool, int opcode, int offset, int methodIndex, int count) {
        super(opcode, offset, 5); // opcode + two bytes method index + one byte count + one byte zero
        if (opcode != 0xB9) {
            throw new IllegalArgumentException("Invalid opcode for InvokeInterfaceInstruction: " + opcode);
        }
        this.methodIndex = methodIndex;
        this.count = count;
        this.constPool = constPool;
    }

    /**
     * Writes the INVOKEINTERFACE opcode and its operands to the DataOutputStream.
     *
     * @param dos The DataOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeByte(opcode);
        dos.writeShort(methodIndex);
        dos.writeByte(count);
        dos.writeByte(0); // Must be zero as per JVM specification
    }

    /**
     * Returns the change in stack size caused by this instruction.
     *
     * @return The stack size change (depends on method signature).
     */
    @Override
    public int getStackChange() {
        InterfaceRefItem method = (InterfaceRefItem) constPool.getItem(methodIndex);
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
     * Returns the method index used by this instruction.
     *
     * @return The constant pool index for the interface method reference.
     */
    public int getMethodIndex() {
        return methodIndex;
    }

    /**
     * Returns the count of arguments for the method.
     *
     * @return The argument count.
     */
    public int getCount() {
        return count;
    }

    /**
     * Resolves and returns a string representation of the interface method.
     *
     * @return The interface method as a string.
     */
    public String resolveMethod() {
        InterfaceRefItem method = (InterfaceRefItem) constPool.getItem(methodIndex);
        return method.toString();
    }

    /**
     * Returns a string representation of the instruction.
     *
     * @return The mnemonic, method index, count, and resolved method.
     */
    @Override
    public String toString() {
        return String.format("INVOKEINTERFACE #%d %d // %s", methodIndex, count, resolveMethod());
    }
}
