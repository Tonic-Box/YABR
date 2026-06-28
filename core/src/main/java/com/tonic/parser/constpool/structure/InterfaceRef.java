package com.tonic.parser.constpool.structure;

/**
 * Represents an Interface Method Reference in the constant pool.
 */
public class InterfaceRef {
    private int classIndex;
    private final int nameAndTypeIndex;

    public InterfaceRef(int classIndex, int nameAndTypeIndex) {
        this.classIndex = classIndex;
        this.nameAndTypeIndex = nameAndTypeIndex;
    }

    public int getClassIndex() {
        return classIndex;
    }

    public int getNameAndTypeIndex() {
        return nameAndTypeIndex;
    }

    /**
     * Repoints this reference at a different owner class.
     *
     * @param classIndex the new CONSTANT_Class index
     */
    public void setClassIndex(int classIndex) {
        this.classIndex = classIndex;
    }

    @Override
    public String toString() {
        return "InterfaceRef{" +
                "classIndex=" + classIndex +
                ", nameAndTypeIndex=" + nameAndTypeIndex +
                '}';
    }
}