package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

/**
 * Represents a uses entry in the Module attribute.
 */
public class Uses {
    private final ConstPool constPool;
    @Getter
    private final int usesIndex;

    public Uses(ConstPool constPool, int usesIndex) {
        this.constPool = constPool;
        this.usesIndex = usesIndex;
    }

    @Override
    public String toString() {
        String usesClass = resolveUsesClass();
        return "Uses{" +
                "usesClass='" + usesClass + '\'' +
                '}';
    }

    private String resolveUsesClass() {
        Item<?> classRefItem = constPool.getItem(usesIndex);
        if (classRefItem instanceof ClassRefItem) {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = constPool.getItem(nameIndex);
            if (utf8Item instanceof Utf8Item) {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }
}