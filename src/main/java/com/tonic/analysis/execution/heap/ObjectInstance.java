package com.tonic.analysis.execution.heap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ObjectInstance {

    private final int id;
    private final String className;
    private final Map<FieldKey, Object> fields;
    private Object classResolver;

    public ObjectInstance(int id, String className) {
        this.id = id;
        this.className = className;
        this.fields = new HashMap<>();
    }

    public int getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public Object getField(String owner, String name, String descriptor) {
        FieldKey key = new FieldKey(owner, name, descriptor);
        return fields.get(key);
    }

    public void setField(String owner, String name, String descriptor, Object value) {
        FieldKey key = new FieldKey(owner, name, descriptor);
        fields.put(key, value);
    }

    public void setClassResolver(Object classResolver) {
        this.classResolver = classResolver;
    }

    public boolean isInstanceOf(String className) {
        if (this.className.equals(className)) {
            return true;
        }

        if (className.equals("java/lang/Object")) {
            return true;
        }

        return false;
    }

    public int getIdentityHashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return className + "@" + Integer.toHexString(id);
    }

    private static class FieldKey {
        private final String ownerClass;
        private final String fieldName;
        private final String descriptor;

        public FieldKey(String ownerClass, String fieldName, String descriptor) {
            this.ownerClass = ownerClass;
            this.fieldName = fieldName;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FieldKey)) return false;
            FieldKey other = (FieldKey) obj;
            return Objects.equals(ownerClass, other.ownerClass) &&
                   Objects.equals(fieldName, other.fieldName) &&
                   Objects.equals(descriptor, other.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerClass, fieldName, descriptor);
        }
    }
}
