package com.tonic.parser.constpool.structure;

/**
 * The index pair carried by a CONSTANT_InvokeDynamic pool entry.
 */
public class InvokeDynamic
{
    private final int bootstrapMethodAttrIndex;
    private final int nameAndTypeIndex;

    /**
     * Creates an invokedynamic reference from its raw indices.
     * @param bootstrapMethodAttrIndex index into the BootstrapMethods attribute
     * @param nameAndTypeIndex constant pool index of the call site name and type
     */
    public InvokeDynamic(int bootstrapMethodAttrIndex, int nameAndTypeIndex)
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
        return "InvokeDynamic{" +
                "bootstrapMethodAttrIndex=" + bootstrapMethodAttrIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}
