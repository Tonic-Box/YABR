package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.util.ArrayList;
import java.util.List;

/**
 * One provides entry of a Module attribute - a service interface plus the
 * implementation classes offered for it, held as constant pool indices.
 */
public class Provides
{
    private final ConstPool constPool;
    private final int providesWithIndex;
    private final List<Integer> providesWithArguments;

    /**
     * Creates a provides entry.
     * @param constPool the pool the indices resolve against
     * @param providesWithIndex class index of the service interface
     * @param providesWithArguments class indices of the implementations
     */
    public Provides(ConstPool constPool, int providesWithIndex, List<Integer> providesWithArguments)
    {
        this.constPool = constPool;
        this.providesWithIndex = providesWithIndex;
        this.providesWithArguments = providesWithArguments;
    }

    /**
     * @return the provides with index
     */
    public int getProvidesWithIndex()
    {
        return providesWithIndex;
    }

    /**
     * @return the provides with arguments
     */
    public List<Integer> getProvidesWithArguments()
    {
        return providesWithArguments;
    }

    @Override
    public String toString()
    {
        return "Provides{" +
                "providesWithClass='" + resolveProvidesWithClass() + '\'' +
                ", providesWithClasses=" + resolveProvidesWithClasses() +
                '}';
    }

    private String resolveProvidesWithClass()
    {
        Item<?> classRefItem = constPool.getItem(providesWithIndex);
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

    private String resolveProvidesWithClasses()
    {
        if (providesWithArguments.isEmpty())
        {
            return "None";
        }
        List<String> classes = new ArrayList<>();
        for (int argIndex : providesWithArguments)
        {
            Item<?> classRefItem = constPool.getItem(argIndex);
            if (classRefItem instanceof ClassRefItem)
            {
                int nameIndex = ((ClassRefItem) classRefItem).getValue();
                Item<?> utf8Item = constPool.getItem(nameIndex);
                if (utf8Item instanceof Utf8Item)
                {
                    classes.add(((Utf8Item) utf8Item).getValue().replace('/', '.'));
                }
            }
            else
            {
                classes.add("Unknown");
            }
        }
        return classes.toString();
    }
}