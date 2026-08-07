package com.tonic.renamer.mapping;

import java.util.*;

/**
 * Container for all rename mappings with efficient lookup capabilities.
 */
public class MappingStore
{

    private final Map<String, ClassMapping> classMappings = new LinkedHashMap<>();
    private final Map<String, MethodMapping> methodMappings = new LinkedHashMap<>();
    private final Map<String, FieldMapping> fieldMappings = new LinkedHashMap<>();

    /**
     * Adds a class mapping.
     * @param mapping The class mapping to add
     * @return this for fluent chaining
     */
    public MappingStore addClassMapping(ClassMapping mapping)
    {
        classMappings.put(mapping.getOldName(), mapping);
        return this;
    }

    /**
     * Adds a method mapping.
     * @param mapping The method mapping to add
     * @return this for fluent chaining
     */
    public MappingStore addMethodMapping(MethodMapping mapping)
    {
        String key = makeMethodKey(mapping.getOwner(), mapping.getOldName(), mapping.getDescriptor());
        methodMappings.put(key, mapping);
        return this;
    }

    /**
     * Adds a field mapping.
     * @param mapping The field mapping to add
     * @return this for fluent chaining
     */
    public MappingStore addFieldMapping(FieldMapping mapping)
    {
        String key = makeFieldKey(mapping.getOwner(), mapping.getOldName(), mapping.getDescriptor());
        fieldMappings.put(key, mapping);
        return this;
    }

    /**
     * Resolves the replacement name for a class.
     *
     * @param oldName the original class name
     * @return the new name, or null if the class is unmapped
     */
    public String getClassMapping(String oldName)
    {
        ClassMapping mapping = classMappings.get(oldName);
        return mapping != null ? mapping.getNewName() : null;
    }

    /**
     * Looks up the mapping registered for one method.
     *
     * @param owner the declaring class name
     * @param name the original method name
     * @param descriptor the method descriptor
     * @return the mapping, or null if the method is unmapped
     */
    public MethodMapping getMethodMapping(String owner, String name, String descriptor)
    {
        return methodMappings.get(makeMethodKey(owner, name, descriptor));
    }

    /**
     * Looks up the mapping registered for one field.
     *
     * @param owner the declaring class name
     * @param name the original field name
     * @param descriptor the field descriptor
     * @return the mapping, or null if the field is unmapped
     */
    public FieldMapping getFieldMapping(String owner, String name, String descriptor)
    {
        return fieldMappings.get(makeFieldKey(owner, name, descriptor));
    }

    /**
     * Reports whether a class is mapped.
     *
     * @param oldName the original class name
     * @return true if a mapping is registered for it
     */
    public boolean hasClassMapping(String oldName)
    {
        return classMappings.containsKey(oldName);
    }

    /**
     * Reports whether a method is mapped.
     *
     * @param owner the declaring class name
     * @param name the original method name
     * @param descriptor the method descriptor
     * @return true if a mapping is registered for it
     */
    public boolean hasMethodMapping(String owner, String name, String descriptor)
    {
        return methodMappings.containsKey(makeMethodKey(owner, name, descriptor));
    }

    /**
     * Reports whether a field is mapped.
     *
     * @param owner the declaring class name
     * @param name the original field name
     * @param descriptor the field descriptor
     * @return true if a mapping is registered for it
     */
    public boolean hasFieldMapping(String owner, String name, String descriptor)
    {
        return fieldMappings.containsKey(makeFieldKey(owner, name, descriptor));
    }

    /**
     * @return an unmodifiable view of the class mappings in insertion order
     */
    public Collection<ClassMapping> getClassMappings()
    {
        return Collections.unmodifiableCollection(classMappings.values());
    }

    /**
     * @return an unmodifiable view of the method mappings in insertion order
     */
    public Collection<MethodMapping> getMethodMappings()
    {
        return Collections.unmodifiableCollection(methodMappings.values());
    }

    /**
     * @return an unmodifiable view of the field mappings in insertion order
     */
    public Collection<FieldMapping> getFieldMappings()
    {
        return Collections.unmodifiableCollection(fieldMappings.values());
    }

    /**
     * @return the class, method and field mapping counts added together
     */
    public int size()
    {
        return classMappings.size() + methodMappings.size() + fieldMappings.size();
    }

    /**
     * @return true if no class, method or field mapping is registered
     */
    public boolean isEmpty()
    {
        return classMappings.isEmpty() && methodMappings.isEmpty() && fieldMappings.isEmpty();
    }

    /**
     * Clears all mappings.
     */
    public void clear()
    {
        classMappings.clear();
        methodMappings.clear();
        fieldMappings.clear();
    }

    private static String makeMethodKey(String owner, String name, String descriptor)
    {
        return owner + "." + name + descriptor;
    }

    private static String makeFieldKey(String owner, String name, String descriptor)
    {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public String toString()
    {
        return "MappingStore{classes=" + classMappings.size() +
                ", methods=" + methodMappings.size() +
                ", fields=" + fieldMappings.size() + "}";
    }
}
