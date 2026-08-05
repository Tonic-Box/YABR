package com.tonic.analysis.execution.heap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A simulated heap object identified by id, holding fields keyed by owner, name, and descriptor.
 */
public class ObjectInstance
{

    private final int id;
    private final String className;
    private final Map<FieldKey, Object> fields;
    private Object classResolver;

    /**
     * Creates an object with no fields set.
     * @param id the heap object id
     * @param className internal name of the object's class
     */
    public ObjectInstance(int id, String className)
    {
        this.id = id;
        this.className = className;
        this.fields = new HashMap<>();
    }

    /**
     * @return the id
     */
    public int getId()
    {
        return id;
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * Reads a field value.
     * @param owner internal name of the declaring class
     * @param name the field's name
     * @param descriptor the field's type descriptor
     * @return the stored value, or null if the field was never set
     */
    public Object getField(String owner, String name, String descriptor)
    {
        FieldKey key = new FieldKey(owner, name, descriptor);
        return fields.get(key);
    }

    /**
     * Writes a field value.
     * @param owner internal name of the declaring class
     * @param name the field's name
     * @param descriptor the field's type descriptor
     * @param value the value to store
     */
    public void setField(String owner, String name, String descriptor, Object value)
    {
        FieldKey key = new FieldKey(owner, name, descriptor);
        fields.put(key, value);
    }

    /**
     * Attaches the resolver used for type hierarchy queries.
     * @param classResolver the resolver to attach
     */
    public void setClassResolver(Object classResolver)
    {
        this.classResolver = classResolver;
    }

    /**
     * Checks assignability by exact class match, treating java/lang/Object as a universal supertype.
     * @param className internal name of the candidate type
     * @return true if the class names match exactly or the candidate is java/lang/Object
     */
    public boolean isInstanceOf(String className)
    {
        if (this.className.equals(className))
        {
            return true;
        }

        return className.equals("java/lang/Object");
    }

    /**
     * @return the identity hash code
     */
    public int getIdentityHashCode()
    {
        return id;
    }

    @Override
    public int hashCode()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return className + "@" + Integer.toHexString(id);
    }

    private static class FieldKey
    {
        private final String ownerClass;
        private final String fieldName;
        private final String descriptor;

        public FieldKey(String ownerClass, String fieldName, String descriptor)
        {
            this.ownerClass = ownerClass;
            this.fieldName = fieldName;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) return true;
            if (!(obj instanceof FieldKey)) return false;
            FieldKey other = (FieldKey) obj;
            return Objects.equals(ownerClass, other.ownerClass) &&
                   Objects.equals(fieldName, other.fieldName) &&
                   Objects.equals(descriptor, other.descriptor);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(ownerClass, fieldName, descriptor);
        }
    }
}
