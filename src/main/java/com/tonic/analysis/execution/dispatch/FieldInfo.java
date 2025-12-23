package com.tonic.analysis.execution.dispatch;

public class FieldInfo {
    private final String ownerClass;
    private final String fieldName;
    private final String descriptor;
    private final boolean isStatic;

    public FieldInfo(String ownerClass, String fieldName, String descriptor, boolean isStatic) {
        this.ownerClass = ownerClass;
        this.fieldName = fieldName;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
    }

    public String getOwnerClass() {
        return ownerClass;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isStatic() {
        return isStatic;
    }

    @Override
    public String toString() {
        return ownerClass + "." + fieldName + ":" + descriptor + (isStatic ? " (static)" : "");
    }
}
