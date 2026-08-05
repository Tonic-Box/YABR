package com.tonic.parser.constpool.structure;

/**
 * Represents a Name and Type in the constant pool.
 */
public class NameAndType
{
    private int nameIndex;
    private int descriptorIndex;

    /**
     * Creates a name-and-type pair from its two constant-pool indices.
     * @param nameIndex CONSTANT_Utf8 index of the member name
     * @param descriptorIndex CONSTANT_Utf8 index of the descriptor
     */
    public NameAndType(int nameIndex, int descriptorIndex)
    {
        this.nameIndex = nameIndex;
        this.descriptorIndex = descriptorIndex;
    }

    /**
     * @return the name index
     */
    public int getNameIndex()
    {
        return nameIndex;
    }

    /**
     * @return the descriptor index
     */
    public int getDescriptorIndex()
    {
        return descriptorIndex;
    }

    /**
     * Repoints the pair at a different member name.
     * @param nameIndex the new CONSTANT_Utf8 index
     */
    public void setNameIndex(int nameIndex)
    {
        this.nameIndex = nameIndex;
    }

    /**
     * Repoints the pair at a different descriptor.
     * @param descriptorIndex the new CONSTANT_Utf8 index
     */
    public void setDescriptorIndex(int descriptorIndex)
    {
        this.descriptorIndex = descriptorIndex;
    }

    @Override
    public String toString()
    {
        return "NameAndType{" +
                "nameIndex=" + nameIndex +
                ", descriptorIndex=" + descriptorIndex +
                '}';
    }
}