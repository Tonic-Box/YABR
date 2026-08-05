package com.tonic.parser.constpool.structure;

/**
 * The mutable class-index and name-and-type-index pair behind a constant pool method reference.
 */
public class MethodRef
{
    private int classIndex;
    private int nameAndTypeIndex;

    /**
     * @param classIndex CONSTANT_Class index of the owner
     * @param nameAndTypeIndex CONSTANT_NameAndType index for the method name and descriptor
     */
    public MethodRef(int classIndex, int nameAndTypeIndex)
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
     * @param classIndex new CONSTANT_Class index for the owner
     */
    public void setClassIndex(int classIndex)
    {
        this.classIndex = classIndex;
    }

    /**
     * @param nameAndTypeIndex new CONSTANT_NameAndType index
     */
    public void setNameAndTypeIndex(int nameAndTypeIndex)
    {
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    @Override
    public String toString()
    {
        return "MethodRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}