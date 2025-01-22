package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.MethodRef;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;

import static com.tonic.parser.constpool.structure.InvokeParameterUtil.*;

/**
 * Represents a Method Reference in the constant pool.
 * The value is a MethodRef object containing class and name-and-type indices.
 */
public class MethodRefItem extends Item<MethodRef> {
    @Setter
    private ClassFile classFile = null;
    @Setter
    private MethodRef value;

    @Override
    public void read(ClassFile classFile) {
        this.classFile = classFile;
        int classIndex = classFile.readUnsignedShort();
        int nameAndTypeIndex = classFile.readUnsignedShort();
        this.value = new MethodRef(classIndex, nameAndTypeIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeShort(value.getClassIndex());
        dos.writeShort(value.getNameAndTypeIndex());
    }

    @Override
    public byte getType() {
        return ITEM_METHOD_REF;
    }

    @Override
    public MethodRef getValue() {
        return value;
    }

    public String getClassName() {
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
        ClassRefItem classRef = (ClassRefItem) constPool.getItem(value.getClassIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(classRef.getValue());
        return utf8.getValue().replace('/', '.');
    }

    public String getName() {
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getNameIndex());
        return utf8.getValue();
    }

    public String getDescriptor() {
        if(classFile == null)
            return null;
        ConstPool constPool = classFile.getConstPool();
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) constPool.getItem(value.getNameAndTypeIndex());
        Utf8Item utf8 = (Utf8Item) constPool.getItem(nameAndType.getValue().getDescriptorIndex());
        return utf8.getValue();
    }

    /**
     * Returns the number of parameters for the invoked method.
     *
     * @return The number of parameters.
     */
    public int getParameterCount() {
        if (classFile == null) {
            throw new IllegalStateException("classFile not set. Ensure read(ClassFile) has been called.");
        }

        // Retrieve the NameAndType entry from the constant pool
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) classFile.getConstPool().getItem(value.getNameAndTypeIndex());
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
        if (classFile.getConstPool() == null) {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        // Retrieve the NameAndType entry from the constant pool
        NameAndTypeRefItem nameAndType = (NameAndTypeRefItem) classFile.getConstPool().getItem(value.getNameAndTypeIndex());
        if (nameAndType == null) {
            throw new IllegalStateException("Invalid NameAndType index: " + value.getNameAndTypeIndex());
        }

        String descriptor = nameAndType.getDescriptor(); // e.g., "(Ljava/lang/String;)V"
        String returnType = parseDescriptorReturnType(descriptor);
        return determineTypeSlots(returnType);
    }

    public void setClassIndex(int classIndex)
    {
        value.setClassIndex(classIndex);
    }

    public void setNameAndTypeIndex(int nameAndTypeIndex)
    {
        value.setNameAndTypeIndex(nameAndTypeIndex);
    }
}
