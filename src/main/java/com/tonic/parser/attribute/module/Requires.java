package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

/**
 * Represents a 'requires' entry in the Module attribute.
 */
public class Requires {
    private final ConstPool constPool;
    @Getter
    private final int requiresIndex, requiresFlags, requiresVersionIndex;

    public Requires(ConstPool constPool, int requiresIndex, int requiresFlags, int requiresVersionIndex) {
        this.constPool = constPool;
        this.requiresIndex = requiresIndex;
        this.requiresFlags = requiresFlags;
        this.requiresVersionIndex = requiresVersionIndex;
    }

    @Override
    public String toString() {
        String requiresName = resolveRequiresName();
        String requiresVersion = resolveRequiresVersion();
        return "Requires{" +
                "requiresName='" + requiresName + '\'' +
                ", requiresFlags=" + requiresFlags +
                ", requiresVersion='" + requiresVersion + '\'' +
                '}';
    }

    private String resolveRequiresName() {
        Item<?> classRefItem = constPool.getItem(requiresIndex);
        if (classRefItem instanceof ClassRefItem) {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = constPool.getItem(nameIndex);
            if (utf8Item instanceof Utf8Item) {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }

    private String resolveRequiresVersion() {
        if (requiresVersionIndex == 0) {
            return "None";
        }
        Item<?> utf8Item = constPool.getItem(requiresVersionIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}