package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

/**
 * A uses entry of the Module attribute, naming a service interface the module
 * consumes.
 */
public class Uses
{
    private final ConstPool constPool;
    private final int usesIndex;

    /**
     * Creates a uses entry.
     * @param constPool the pool the class reference is resolved against
     * @param usesIndex constant pool index of the service interface class
     */
    public Uses(ConstPool constPool, int usesIndex)
    {
        this.constPool = constPool;
        this.usesIndex = usesIndex;
    }

    /**
     * @return the uses index
     */
    public int getUsesIndex()
    {
        return usesIndex;
    }

    @Override
    public String toString()
    {
        String usesClass = resolveUsesClass();
        return "Uses{" +
                "usesClass='" + usesClass + '\'' +
                '}';
    }

    private String resolveUsesClass()
    {
        Item<?> classRefItem = constPool.getItem(usesIndex);
        if (classRefItem instanceof ClassRefItem)
        {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = constPool.getItem(nameIndex);
            if (utf8Item instanceof Utf8Item)
            {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }
}