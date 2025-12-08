package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents a MethodHandle constant loaded via ldc.
 * Corresponds to CONSTANT_MethodHandle in the constant pool.
 *
 * Reference kinds (per JVM spec):
 * 1: REF_getField
 * 2: REF_getStatic
 * 3: REF_putField
 * 4: REF_putStatic
 * 5: REF_invokeVirtual
 * 6: REF_invokeStatic
 * 7: REF_invokeSpecial
 * 8: REF_newInvokeSpecial
 * 9: REF_invokeInterface
 */
@Getter
public final class MethodHandleConstant extends Constant {

    // Reference kind constants
    public static final int REF_getField = 1;
    public static final int REF_getStatic = 2;
    public static final int REF_putField = 3;
    public static final int REF_putStatic = 4;
    public static final int REF_invokeVirtual = 5;
    public static final int REF_invokeStatic = 6;
    public static final int REF_invokeSpecial = 7;
    public static final int REF_newInvokeSpecial = 8;
    public static final int REF_invokeInterface = 9;

    private final int referenceKind;  // 1-9 per JVM spec
    private final String owner;        // Class containing the member
    private final String name;         // Member name
    private final String descriptor;   // Member descriptor

    /**
     * Creates a MethodHandle constant.
     *
     * @param referenceKind the reference kind (1-9)
     * @param owner the class containing the referenced member
     * @param name the name of the referenced member
     * @param descriptor the descriptor of the referenced member
     */
    public MethodHandleConstant(int referenceKind, String owner, String name, String descriptor) {
        if (referenceKind < 1 || referenceKind > 9) {
            throw new IllegalArgumentException("Invalid reference kind: " + referenceKind);
        }
        this.referenceKind = referenceKind;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
    }

    @Override
    public IRType getType() {
        return new ReferenceType("java/lang/invoke/MethodHandle");
    }

    @Override
    public Object getValue() {
        return this; // Return self for complex constants
    }

    /**
     * Returns whether this method handle references a field.
     */
    public boolean isFieldReference() {
        return referenceKind >= REF_getField && referenceKind <= REF_putStatic;
    }

    /**
     * Returns whether this method handle references a method.
     */
    public boolean isMethodReference() {
        return referenceKind >= REF_invokeVirtual && referenceKind <= REF_invokeInterface;
    }

    /**
     * Returns a human-readable name for the reference kind.
     */
    public String getReferenceKindName() {
        return switch (referenceKind) {
            case REF_getField -> "REF_getField";
            case REF_getStatic -> "REF_getStatic";
            case REF_putField -> "REF_putField";
            case REF_putStatic -> "REF_putStatic";
            case REF_invokeVirtual -> "REF_invokeVirtual";
            case REF_invokeStatic -> "REF_invokeStatic";
            case REF_invokeSpecial -> "REF_invokeSpecial";
            case REF_newInvokeSpecial -> "REF_newInvokeSpecial";
            case REF_invokeInterface -> "REF_invokeInterface";
            default -> "UNKNOWN(" + referenceKind + ")";
        };
    }

    @Override
    public String toString() {
        return "MethodHandle[" + getReferenceKindName() + " " + owner + "." + name + descriptor + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodHandleConstant that)) return false;
        return referenceKind == that.referenceKind &&
                Objects.equals(owner, that.owner) &&
                Objects.equals(name, that.name) &&
                Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceKind, owner, name, descriptor);
    }
}
