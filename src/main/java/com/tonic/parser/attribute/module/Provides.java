package com.tonic.parser.attribute.module;

import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a 'provides' entry in the Module attribute.
 */
public class Provides {
    private final ConstPool constPool;
    @Getter
    private final int providesWithIndex;
    @Getter
    private final List<Integer> providesWithArguments;

    public Provides(ConstPool constPool, int providesWithIndex, List<Integer> providesWithArguments) {
        this.constPool = constPool;
        this.providesWithIndex = providesWithIndex;
        this.providesWithArguments = providesWithArguments;
    }

    @Override
    public String toString() {
        return "Provides{" +
                "providesWithClass='" + resolveProvidesWithClass() + '\'' +
                ", providesWithClasses=" + resolveProvidesWithClasses() +
                '}';
    }

    private String resolveProvidesWithClass() {
        Item<?> classRefItem = constPool.getItem(providesWithIndex);
        if (classRefItem instanceof ClassRefItem) {
            int nameIndex = ((ClassRefItem) classRefItem).getValue();
            Item<?> utf8Item = constPool.getItem(nameIndex);
            if (utf8Item instanceof Utf8Item) {
                return ((Utf8Item) utf8Item).getValue().replace('/', '.');
            }
        }
        return "Unknown";
    }

    private String resolveProvidesWithClasses() {
        if (providesWithArguments.isEmpty()) {
            return "None";
        }
        List<String> classes = new ArrayList<>();
        for (int argIndex : providesWithArguments) {
            Item<?> classRefItem = constPool.getItem(argIndex);
            if (classRefItem instanceof ClassRefItem) {
                int nameIndex = ((ClassRefItem) classRefItem).getValue();
                Item<?> utf8Item = constPool.getItem(nameIndex);
                if (utf8Item instanceof Utf8Item) {
                    classes.add(((Utf8Item) utf8Item).getValue().replace('/', '.'));
                }
            } else {
                classes.add("Unknown");
            }
        }
        return classes.toString();
    }
}