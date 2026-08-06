package com.tonic.parser.constpool.structure;

/**
 * Represents a CONSTANT_Dynamic entry structure in the constant pool.
 */
public class ConstantDynamic
{
    private final int bootstrapMethodAttrIndex;
    private final int nameAndTypeIndex;

    /**
     * @param bootstrapMethodAttrIndex index into the BootstrapMethods attribute
     * @param nameAndTypeIndex CONSTANT_NameAndType index for the constant's name and descriptor
     */
    public ConstantDynamic(int bootstrapMethodAttrIndex, int nameAndTypeIndex)
    {
        this.bootstrapMethodAttrIndex = bootstrapMethodAttrIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    /**
     * @return the bootstrap method attr index
     */
    public int getBootstrapMethodAttrIndex()
    {
        return bootstrapMethodAttrIndex;
    }

    /**
     * @return the name and type index
     */
    public int getNameAndTypeIndex()
    {
        return nameAndTypeIndex;
    }

    @Override
    public String toString()
    {
        return "ConstantDynamic{" +
                "bootstrapMethodAttrIndex=" + bootstrapMethodAttrIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}
