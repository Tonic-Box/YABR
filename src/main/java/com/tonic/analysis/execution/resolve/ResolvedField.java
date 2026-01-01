package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.utill.Modifiers;

public class ResolvedField {

    private final FieldEntry field;
    private final ClassFile declaringClass;

    public ResolvedField(FieldEntry field, ClassFile declaringClass) {
        this.field = field;
        this.declaringClass = declaringClass;
    }

    public FieldEntry getField() {
        return field;
    }

    public ClassFile getDeclaringClass() {
        return declaringClass;
    }

    public boolean isStatic() {
        return (field.getAccess() & Modifiers.STATIC) != 0;
    }

    @Override
    public String toString() {
        return "ResolvedField{" +
                "field=" + field.getOwnerName() + "." + field.getName() + ":" + field.getDesc() +
                '}';
    }
}
