package com.tonic.parser.attribute.table;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

/**
 * Represents an entry in the InnerClasses attribute.
 * Describes the relationship between an inner class and its enclosing class.
 */
public class InnerClassEntry
{
    private final ConstPool constPool;
    private final int innerClassInfoIndex, outerClassInfoIndex, innerClassAccessFlags;
    private int innerNameIndex;

    /**
     * Constructs an inner class entry.
     * @param constPool the constant pool for resolving references
     * @param innerClassInfoIndex constant pool index of the inner class
     * @param outerClassInfoIndex constant pool index of the outer class, or 0 if none
     * @param innerNameIndex constant pool index of the simple name, or 0 if anonymous
     * @param innerClassAccessFlags access flags for the inner class
     */
    public InnerClassEntry(ConstPool constPool, int innerClassInfoIndex, int outerClassInfoIndex, int innerNameIndex, int innerClassAccessFlags)
    {
        this.constPool = constPool;
        this.innerClassInfoIndex = innerClassInfoIndex;
        this.outerClassInfoIndex = outerClassInfoIndex;
        this.innerNameIndex = innerNameIndex;
        this.innerClassAccessFlags = innerClassAccessFlags;
    }

    /**
     * @return the inner class info index
     */
    public int getInnerClassInfoIndex()
    {
        return innerClassInfoIndex;
    }

    /**
     * @return the outer class info index
     */
    public int getOuterClassInfoIndex()
    {
        return outerClassInfoIndex;
    }

    /**
     * @return the inner class access flags
     */
    public int getInnerClassAccessFlags()
    {
        return innerClassAccessFlags;
    }

    /**
     * @return the inner name index
     */
    public int getInnerNameIndex()
    {
        return innerNameIndex;
    }

    /**
     * Repoints the simple name of the inner class.
     * @param innerNameIndex constant pool index of the new simple name, or 0 to make it anonymous
     */
    public void setInnerNameIndex(int innerNameIndex)
    {
        this.innerNameIndex = innerNameIndex;
    }

    /**
     * Resolves the enclosing class name, with dots for slashes.
     * @return the dotted name, null when there is no outer class, or "Unknown" if the pool entry is not a class ref
     */
    public String getOuterClassName()
    {
        if (outerClassInfoIndex == 0)
        {
            return null;
        }
        return resolveClassName(outerClassInfoIndex);
    }

    /**
     * Resolves the inner class name, with dots for slashes.
     * @return the dotted name, or "Unknown" if the pool entry is not a class ref
     */
    public String getInnerClassName()
    {
        return resolveClassName(innerClassInfoIndex);
    }

    @Override
    public String toString()
    {
        String innerClassName = resolveClassName(innerClassInfoIndex);
        String outerClassName = outerClassInfoIndex == 0 ? "None" : resolveClassName(outerClassInfoIndex);
        String innerName = innerNameIndex == 0 ? "Anonymous" : resolveUtf8(innerNameIndex);
        return "InnerClassEntry{" +
                "innerClassName='" + innerClassName + '\'' +
                ", outerClassName='" + outerClassName + '\'' +
                ", innerName='" + innerName + '\'' +
                ", accessFlags=" + innerClassAccessFlags +
                '}';
    }

    private String resolveClassName(int classInfoIndex)
    {
        Item<?> classRefItem = constPool.getItem(classInfoIndex);
        if (classRefItem instanceof ClassRefItem)
        {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = constPool.getItem(nameIndex);
            if (utf8Item instanceof Utf8Item)
            {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }

    private String resolveUtf8(int utf8Index)
    {
        Item<?> utf8Item = constPool.getItem(utf8Index);
        if (utf8Item instanceof Utf8Item)
        {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}