package com.tonic.parser.attribute.anotation;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

/**
 * Represents an enum constant in an annotation.
 */
public class EnumConst {
    private final ConstPool constPool;
    @Getter
    private final int typeNameIndex, constNameIndex;;

    public EnumConst(ConstPool constPool, int typeNameIndex, int constNameIndex) {
        this.constPool = constPool;
        this.typeNameIndex = typeNameIndex;
        this.constNameIndex = constNameIndex;
    }

    @Override
    public String toString() {
        String typeName = resolveTypeName();
        String constName = resolveConstName();
        return "EnumConst{typeName='" + typeName + "', constName='" + constName + "'}";
    }

    private String resolveTypeName() {
        Item<?> typeNameItem = constPool.getItem(typeNameIndex);
        if (typeNameItem instanceof Utf8Item) {
            return ((Utf8Item) typeNameItem).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveConstName() {
        Item<?> constNameItem = constPool.getItem(constNameIndex);
        if (constNameItem instanceof Utf8Item) {
            return ((Utf8Item) constNameItem).getValue();
        }
        return "Unknown";
    }
}