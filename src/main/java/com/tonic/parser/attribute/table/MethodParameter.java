package com.tonic.parser.attribute.table;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import lombok.Getter;

/**
 * Represents a method parameter.
 */
public class MethodParameter {
    private final ConstPool constPool;
    @Getter
    private final int nameIndex, accessFlags;

    public MethodParameter(ConstPool constPool, int nameIndex, int accessFlags) {
        this.constPool = constPool;
        this.nameIndex = nameIndex;
        this.accessFlags = accessFlags;
    }

    public String getName() {
        Item<?> utf8Item = constPool.getItem(nameIndex);
        if (utf8Item instanceof com.tonic.parser.constpool.Utf8Item) {
            return ((com.tonic.parser.constpool.Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }

    @Override
    public String toString() {
        return "MethodParameter{" +
                "name='" + getName() + '\'' +
                ", accessFlags=" + accessFlags +
                '}';
    }
}