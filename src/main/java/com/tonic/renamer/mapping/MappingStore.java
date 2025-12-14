package com.tonic.renamer.mapping;

import java.util.*;

/**
 * Container for all rename mappings with efficient lookup capabilities.
 */
public class MappingStore {

    private final Map<String, ClassMapping> classMappings = new LinkedHashMap<>();
    private final Map<String, MethodMapping> methodMappings = new LinkedHashMap<>();
    private final Map<String, FieldMapping> fieldMappings = new LinkedHashMap<>();

    /**
     * Adds a class mapping.
     *
     * @param mapping The class mapping to add
     * @return this for fluent chaining
     */
    public MappingStore addClassMapping(ClassMapping mapping) {
        classMappings.put(mapping.getOldName(), mapping);
        return this;
    }

    /**
     * Adds a method mapping.
     *
     * @param mapping The method mapping to add
     * @return this for fluent chaining
     */
    public MappingStore addMethodMapping(MethodMapping mapping) {
        String key = makeMethodKey(mapping.getOwner(), mapping.getOldName(), mapping.getDescriptor());
        methodMappings.put(key, mapping);
        return this;
    }

    /**
     * Adds a field mapping.
     *
     * @param mapping The field mapping to add
     * @return this for fluent chaining
     */
    public MappingStore addFieldMapping(FieldMapping mapping) {
        String key = makeFieldKey(mapping.getOwner(), mapping.getOldName(), mapping.getDescriptor());
        fieldMappings.put(key, mapping);
        return this;
    }

    /**
     * Gets the new class name for an old class name, or null if no mapping exists.
     */
    public String getClassMapping(String oldName) {
        ClassMapping mapping = classMappings.get(oldName);
        return mapping != null ? mapping.getNewName() : null;
    }

    /**
     * Gets the method mapping for a method, or null if no mapping exists.
     */
    public MethodMapping getMethodMapping(String owner, String name, String descriptor) {
        return methodMappings.get(makeMethodKey(owner, name, descriptor));
    }

    /**
     * Gets the field mapping for a field, or null if no mapping exists.
     */
    public FieldMapping getFieldMapping(String owner, String name, String descriptor) {
        return fieldMappings.get(makeFieldKey(owner, name, descriptor));
    }

    /**
     * Checks if a class has a mapping.
     */
    public boolean hasClassMapping(String oldName) {
        return classMappings.containsKey(oldName);
    }

    /**
     * Checks if a method has a mapping.
     */
    public boolean hasMethodMapping(String owner, String name, String descriptor) {
        return methodMappings.containsKey(makeMethodKey(owner, name, descriptor));
    }

    /**
     * Checks if a field has a mapping.
     */
    public boolean hasFieldMapping(String owner, String name, String descriptor) {
        return fieldMappings.containsKey(makeFieldKey(owner, name, descriptor));
    }

    /**
     * Returns all class mappings.
     */
    public Collection<ClassMapping> getClassMappings() {
        return Collections.unmodifiableCollection(classMappings.values());
    }

    /**
     * Returns all method mappings.
     */
    public Collection<MethodMapping> getMethodMappings() {
        return Collections.unmodifiableCollection(methodMappings.values());
    }

    /**
     * Returns all field mappings.
     */
    public Collection<FieldMapping> getFieldMappings() {
        return Collections.unmodifiableCollection(fieldMappings.values());
    }

    /**
     * Returns the total number of mappings.
     */
    public int size() {
        return classMappings.size() + methodMappings.size() + fieldMappings.size();
    }

    /**
     * Checks if the store is empty.
     */
    public boolean isEmpty() {
        return classMappings.isEmpty() && methodMappings.isEmpty() && fieldMappings.isEmpty();
    }

    /**
     * Clears all mappings.
     */
    public void clear() {
        classMappings.clear();
        methodMappings.clear();
        fieldMappings.clear();
    }

    private static String makeMethodKey(String owner, String name, String descriptor) {
        return owner + "." + name + descriptor;
    }

    private static String makeFieldKey(String owner, String name, String descriptor) {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public String toString() {
        return "MappingStore{classes=" + classMappings.size() +
                ", methods=" + methodMappings.size() +
                ", fields=" + fieldMappings.size() + "}";
    }
}
