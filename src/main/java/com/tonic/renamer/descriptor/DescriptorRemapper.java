package com.tonic.renamer.descriptor;

import java.util.Map;
import java.util.function.Function;

/**
 * Remaps class references within method and field descriptors.
 *
 * Handles transformations like:
 * - (Lcom/old/Type;I)Lcom/old/Result; -> (Lcom/new/Type;I)Lcom/new/Result;
 * - [Lcom/old/Type; -> [Lcom/new/Type;
 * - [[Lcom/old/Type; -> [[Lcom/new/Type;
 */
public class DescriptorRemapper {

    private final Function<String, String> classMapper;

    /**
     * Creates a remapper with the given class name mapping function.
     *
     * @param classMapper Function that maps old class names to new ones (returns null or same for no change)
     */
    public DescriptorRemapper(Function<String, String> classMapper) {
        this.classMapper = classMapper;
    }

    /**
     * Creates a remapper from a class mapping map.
     *
     * @param classMappings Map from old class names to new class names
     */
    public DescriptorRemapper(Map<String, String> classMappings) {
        this.classMapper = classMappings::get;
    }

    /**
     * Remaps all class references in a method descriptor.
     *
     * @param descriptor The method descriptor (e.g., "(Lcom/old/Type;I)V")
     * @return The remapped descriptor
     */
    public String remapMethodDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return descriptor;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                // Object type: Lclassname;
                int end = descriptor.indexOf(';', i);
                if (end == -1) {
                    // Malformed descriptor, just copy rest
                    result.append(descriptor.substring(i));
                    break;
                }
                String className = descriptor.substring(i + 1, end);
                String mapped = classMapper.apply(className);
                if (mapped != null && !mapped.equals(className)) {
                    result.append('L').append(mapped).append(';');
                } else {
                    result.append(descriptor, i, end + 1);
                }
                i = end + 1;
            } else if (c == '[') {
                // Array type, just append and continue to get element type
                result.append(c);
                i++;
            } else if (c == '(' || c == ')') {
                // Parameter list delimiters
                result.append(c);
                i++;
            } else {
                // Primitive types: B, C, D, F, I, J, S, Z, V
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /**
     * Remaps all class references in a field descriptor.
     *
     * @param descriptor The field descriptor (e.g., "Lcom/old/Type;")
     * @return The remapped descriptor
     */
    public String remapFieldDescriptor(String descriptor) {
        // Field descriptors are just single types, same logic applies
        return remapMethodDescriptor(descriptor);
    }

    /**
     * Remaps a single type descriptor.
     *
     * @param typeDescriptor A single type (e.g., "Lcom/old/Type;", "I", "[Lcom/old/Type;")
     * @return The remapped type descriptor
     */
    public String remapType(String typeDescriptor) {
        return remapMethodDescriptor(typeDescriptor);
    }

    /**
     * Remaps an internal class name (not a descriptor).
     *
     * @param className The internal class name (e.g., "com/old/Type")
     * @return The remapped class name, or the original if no mapping exists
     */
    public String remapClassName(String className) {
        if (className == null) {
            return null;
        }
        String mapped = classMapper.apply(className);
        return mapped != null ? mapped : className;
    }

    /**
     * Checks if a descriptor contains any class references that would be remapped.
     *
     * @param descriptor The descriptor to check
     * @return true if the descriptor would change after remapping
     */
    public boolean needsRemapping(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return false;
        }
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end == -1) break;
                String className = descriptor.substring(i + 1, end);
                String mapped = classMapper.apply(className);
                if (mapped != null && !mapped.equals(className)) {
                    return true;
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return false;
    }
}
