package com.tonic.analysis.source.recovery;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EnumSwitchMapRegistry {

    private static final EnumSwitchMapRegistry INSTANCE = new EnumSwitchMapRegistry();

    private final Map<String, Map<Integer, String>> switchMaps = new ConcurrentHashMap<>();

    public static EnumSwitchMapRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers one {@code caseValue -> enumConstant} entry of a switch map. The map is keyed by the
     * holder class that declares the {@code $SwitchMap$} field as well as the enum: javac emits a
     * separate holder per class that switches on the enum, each with its own dense numbering, so
     * keying by the enum alone lets one class's mapping overwrite another's and mislabels the cases.
     */
    public void registerMapping(String holderClass, String enumClassName, int caseValue, String enumConstant) {
        switchMaps.computeIfAbsent(key(holderClass, enumClassName), k -> new ConcurrentHashMap<>())
                  .put(caseValue, enumConstant);
    }

    public String lookupEnumConstant(String holderClass, String enumClassName, int caseValue) {
        Map<Integer, String> mapping = switchMaps.get(key(holderClass, enumClassName));
        if (mapping == null) {
            return null;
        }
        return mapping.get(caseValue);
    }

    public boolean hasMapping(String holderClass, String enumClassName) {
        return switchMaps.containsKey(key(holderClass, enumClassName));
    }

    public void clear() {
        switchMaps.clear();
    }

    /** The registry key: the holder class (normalized to internal form) plus the enum class. */
    private static String key(String holderClass, String enumClassName) {
        String holder = holderClass == null ? "" : holderClass.replace('.', '/');
        return holder + "#" + enumClassName;
    }

    public static String parseEnumClassFromFieldName(String fieldName) {
        if (fieldName == null || !fieldName.startsWith("$SwitchMap$")) {
            return null;
        }
        String remainder = fieldName.substring("$SwitchMap$".length());
        StringBuilder result = new StringBuilder();
        String[] parts = remainder.split("\\$");
        boolean prevWasClass = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isClass = !part.isEmpty() && Character.isUpperCase(part.charAt(0));
            if (i > 0) {
                result.append(prevWasClass ? "$" : "/");
            }
            result.append(part);
            prevWasClass = isClass;
        }
        return result.toString();
    }
}
