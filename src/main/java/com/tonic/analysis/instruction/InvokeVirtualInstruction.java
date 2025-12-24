package com.tonic.analysis.instruction;

import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.InterfaceRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.parser.constpool.NameAndTypeRefItem;
import com.tonic.parser.constpool.Utf8Item;
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
        return -(params + 1) + returnSlots;
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
        return String.format("INVOKEVIRTUAL #%d // %s", methodIndex, resolveMethod());
    }

    /**
     * Returns the method name.
     *
     * @return The method name.
     */
    public String getMethodName() {
        Item<?> item = constPool.getItem(methodIndex);
        int nameAndTypeIndex;
        if (item instanceof MethodRefItem) {
            nameAndTypeIndex = ((MethodRefItem) item).getValue().getNameAndTypeIndex();
        } else if (item instanceof InterfaceRefItem) {
            nameAndTypeIndex = ((InterfaceRefItem) item).getValue().getNameAndTypeIndex();
        } else {
            throw new IllegalStateException("Unexpected ref type: " + item.getClass());
        }
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(nameAndTypeIndex);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getNameIndex());
        return utf8.getValue();
    }

    /**
     * Returns the method descriptor.
     *
     * @return The method descriptor.
     */
    public String getMethodDescriptor() {
        Item<?> item = constPool.getItem(methodIndex);
        int nameAndTypeIndex;
        if (item instanceof MethodRefItem) {
            nameAndTypeIndex = ((MethodRefItem) item).getValue().getNameAndTypeIndex();
        } else if (item instanceof InterfaceRefItem) {
            nameAndTypeIndex = ((InterfaceRefItem) item).getValue().getNameAndTypeIndex();
        } else {
            throw new IllegalStateException("Unexpected ref type: " + item.getClass());
        }
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(nameAndTypeIndex);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getDescriptorIndex());
        return utf8.getValue();
    }

    /**
     * Returns the owner class name.
     *
     * @return The owner class internal name.
     */
    public String getOwnerClass() {
        Item<?> item = constPool.getItem(methodIndex);
        int classIndex;
        if (item instanceof MethodRefItem) {
            classIndex = ((MethodRefItem) item).getValue().getClassIndex();
        } else if (item instanceof InterfaceRefItem) {
            classIndex = ((InterfaceRefItem) item).getValue().getClassIndex();
        } else {
            throw new IllegalStateException("Unexpected ref type: " + item.getClass());
        }
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(classIndex);
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        return utf8.getValue();
    }
}
