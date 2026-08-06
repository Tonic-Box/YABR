package com.tonic.analysis.simulation.heap;

import java.util.Objects;

/**
 * Identifies a specific field for field-sensitive tracking.
 */
public final class FieldKey
{

    private final String owner;
    private final String name;
    private final String descriptor;

    private FieldKey(String owner, String name, String descriptor)
    {
        this.owner = Objects.requireNonNull(owner);
        this.name = Objects.requireNonNull(name);
        this.descriptor = Objects.requireNonNull(descriptor);
    }

    /**
     * Creates a key for one field.
     * @param owner the internal name of the declaring class
     * @param name the field name
     * @param descriptor the field descriptor
     * @return the key
     * @throws NullPointerException if any argument is null
     */
    public static FieldKey of(String owner, String name, String descriptor)
    {
        return new FieldKey(owner, name, descriptor);
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
     * @return true if the descriptor names an object or array type
     */
    public boolean isReferenceType()
    {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    /**
     * @return true if the descriptor names a primitive type
     */
    public boolean isPrimitiveType()
    {
        return !isReferenceType();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof FieldKey)) return false;
        FieldKey that = (FieldKey) o;
        return owner.equals(that.owner) &&
               name.equals(that.name) &&
               descriptor.equals(that.descriptor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public String toString()
    {
        return owner + "." + name + ":" + descriptor;
    }
}
