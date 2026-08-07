package com.tonic.parser.attribute.annotation;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

/**
 * An enum constant used as an annotation value: its type and constant name indices.
 */
public class EnumConst
{
    private final ConstPool constPool;
    private final int typeNameIndex, constNameIndex;

    /**
     * Creates an enum constant reference.
     * @param constPool the pool used to resolve indices
     * @param typeNameIndex constant-pool index of the enum type descriptor Utf8
     * @param constNameIndex constant-pool index of the constant name Utf8
     */
    public EnumConst(ConstPool constPool, int typeNameIndex, int constNameIndex)
    {
        this.constPool = constPool;
        this.typeNameIndex = typeNameIndex;
        this.constNameIndex = constNameIndex;
    }

    /**
     * @return the type name index
     */
    public int getTypeNameIndex()
    {
        return typeNameIndex;
    }

    /**
     * @return the const name index
     */
    public int getConstNameIndex()
    {
        return constNameIndex;
    }

    @Override
    public String toString()
    {
        String typeName = resolveTypeName();
        String constName = resolveConstName();
        return "EnumConst{typeName='" + typeName + "', constName='" + constName + "'}";
    }

    private String resolveTypeName()
    {
        Item<?> typeNameItem = constPool.getItem(typeNameIndex);
        if (typeNameItem instanceof Utf8Item)
        {
            return ((Utf8Item) typeNameItem).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveConstName()
    {
        Item<?> constNameItem = constPool.getItem(constNameIndex);
        if (constNameItem instanceof Utf8Item)
        {
            return ((Utf8Item) constNameItem).getValue();
        }
        return "Unknown";
    }
}