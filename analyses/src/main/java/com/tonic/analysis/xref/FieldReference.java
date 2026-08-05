package com.tonic.analysis.xref;

import java.util.Objects;

/**
 * Identifies a specific field by owner class, name, and descriptor.
 * Used as a key for field-based xref lookups.
 */
public class FieldReference
{

    private final String owner;
    private final String name;
    private final String descriptor;

    /**
     * Creates a field reference, substituting the empty string for any null component.
     * @param owner internal name of the declaring class, may be null
     * @param name the field name, may be null
     * @param descriptor the field type descriptor, may be null
     */
    public FieldReference(String owner, String name, String descriptor)
    {
        this.owner = owner != null ? owner : "";
        this.name = name != null ? name : "";
        this.descriptor = descriptor != null ? descriptor : "";
    }

    /**
     * @return the owner
     */
    public String getOwner()
    {
        return owner;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * Renders the reference with a dotted class name and no descriptor.
     * @return a string of the form "com.example.MyClass.fieldName"
     */
    public String getDisplayName()
    {
        String className = owner.replace('/', '.');
        return className + "." + name;
    }

    /**
     * Renders the reference with the internal owner name and the descriptor.
     * @return a string of the form "owner.name:descriptor"
     */
    public String getFullReference()
    {
        return owner + "." + name + ":" + descriptor;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldReference that = (FieldReference) o;
        return Objects.equals(owner, that.owner) &&
               Objects.equals(name, that.name) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public String toString()
    {
        return getDisplayName();
    }
}
