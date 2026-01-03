package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.ConstantValueAttribute;
import com.tonic.parser.attribute.DeprecatedAttribute;
import com.tonic.type.AccessFlags;

import java.util.ArrayList;
import java.util.List;

public class FieldBuilder {

    private final ClassBuilder parent;
    private final int access;
    private final String name;
    private final String descriptor;
    private Object constantValue;
    private boolean synthetic;
    private boolean deprecated;

    FieldBuilder(ClassBuilder parent, int access, String name, String descriptor) {
        this.parent = parent;
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
    }

    public FieldBuilder constantValue(Object value) {
        this.constantValue = value;
        return this;
    }

    public FieldBuilder synthetic() {
        this.synthetic = true;
        return this;
    }

    public FieldBuilder deprecated() {
        this.deprecated = true;
        return this;
    }

    public ClassBuilder end() {
        return parent;
    }

    int getAccess() {
        int flags = access;
        if (synthetic) {
            flags |= AccessFlags.ACC_SYNTHETIC;
        }
        return flags;
    }

    boolean isDeprecated() {
        return deprecated;
    }

    String getName() {
        return name;
    }

    String getDescriptor() {
        return descriptor;
    }

    void buildField(ClassFile classFile, ConstPool constPool) {
        List<Attribute> attributes = new ArrayList<>();
        FieldEntry field = classFile.createNewField(getAccess(), name, descriptor, attributes);

        if (constantValue != null) {
            int nameIndex = constPool.getIndexOf(constPool.findOrAddUtf8("ConstantValue"));
            int valueIndex = addConstantToPool(constPool, constantValue);
            ConstantValueAttribute cvAttr = new ConstantValueAttribute("ConstantValue", field, nameIndex, 2);
            cvAttr.setConstantValueIndex(valueIndex);
            field.getAttributes().add(cvAttr);
        }

        if (deprecated) {
            int nameIndex = constPool.getIndexOf(constPool.findOrAddUtf8("Deprecated"));
            DeprecatedAttribute deprecatedAttr = new DeprecatedAttribute("Deprecated", field, nameIndex, 0);
            field.getAttributes().add(deprecatedAttr);
        }
    }

    private int addConstantToPool(ConstPool pool, Object value) {
        if (value instanceof Integer) {
            return pool.getIndexOf(pool.findOrAddInteger((Integer) value));
        } else if (value instanceof Long) {
            return pool.getIndexOf(pool.findOrAddLong((Long) value));
        } else if (value instanceof Float) {
            return pool.getIndexOf(pool.findOrAddFloat((Float) value));
        } else if (value instanceof Double) {
            return pool.getIndexOf(pool.findOrAddDouble((Double) value));
        } else if (value instanceof String) {
            return pool.getIndexOf(pool.findOrAddString((String) value));
        }
        throw new IllegalArgumentException("Unsupported constant type: " + value.getClass());
    }
}
