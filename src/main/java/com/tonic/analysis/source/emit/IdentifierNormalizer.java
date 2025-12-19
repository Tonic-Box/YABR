package com.tonic.analysis.source.emit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for normalizing identifiers based on the configured mode.
 * Handles Unicode escaping and semantic renaming of obfuscated identifiers.
 */
public class IdentifierNormalizer {

    private final IdentifierMode mode;

    // Counters for semantic renaming
    private final AtomicInteger methodCounter = new AtomicInteger(0);
    private final AtomicInteger fieldCounter = new AtomicInteger(0);
    private final AtomicInteger classCounter = new AtomicInteger(0);
    private final AtomicInteger varCounter = new AtomicInteger(0);
    private final AtomicInteger constCounter = new AtomicInteger(0);

    // Cache for consistent renaming (same input -> same output)
    private final Map<String, String> methodRenames = new HashMap<>();
    private final Map<String, String> fieldRenames = new HashMap<>();
    private final Map<String, String> classRenames = new HashMap<>();
    private final Map<String, String> varRenames = new HashMap<>();
    private final Map<String, String> constRenames = new HashMap<>();

    public IdentifierNormalizer(IdentifierMode mode) {
        this.mode = mode;
    }

    /**
     * Normalizes an identifier based on the configured mode.
     */
    public String normalize(String identifier, IdentifierType type) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }

        switch (mode) {
            case RAW:
                return identifier;
            case UNICODE_ESCAPE:
                return escapeToUnicode(identifier);
            case SEMANTIC_RENAME:
                return semanticRename(identifier, type);
            default:
                return identifier;
        }
    }

    /**
     * Normalizes a class name, handling internal format (with slashes).
     */
    public String normalizeClassName(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return internalName;
        }

        switch (mode) {
            case RAW:
                return internalName;
            case UNICODE_ESCAPE:
                // Escape each part of the class name separately, preserve slashes/dots
                return escapeClassNameParts(internalName);
            case SEMANTIC_RENAME:
                return semanticRenameClassName(internalName);
            default:
                return internalName;
        }
    }

    /**
     * Escapes non-standard characters to Unicode escape sequences.
     * Only escapes characters that are not valid in Java identifiers.
     */
    public String escapeToUnicode(String s) {
        if (s == null) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isValidIdentifierChar(c, i == 0)) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04x", (int) c));
            }
        }
        return sb.toString();
    }

    /**
     * Escapes class name parts while preserving package separators.
     */
    private String escapeClassNameParts(String className) {
        // Handle both internal (/) and source (.) format
        String separator = className.contains("/") ? "/" : "\\.";
        String[] parts = className.split(separator);
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(className.contains("/") ? "/" : ".");
            }
            result.append(escapeToUnicode(parts[i]));
        }
        return result.toString();
    }

    /**
     * Generates a semantic name for an invalid identifier.
     */
    private String semanticRename(String identifier, IdentifierType type) {
        if (isValidJavaIdentifier(identifier)) {
            return identifier;
        }

        Map<String, String> cache;
        AtomicInteger counter;
        String prefix;

        switch (type) {
            case METHOD:
                cache = methodRenames;
                counter = methodCounter;
                prefix = "method_";
                break;
            case FIELD:
                cache = fieldRenames;
                counter = fieldCounter;
                prefix = "field_";
                break;
            case CLASS:
                cache = classRenames;
                counter = classCounter;
                prefix = "Class_";
                break;
            case VARIABLE:
                cache = varRenames;
                counter = varCounter;
                prefix = "var_";
                break;
            case CONSTANT:
                cache = constRenames;
                counter = constCounter;
                prefix = "const_";
                break;
            default:
                cache = varRenames;
                counter = varCounter;
                prefix = "id_";
        }

        return cache.computeIfAbsent(identifier, k -> prefix + counter.incrementAndGet());
    }

    /**
     * Generates a semantic class name, preserving package structure.
     */
    private String semanticRenameClassName(String internalName) {
        // Handle both internal (/) and source (.) format
        boolean isInternal = internalName.contains("/");
        String separator = isInternal ? "/" : "\\.";
        String[] parts = internalName.split(separator);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(isInternal ? "/" : ".");
            }

            String part = parts[i];
            if (isValidJavaIdentifier(part)) {
                result.append(part);
            } else {
                // Only rename the class name part (last), keep package parts as escaped
                if (i == parts.length - 1) {
                    result.append(semanticRename(part, IdentifierType.CLASS));
                } else {
                    result.append(escapeToUnicode(part));
                }
            }
        }
        return result.toString();
    }

    /**
     * Checks if a string is a valid Java identifier.
     */
    public boolean isValidJavaIdentifier(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }

        if (!Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }

        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }

        return !isJavaKeyword(s);
    }

    /**
     * Checks if a character is valid in a Java identifier.
     */
    private boolean isValidIdentifierChar(char c, boolean isStart) {
        if (isStart) {
            return Character.isJavaIdentifierStart(c);
        }
        return Character.isJavaIdentifierPart(c);
    }

    /**
     * Checks if the string is a Java keyword.
     */
    private boolean isJavaKeyword(String s) {
        switch (s) {
            case "abstract": case "assert": case "boolean": case "break":
            case "byte": case "case": case "catch": case "char":
            case "class": case "const": case "continue": case "default":
            case "do": case "double": case "else": case "enum":
            case "extends": case "final": case "finally": case "float":
            case "for": case "goto": case "if": case "implements":
            case "import": case "instanceof": case "int": case "interface":
            case "long": case "native": case "new": case "package":
            case "private": case "protected": case "public": case "return":
            case "short": case "static": case "strictfp": case "super":
            case "switch": case "synchronized": case "this": case "throw":
            case "throws": case "transient": case "try": case "void":
            case "volatile": case "while": case "true": case "false": case "null":
                return true;
            default:
                return false;
        }
    }

    /**
     * Resets all counters and caches. Useful for starting fresh per class.
     */
    public void reset() {
        methodCounter.set(0);
        fieldCounter.set(0);
        classCounter.set(0);
        varCounter.set(0);
        constCounter.set(0);
        methodRenames.clear();
        fieldRenames.clear();
        classRenames.clear();
        varRenames.clear();
        constRenames.clear();
    }

    /**
     * Types of identifiers for semantic renaming.
     */
    public enum IdentifierType {
        METHOD,
        FIELD,
        CLASS,
        VARIABLE,
        CONSTANT
    }
}
