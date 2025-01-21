package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an 'exports' entry in the Module attribute.
 */
public class Exports {
    private final ConstPool constPool;
    @Getter
    private final int exportsIndex, exportsFlags;
    @Getter
    private final List<Integer> exportsTo;

    public Exports(ConstPool constPool, int exportsIndex, int exportsFlags, List<Integer> exportsTo) {
        this.constPool = constPool;
        this.exportsIndex = exportsIndex;
        this.exportsFlags = exportsFlags;
        this.exportsTo = exportsTo;
    }

    @Override
    public String toString() {
        String exportsPackage = resolveExportsPackage();
        String exportsToPackages = resolveExportsToPackages();
        return "Exports{" +
                "exportsPackage='" + exportsPackage + '\'' +
                ", exportsFlags=" + exportsFlags +
                ", exportsToPackages=" + exportsToPackages +
                '}';
    }

    private String resolveExportsPackage() {
        Item<?> utf8Item = constPool.getItem(exportsIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveExportsToPackages() {
        if (exportsTo.isEmpty()) {
            return "None";
        }
        List<String> packages = new ArrayList<>();
        for (int toIndex : exportsTo) {
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