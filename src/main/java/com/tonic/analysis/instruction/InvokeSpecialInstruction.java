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
        Item<?> item = constPool.getItem(methodIndex);
        int params, returnSlots;
        if (item instanceof MethodRefItem) {
            MethodRefItem method = (MethodRefItem) item;
            params = method.getParameterCount();
            returnSlots = method.getReturnTypeSlots();
        } else if (item instanceof InterfaceRefItem) {
            InterfaceRefItem iface = (InterfaceRefItem) item;
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
     * Returns the method name.
     *
     * @return The method name.
     */
    public String getMethodName() {
        Item<?> item = constPool.getItem(methodIndex);
        if (item instanceof MethodRefItem) {
            MethodRefItem method = (MethodRefItem) item;
            return method.getName();
        } else if (item instanceof InterfaceRefItem) {
            InterfaceRefItem iface = (InterfaceRefItem) item;
            return iface.getName();
        }
        throw new IllegalStateException("Unexpected ref type: " + item.getClass());
    }

    /**
     * Returns the class name that owns the method.
     *
     * @return The owner class name.
     */
    public String getOwnerName() {
        Item<?> item = constPool.getItem(methodIndex);
        if (item instanceof MethodRefItem) {
            MethodRefItem method = (MethodRefItem) item;
            return method.getClassName();
        } else if (item instanceof InterfaceRefItem) {
            InterfaceRefItem iface = (InterfaceRefItem) item;
            return iface.getOwner().replace('/', '.');
        }
        throw new IllegalStateException("Unexpected ref type: " + item.getClass());
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
