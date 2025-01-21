package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.InterfaceRef;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.parser.constpool.structure.InvokeParameterUtil.*;

/**
 * Represents an Interface Method Reference in the constant pool.
 * The value is an InterfaceRef object containing class and name-and-type indices.
 */
public class InterfaceRefItem extends Item<InterfaceRef> {
    private ConstPool constPool;
    private InterfaceRef value;

    @Override
    public void read(ClassFile classFile) {
        this.constPool = classFile.getConstPool();
        int classIndex = classFile.readUnsignedShort();
        int nameAndTypeIndex = classFile.readUnsignedShort();
        this.value = new InterfaceRef(classIndex, nameAndTypeIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getClassIndex());
        dos.writeShort(value.getNameAndTypeIndex());
    }

    @Override
    public byte getType() {
        return ITEM_INTERFACE_REF;
    }

    @Override
    public InterfaceRef getValue() {
        return value;
    }

    /**
     * Returns the number of parameters for the invoked method.
     *
     * @return The number of parameters.
     */
    public int getParameterCount() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        // Retrieve the NameAndType entry from the constant pool
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        String descriptor = nameAndType.getDescriptor(); // e.g., "(Ljava/lang/String;)V"
        return parseDescriptorParameters(descriptor);
    }

    /**
     * Returns the number of slots for the return type of the invoked method.
     *
     * @return The number of return type slots.
     */
    public int getReturnTypeSlots() {
        if (constPool == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        // Retrieve the NameAndType entry from the constant pool
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        String descriptor = nameAndType.getDescriptor(); // e.g., "(Ljava/lang/String;)V"
        String returnType = parseDescriptorReturnType(descriptor);
        return determineTypeSlots(returnType);
    }
}
