package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an 'opens' entry in the Module attribute.
 */
public class Opens {
    private final ConstPool constPool;
    @Getter
    private final int opensIndex, opensFlags;
    @Getter
    private final List<Integer> opensTo;

    public Opens(ConstPool constPool, int opensIndex, int opensFlags, List<Integer> opensTo) {
        this.constPool = constPool;
        this.opensIndex = opensIndex;
        this.opensFlags = opensFlags;
        this.opensTo = opensTo;
    }

    @Override
    public String toString() {
        String opensPackage = resolveOpensPackage();
        String opensToPackages = resolveOpensToPackages();
        return "Opens{" +
                "opensPackage='" + opensPackage + '\'' +
                ", opensFlags=" + opensFlags +
                ", opensToPackages=" + opensToPackages +
                '}';
    }

    private String resolveOpensPackage() {
        Item<?> utf8Item = constPool.getItem(opensIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveOpensToPackages() {
        if (opensTo.isEmpty()) {
            return "None";
        }
        List<String> packages = new ArrayList<>();
        for (int toIndex : opensTo) {
            Item<?> utf8Item = constPool.getItem(toIndex);
            if (utf8Item instanceof Utf8Item) {
                packages.add(((Utf8Item) utf8Item).getValue().replace('/', '.'));
            } else {
                packages.add("Unknown");
            }
        }
        return packages.toString();
    }
}