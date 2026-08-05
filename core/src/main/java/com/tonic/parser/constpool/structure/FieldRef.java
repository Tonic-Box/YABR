package com.tonic.parser.constpool.structure;

/**
 * The index pair of a CONSTANT_Fieldref entry - owning class and name-and-type.
 */
public class FieldRef
{
    private int classIndex;
    private int nameAndTypeIndex;

    /**
     * Creates a field reference from its two constant pool indices.
     * @param classIndex the index of the CONSTANT_Class entry
     * @param nameAndTypeIndex the index of the CONSTANT_NameAndType entry
     */
    public FieldRef(int classIndex, int nameAndTypeIndex)
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
     * Repoints the reference at another owning class entry.
     * @param classIndex the constant pool index of the CONSTANT_Class entry
     */
    public void setClassIndex(int classIndex)
    {
        this.classIndex = classIndex;
    }

    /**
     * Repoints the reference at another name-and-type entry.
     * @param nameAndTypeIndex the constant pool index of the CONSTANT_NameAndType entry
     */
    public void setNameAndTypeIndex(int nameAndTypeIndex)
    {
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    @Override
    public String toString()
    {
        return "FieldRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}