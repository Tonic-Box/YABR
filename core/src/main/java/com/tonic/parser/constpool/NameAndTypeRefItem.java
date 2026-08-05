package com.tonic.parser.constpool;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.structure.NameAndType;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A CONSTANT_NameAndType constant pool entry, holding the name and descriptor indices of a member.
 */
public class NameAndTypeRefItem extends Item<NameAndType>
{
    private ConstPool constPool;
    private NameAndType value;

    /**
     * @return the const pool
     */
    public ConstPool getConstPool()
    {
        return constPool;
    }

    /**
     * Binds the pool used to resolve the name and descriptor indices.
     * @param constPool the owning constant pool
     */
    public void setConstPool(ConstPool constPool)
    {
        this.constPool = constPool;
    }

    /**
     * Replaces the name and descriptor index pair this entry holds.
     * @param value the new pair
     */
    public void setValue(NameAndType value)
    {
        this.value = value;
    }

    @Override
    public void read(ClassFile classFile)
    {
        this.constPool = classFile.getConstPool();
        int nameIndex = classFile.readUnsignedShort();
        int descriptorIndex = classFile.readUnsignedShort();
        this.value = new NameAndType(nameIndex, descriptorIndex);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException
    {
        dos.writeShort(value.getNameIndex());
        dos.writeShort(value.getDescriptorIndex());
    }

    @Override
    public byte getType()
    {
        return ITEM_NAME_TYPE_REF;
    }

    @Override
    public NameAndType getValue()
    {
        return value;
    }

    /**
     * Resolves the descriptor string through the bound constant pool.
     * @return the descriptor string
     * @throws IllegalStateException if no pool is bound or the descriptor index is unresolvable
     */
    public String getDescriptor()
    {
        if (constPool == null)
        {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        String descriptor = ((Utf8Item)constPool.getItem(value.getDescriptorIndex())).getValue();
        if (descriptor == null)
        {
            throw new IllegalStateException("Invalid descriptor index: " + value.getDescriptorIndex());
        }

        return descriptor;
    }

    /**
     * Resolves the name string through the bound constant pool.
     * @return the name string
     * @throws IllegalStateException if no pool is bound or the name index is unresolvable
     */
    public String getName()
    {
        if (constPool == null)
        {
            throw new IllegalStateException("ConstPool not set. Ensure read(ClassFile) has been called.");
        }

        String name = ((Utf8Item)constPool.getItem(value.getNameIndex())).getValue();
        if (name == null)
        {
            throw new IllegalStateException("Invalid name index: " + value.getNameIndex());
        }

        return name;
    }

    /**
     * Repoints this entry at a different name.
     * @param nameIndex the constant pool index of the new name
     */
    public void setNameIndex(int nameIndex)
    {
        value.setNameIndex(nameIndex);
    }

    /**
     * Repoints this entry at a different descriptor.
     * @param descIndex the constant pool index of the new descriptor
     */
    public void setDescIndex(int descIndex)
    {
        value.setDescriptorIndex(descIndex);
    }
}
