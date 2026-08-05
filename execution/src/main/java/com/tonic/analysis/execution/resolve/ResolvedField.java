package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.util.Modifiers;

/**
 * A resolved field reference paired with the class that actually declares it.
 */
public class ResolvedField
{

    private final FieldEntry field;
    private final ClassFile declaringClass;

    /**
     * Creates a resolved field.
     * @param field the matched field entry
     * @param declaringClass the class that declares it
     */
    public ResolvedField(FieldEntry field, ClassFile declaringClass)
    {
        this.field = field;
        this.declaringClass = declaringClass;
    }

    /**
     * @return the field
     */
    public FieldEntry getField()
    {
        return field;
    }

    /**
     * @return the declaring class
     */
    public ClassFile getDeclaringClass()
    {
        return declaringClass;
    }

    /**
     * @return true if the field has the static modifier
     */
    public boolean isStatic()
    {
        return (field.getAccess() & Modifiers.STATIC) != 0;
    }

    @Override
    public String toString()
    {
        return "ResolvedField{" +
                "field=" + field.getOwnerName() + "." + field.getName() + ":" + field.getDesc() +
                '}';
    }
}
