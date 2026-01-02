package com.tonic.analysis.source.recovery;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EnumSwitchMapRegistry {

    private static final EnumSwitchMapRegistry INSTANCE = new EnumSwitchMapRegistry();

    private final Map<String, Map<Integer, String>> switchMaps = new ConcurrentHashMap<>();

    public static EnumSwitchMapRegistry getInstance() {
        return INSTANCE;
    }

    public void registerMapping(String enumClassName, int caseValue, String enumConstant) {
        switchMaps.computeIfAbsent(enumClassName, k -> new ConcurrentHashMap<>())
                  .put(caseValue, enumConstant);
    }

    public String lookupEnumConstant(String enumClassName, int caseValue) {
        Map<Integer, String> mapping = switchMaps.get(enumClassName);
        if (mapping == null) {
            return null;
        }
        return mapping.get(caseValue);
    }

    public boolean hasMapping(String enumClassName) {
        return switchMaps.containsKey(enumClassName);
    }

    public void clear() {
        switchMaps.clear();
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
