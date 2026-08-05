package com.tonic.analysis.execution.dispatch;

/**
 * A resolved field reference: owner class, name, descriptor, and static flag.
 */
public class FieldInfo
{
    private final String ownerClass;
    private final String fieldName;
    private final String descriptor;
    private final boolean isStatic;

    /**
     * Creates a field reference.
     * @param ownerClass internal name of the declaring class
     * @param fieldName the field's name
     * @param descriptor the field's type descriptor
     * @param isStatic whether the field is static
     */
    public FieldInfo(String ownerClass, String fieldName, String descriptor, boolean isStatic)
    {
        this.ownerClass = ownerClass;
        this.fieldName = fieldName;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
    }

    /**
     * @return the owner class
     */
    public String getOwnerClass()
    {
        return ownerClass;
    }

    /**
     * @return the field name
     */
    public String getFieldName()
    {
        return fieldName;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return whether static
     */
    public boolean isStatic()
    {
        return isStatic;
    }

    @Override
    public String toString()
    {
        return ownerClass + "." + fieldName + ":" + descriptor + (isStatic ? " (static)" : "");
    }
}
