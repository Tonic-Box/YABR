package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodRefItem;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Represents the INVOKESTATIC instruction (0xB8).
 */
public class InvokeStaticInstruction extends Instruction {
    @Getter
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

    @Override
    public void accept(AbstractBytecodeVisitor visitor) {

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
        Item<?> item = constPool.getItem(methodIndex);
        int params, returnSlots;
        if (item instanceof MethodRefItem method) {
            params = method.getParameterCount();
            returnSlots = method.getReturnTypeSlots();
        } else if (item instanceof InterfaceRefItem iface) {
            params = iface.getParameterCount();
            returnSlots = iface.getReturnTypeSlots();
        } else {
            throw new IllegalStateException("Unexpected ref type: " + item.getClass());
        }
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
        Item<?> item = constPool.getItem(methodIndex);
        return item.toString();
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
