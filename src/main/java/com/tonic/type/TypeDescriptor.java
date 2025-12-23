package com.tonic.type;

import com.tonic.utill.DescriptorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TypeDescriptor {

    public static final int VOID = 0;
    public static final int BOOLEAN = 1;
    public static final int CHAR = 2;
    public static final int BYTE = 3;
    public static final int SHORT = 4;
    public static final int INT = 5;
    public static final int FLOAT = 6;
    public static final int LONG = 7;
    public static final int DOUBLE = 8;
    public static final int ARRAY = 9;
    public static final int OBJECT = 10;
    public static final int METHOD = 11;

    public static final TypeDescriptor VOID_TYPE = new TypeDescriptor("V", VOID);
    public static final TypeDescriptor BOOLEAN_TYPE = new TypeDescriptor("Z", BOOLEAN);
    public static final TypeDescriptor BYTE_TYPE = new TypeDescriptor("B", BYTE);
    public static final TypeDescriptor CHAR_TYPE = new TypeDescriptor("C", CHAR);
    public static final TypeDescriptor SHORT_TYPE = new TypeDescriptor("S", SHORT);
    public static final TypeDescriptor INT_TYPE = new TypeDescriptor("I", INT);
    public static final TypeDescriptor LONG_TYPE = new TypeDescriptor("J", LONG);
    public static final TypeDescriptor FLOAT_TYPE = new TypeDescriptor("F", FLOAT);
    public static final TypeDescriptor DOUBLE_TYPE = new TypeDescriptor("D", DOUBLE);

    private final String descriptor;
    private final int sort;

    private TypeDescriptor(String descriptor, int sort) {
        this.descriptor = descriptor;
        this.sort = sort;
    }

    public static TypeDescriptor forClass(String internalName) {
        return new TypeDescriptor("L" + internalName + ";", OBJECT);
    }

    public static TypeDescriptor forArray(TypeDescriptor element, int dimensions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            sb.append('[');
        }
        sb.append(element.descriptor);
        return new TypeDescriptor(sb.toString(), ARRAY);
    }

    public static TypeDescriptor forMethod(TypeDescriptor returnType, TypeDescriptor... params) {
        StringBuilder sb = new StringBuilder("(");
        for (TypeDescriptor param : params) {
            sb.append(param.descriptor);
        }
        sb.append(")");
        sb.append(returnType.descriptor);
        return new TypeDescriptor(sb.toString(), METHOD);
    }

    public static TypeDescriptor parse(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            throw new IllegalArgumentException("Descriptor cannot be null or empty");
        }

        char first = descriptor.charAt(0);
        switch (first) {
            case 'V': return VOID_TYPE;
            case 'Z': return BOOLEAN_TYPE;
            case 'B': return BYTE_TYPE;
            case 'C': return CHAR_TYPE;
            case 'S': return SHORT_TYPE;
            case 'I': return INT_TYPE;
            case 'J': return LONG_TYPE;
            case 'F': return FLOAT_TYPE;
            case 'D': return DOUBLE_TYPE;
            case '[': return new TypeDescriptor(descriptor, ARRAY);
            case 'L': return new TypeDescriptor(descriptor, OBJECT);
            case '(': return new TypeDescriptor(descriptor, METHOD);
            default:
                throw new IllegalArgumentException("Invalid descriptor: " + descriptor);
        }
    }

    public int getSort() {
        return sort;
    }

    public boolean isPrimitive() {
        return sort >= VOID && sort <= DOUBLE;
    }

    public boolean isArray() {
        return sort == ARRAY;
    }

    public boolean isObject() {
        return sort == OBJECT;
    }

    public boolean isMethod() {
        return sort == METHOD;
    }

    public String getInternalName() {
        if (sort == OBJECT) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        if (sort == ARRAY) {
            String elem = getArrayElementType(descriptor);
            if (elem != null && elem.startsWith("L")) {
                return elem.substring(1, elem.length() - 1);
            }
        }
        return null;
    }

    public String getClassName() {
        String internal = getInternalName();
        return internal != null ? internal.replace('/', '.') : null;
    }

    public int getDimensions() {
        if (sort != ARRAY) return 0;
        return DescriptorUtil.getArrayDimensions(descriptor);
    }

    public TypeDescriptor getElementType() {
        if (sort != ARRAY) return null;
        String elemDesc = DescriptorUtil.getArrayElementType(descriptor);
        return elemDesc != null ? parse(elemDesc) : null;
    }

    public TypeDescriptor getReturnType() {
        if (sort != METHOD) return null;
        String ret = DescriptorUtil.parseReturnDescriptor(descriptor);
        return ret != null ? parse(ret) : null;
    }

    public TypeDescriptor[] getArgumentTypes() {
        if (sort != METHOD) return new TypeDescriptor[0];
        List<String> params = DescriptorUtil.parseParameterDescriptors(descriptor);
        TypeDescriptor[] result = new TypeDescriptor[params.size()];
        for (int i = 0; i < params.size(); i++) {
            result[i] = parse(params.get(i));
        }
        return result;
    }

    public int getArgumentsSize() {
        if (sort != METHOD) return 0;
        return DescriptorUtil.countParameterSlots(descriptor);
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getSize() {
        if (sort == LONG || sort == DOUBLE) return 2;
        if (sort == VOID) return 0;
        return 1;
    }

    public int getLoadOpcode() {
        switch (sort) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return AccessFlags.ILOAD;
            case LONG:
                return AccessFlags.LLOAD;
            case FLOAT:
                return AccessFlags.FLOAD;
            case DOUBLE:
                return AccessFlags.DLOAD;
            case ARRAY:
            case OBJECT:
                return AccessFlags.ALOAD;
            default:
                throw new IllegalStateException("Cannot load type: " + sort);
        }
    }

    public int getStoreOpcode() {
        switch (sort) {
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return AccessFlags.ISTORE;
            case LONG:
                return AccessFlags.LSTORE;
            case FLOAT:
                return AccessFlags.FSTORE;
            case DOUBLE:
                return AccessFlags.DSTORE;
            case ARRAY:
            case OBJECT:
                return AccessFlags.ASTORE;
            default:
                throw new IllegalStateException("Cannot store type: " + sort);
        }
    }

    public int getReturnOpcode() {
        switch (sort) {
            case VOID:
                return AccessFlags.RETURN;
            case BOOLEAN:
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
                return AccessFlags.IRETURN;
            case LONG:
                return AccessFlags.LRETURN;
            case FLOAT:
                return AccessFlags.FRETURN;
            case DOUBLE:
                return AccessFlags.DRETURN;
            case ARRAY:
            case OBJECT:
                return AccessFlags.ARETURN;
            default:
                throw new IllegalStateException("Cannot return type: " + sort);
        }
    }

    private static String getArrayElementType(String desc) {
        int dims = 0;
        while (dims < desc.length() && desc.charAt(dims) == '[') {
            dims++;
        }
        return desc.substring(dims);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeDescriptor that = (TypeDescriptor) o;
        return Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor);
    }

    @Override
    public String toString() {
        return descriptor;
    }
}
