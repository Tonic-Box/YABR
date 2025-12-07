package com.tonic.parser.attribute.table;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import lombok.Getter;

/**
 * Represents a method parameter in the MethodParameters attribute.
 * Contains metadata about formal parameters including name and access flags.
 */
public class MethodParameter {
    private final ConstPool constPool;
    @Getter
    private final int nameIndex, accessFlags;

    /**
     * Constructs a method parameter entry.
     *
     * @param constPool the constant pool for resolving references
     * @param nameIndex constant pool index of the parameter name, or 0 if unnamed
     * @param accessFlags access flags for the parameter
     */
    public MethodParameter(ConstPool constPool, int nameIndex, int accessFlags) {
        this.constPool = constPool;
        this.nameIndex = nameIndex;
        this.accessFlags = accessFlags;
    }

    /**
     * Returns the parameter name.
     *
     * @return the parameter name, or "&lt;unnamed&gt;" if not specified
     */
    public String getName() {
        if (nameIndex == 0) {
            return "<unnamed>";
        }
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