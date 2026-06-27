package com.tonic.analysis.execution.dispatch;

public final class MethodHandleInfo {

    public static final int REF_getField = 1;
    public static final int REF_getStatic = 2;
    public static final int REF_putField = 3;
    public static final int REF_putStatic = 4;
    public static final int REF_invokeVirtual = 5;
    public static final int REF_invokeStatic = 6;
    public static final int REF_invokeSpecial = 7;
    public static final int REF_newInvokeSpecial = 8;
    public static final int REF_invokeInterface = 9;

    private final int referenceKind;
    private final String owner;
    private final String name;
    private final String descriptor;

    public MethodHandleInfo(int referenceKind, String owner, String name, String descriptor) {
        this.referenceKind = referenceKind;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    public int getReferenceKind() {
        return referenceKind;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isFieldReference() {
        return referenceKind >= REF_getField && referenceKind <= REF_putStatic;
    }

    public boolean isMethodReference() {
        return referenceKind >= REF_invokeVirtual && referenceKind <= REF_invokeInterface;
    }

    public boolean isGetter() {
        return referenceKind == REF_getField || referenceKind == REF_getStatic;
    }

    public boolean isSetter() {
        return referenceKind == REF_putField || referenceKind == REF_putStatic;
    }

    public boolean isStatic() {
        return referenceKind == REF_getStatic || referenceKind == REF_putStatic ||
               referenceKind == REF_invokeStatic;
    }

    public boolean isConstructor() {
        return referenceKind == REF_newInvokeSpecial;
    }

    public String getReferenceKindName() {
        switch (referenceKind) {
            case REF_getField: return "REF_getField";
            case REF_getStatic: return "REF_getStatic";
            case REF_putField: return "REF_putField";
            case REF_putStatic: return "REF_putStatic";
            case REF_invokeVirtual: return "REF_invokeVirtual";
            case REF_invokeStatic: return "REF_invokeStatic";
            case REF_invokeSpecial: return "REF_invokeSpecial";
            case REF_newInvokeSpecial: return "REF_newInvokeSpecial";
            case REF_invokeInterface: return "REF_invokeInterface";
            default: return "REF_unknown(" + referenceKind + ")";
        }
    }

    @Override
    public String toString() {
        return "MethodHandleInfo{" +
            "kind=" + getReferenceKindName() +
            ", owner='" + owner + '\'' +
            ", name='" + name + '\'' +
            ", desc='" + descriptor + '\'' +
            '}';
    }
}
