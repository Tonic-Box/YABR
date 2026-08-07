package com.tonic.parser.constpool.structure;

/**
 * Represents an Interface Method Reference in the constant pool.
 */
public class InterfaceRef
{
    private int classIndex;
    private final int nameAndTypeIndex;

    /**
     * Creates an interface method reference from its two constant-pool indices.
     * @param classIndex CONSTANT_Class index of the owning interface
     * @param nameAndTypeIndex CONSTANT_NameAndType index of the method
     */
    public InterfaceRef(int classIndex, int nameAndTypeIndex)
    {
        this.classIndex = classIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    /**
     * @return the class index
     */
    public int getClassIndex()
    {
        return classIndex;
    }

    /**
     * @return the name and type index
     */
    public int getNameAndTypeIndex()
    {
        return nameAndTypeIndex;
    }

    /**
     * Repoints this reference at a different owner class.
     * @param classIndex the new CONSTANT_Class index
     */
    public void setClassIndex(int classIndex)
    {
        this.classIndex = classIndex;
    }

    @Override
    public String toString()
    {
        return "InterfaceRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}