package com.tonic.analysis.ssa.llvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Collects the static fields referenced during lowering and renders module-level global variables for them.
 */
final class GlobalCollector
{

    private static final class Entry
    {
        final LlvmType type;
        final String ownerClass;

        Entry(LlvmType type, String ownerClass)
        {
            this.type = type;
            this.ownerClass = ownerClass;
        }
    }

    private final TreeMap<String, Entry> globals = new TreeMap<>();

    void note(String mangledSymbol, LlvmType type, String ownerClass)
    {
        globals.putIfAbsent(mangledSymbol, new Entry(type, ownerClass));
    }

    /**
     * Renders the global definitions.
     */
    List<String> renderGlobals(Set<String> definedOwnerClasses)
    {
        List<String> out = new ArrayList<>();
        for (var e : globals.entrySet())
        {
            Entry entry = e.getValue();
            if (definedOwnerClasses.contains(entry.ownerClass))
            {
                out.add(e.getKey() + " = global " + entry.type.render() + " zeroinitializer");
            }
            else
            {
                out.add(e.getKey() + " = external global " + entry.type.render());
            }
        }
        return out;
    }
}
