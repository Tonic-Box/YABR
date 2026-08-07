package com.tonic.analysis.execution.dispatch;

/**
 * A CONSTANT_MethodHandle entry: reference kind plus the referenced member's owner, name, and descriptor.
 */
public final class MethodHandleInfo
{

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

    /**
     * Creates a method handle descriptor.
     * @param referenceKind one of the REF_* kind constants
     * @param owner internal name of the referenced member's declaring class
     * @param name the referenced member's name
     * @param descriptor the referenced member's descriptor
     */
    public MethodHandleInfo(int referenceKind, String owner, String name, String descriptor)
    {
        this.referenceKind = referenceKind;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    /**
     * @return the reference kind
     */
    public int getReferenceKind()
    {
        return referenceKind;
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
     * Checks whether the handle references a field.
     * @return true for the get/put field and static kinds
     */
    public boolean isFieldReference()
    {
        return referenceKind >= REF_getField && referenceKind <= REF_putStatic;
    }

    /**
     * Checks whether the handle references a method or constructor.
     * @return true for the invoke kinds
     */
    public boolean isMethodReference()
    {
        return referenceKind >= REF_invokeVirtual && referenceKind <= REF_invokeInterface;
    }

    /**
     * Checks whether the handle reads a field.
     * @return true for REF_getField or REF_getStatic
     */
    public boolean isGetter()
    {
        return referenceKind == REF_getField || referenceKind == REF_getStatic;
    }

    /**
     * Checks whether the handle writes a field.
     * @return true for REF_putField or REF_putStatic
     */
    public boolean isSetter()
    {
        return referenceKind == REF_putField || referenceKind == REF_putStatic;
    }

    /**
     * Checks whether the referenced member is static.
     * @return true for the static field kinds or REF_invokeStatic
     */
    public boolean isStatic()
    {
        return referenceKind == REF_getStatic || referenceKind == REF_putStatic ||
               referenceKind == REF_invokeStatic;
    }

    /**
     * Checks whether the handle invokes a constructor.
     * @return true for REF_newInvokeSpecial
     */
    public boolean isConstructor()
    {
        return referenceKind == REF_newInvokeSpecial;
    }

    /**
     * Names the reference kind for diagnostics.
     * @return the REF_* constant name, or a placeholder for unknown kinds
     */
    public String getReferenceKindName()
    {
        switch (referenceKind)
        {
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
    public String toString()
    {
        return "MethodHandleInfo{" +
            "kind=" + getReferenceKindName() +
            ", owner='" + owner + '\'' +
            ", name='" + name + '\'' +
            ", desc='" + descriptor + '\'' +
            '}';
    }
}
