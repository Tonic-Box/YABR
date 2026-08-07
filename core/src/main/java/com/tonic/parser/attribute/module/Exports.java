package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an exports entry in the Module attribute.
 */
public class Exports
{
    private final ConstPool constPool;
    private final int exportsIndex, exportsFlags;
    private final List<Integer> exportsTo;

    /**
     * Creates an exports entry over raw constant-pool indices.
     * @param constPool pool used to resolve the package names
     * @param exportsIndex CONSTANT_Package index of the exported package
     * @param exportsFlags the ACC_ flags on the entry
     * @param exportsTo CONSTANT_Module indices of the targeted modules, empty when unqualified
     */
    public Exports(ConstPool constPool, int exportsIndex, int exportsFlags, List<Integer> exportsTo)
    {
        this.constPool = constPool;
        this.exportsIndex = exportsIndex;
        this.exportsFlags = exportsFlags;
        this.exportsTo = exportsTo;
    }

    /**
     * @return the exports index
     */
    public int getExportsIndex()
    {
        return exportsIndex;
    }

    /**
     * @return the exports flags
     */
    public int getExportsFlags()
    {
        return exportsFlags;
    }

    /**
     * @return the exports to
     */
    public List<Integer> getExportsTo()
    {
        return exportsTo;
    }

    @Override
    public String toString()
    {
        String exportsPackage = resolveExportsPackage();
        String exportsToPackages = resolveExportsToPackages();
        return "Exports{" +
                "exportsPackage='" + exportsPackage + '\'' +
                ", exportsFlags=" + exportsFlags +
                ", exportsToPackages=" + exportsToPackages +
                '}';
    }

    private String resolveExportsPackage()
    {
        Item<?> utf8Item = constPool.getItem(exportsIndex);
        if (utf8Item instanceof Utf8Item)
        {
            return ((Utf8Item) utf8Item).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveExportsToPackages()
    {
        if (exportsTo.isEmpty())
        {
            return "None";
        }
        List<String> packages = new ArrayList<>();
        for (int toIndex : exportsTo)
        {
            Item<?> utf8Item = constPool.getItem(toIndex);
            if (utf8Item instanceof Utf8Item)
            {
                packages.add(((Utf8Item) utf8Item).getValue().replace('/', '.'));
            }
            else
            {
                packages.add("Unknown");
            }
        }
        return packages.toString();
    }
}