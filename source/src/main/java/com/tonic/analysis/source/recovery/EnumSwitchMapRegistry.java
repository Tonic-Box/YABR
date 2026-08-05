package com.tonic.analysis.source.recovery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide store of recovered javac $SwitchMap$ tables, keyed by holder class and enum so two
 * classes switching on the same enum keep separate numberings.
 */
public class EnumSwitchMapRegistry
{

    private static final EnumSwitchMapRegistry INSTANCE = new EnumSwitchMapRegistry();

    private final Map<String, Map<Integer, String>> switchMaps = new ConcurrentHashMap<>();

    /**
     * @return the instance
     */
    public static EnumSwitchMapRegistry getInstance()
    {
        return INSTANCE;
    }

    /**
     * Registers one {@code caseValue -> enumConstant} entry of a switch map. The map is keyed by the
     * holder class that declares the {@code $SwitchMap$} field as well as the enum: javac emits a
     * separate holder per class that switches on the enum, each with its own dense numbering, so
     * keying by the enum alone lets one class's mapping overwrite another's and mislabels the cases.
     *
     * @param holderClass the class declaring the $SwitchMap$ field
     * @param enumClassName the enum being switched on
     * @param caseValue the dense case value javac assigned
     * @param enumConstant the enum constant that value stands for
     */
    public void registerMapping(String holderClass, String enumClassName, int caseValue, String enumConstant)
    {
        switchMaps.computeIfAbsent(key(holderClass, enumClassName), k -> new ConcurrentHashMap<>())
                  .put(caseValue, enumConstant);
    }

    /**
     * Resolves a switch case value back to its enum constant name.
     *
     * @param holderClass the class declaring the $SwitchMap$ field
     * @param enumClassName the enum being switched on
     * @param caseValue the dense case value
     * @return the constant name, or null if nothing was registered for it
     */
    public String lookupEnumConstant(String holderClass, String enumClassName, int caseValue)
    {
        Map<Integer, String> mapping = switchMaps.get(key(holderClass, enumClassName));
        if (mapping == null)
        {
            return null;
        }
        return mapping.get(caseValue);
    }

    /**
     * @param holderClass the class declaring the $SwitchMap$ field
     * @param enumClassName the enum being switched on
     * @return true if any entry has been registered for the pair
     */
    public boolean hasMapping(String holderClass, String enumClassName)
    {
        return switchMaps.containsKey(key(holderClass, enumClassName));
    }

    /**
     * Drops every registered mapping.
     */
    public void clear()
    {
        switchMaps.clear();
    }

    /**
     * The registry key: the holder class (normalized to internal form) plus the enum class.
     */
    private static String key(String holderClass, String enumClassName)
    {
        String holder = holderClass == null ? "" : holderClass.replace('.', '/');
        return holder + "#" + enumClassName;
    }

    /**
     * Recovers the enum's internal name from a $SwitchMap$ field name, treating a segment that
     * starts with an upper-case letter as a nested class rather than a package part.
     *
     * @param fieldName the synthetic field name
     * @return the enum's internal name, or null if the name is not a $SwitchMap$ field
     */
    public static String parseEnumClassFromFieldName(String fieldName)
    {
        if (fieldName == null || !fieldName.startsWith("$SwitchMap$"))
        {
            return null;
        }
        String remainder = fieldName.substring("$SwitchMap$".length());
        StringBuilder result = new StringBuilder();
        String[] parts = remainder.split("\\$");
        boolean prevWasClass = false;
        for (int i = 0; i < parts.length; i++)
        {
            String part = parts[i];
            boolean isClass = !part.isEmpty() && Character.isUpperCase(part.charAt(0));
            if (i > 0)
            {
                result.append(prevWasClass ? "$" : "/");
            }
            result.append(part);
            prevWasClass = isClass;
        }
        return result.toString();
    }
}
