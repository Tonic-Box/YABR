package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.util.ArrayList;
import java.util.List;

/**
 * One opens directive of a Module attribute.
 */
public class Opens
{
    private final ConstPool constPool;
    private final int opensIndex, opensFlags;
    private final List<Integer> opensTo;

    /**
     * Creates an opens entry from its raw constant pool indices.
     * @param constPool the pool used to resolve the package names
     * @param opensIndex the pool index of the opened package
     * @param opensFlags the ACC_ flags on the directive
     * @param opensTo pool indices of the modules the package is opened to, empty for unqualified
     */
    public Opens(ConstPool constPool, int opensIndex, int opensFlags, List<Integer> opensTo)
    {
        this.constPool = constPool;
        this.opensIndex = opensIndex;
        this.opensFlags = opensFlags;
        this.opensTo = opensTo;
    }

    /**
     * @return the opens index
     */
    public int getOpensIndex()
    {
        return opensIndex;
    }

    /**
     * @return the opens flags
     */
    public int getOpensFlags()
    {
        return opensFlags;
    }

    /**
     * @return the opens to
     */
    public List<Integer> getOpensTo()
    {
        return opensTo;
    }

    @Override
    public String toString()
    {
        String opensPackage = resolveOpensPackage();
        String opensToPackages = resolveOpensToPackages();
        return "Opens{" +
                "opensPackage='" + opensPackage + '\'' +
                ", opensFlags=" + opensFlags +
                ", opensToPackages=" + opensToPackages +
                '}';
    }

    private String resolveOpensPackage()
    {
        Item<?> utf8Item = constPool.getItem(opensIndex);
        if (utf8Item instanceof Utf8Item)
        {
            return ((Utf8Item) utf8Item).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveOpensToPackages()
    {
        if (opensTo.isEmpty())
        {
            return "None";
        }
        List<String> packages = new ArrayList<>();
        for (int toIndex : opensTo)
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