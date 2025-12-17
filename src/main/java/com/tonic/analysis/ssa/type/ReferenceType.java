package com.tonic.analysis.ssa.type;

import com.tonic.utill.ClassNameUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Represents a reference type (class or interface).
 */
@Getter
@EqualsAndHashCode
public final class ReferenceType implements IRType {

    public static final ReferenceType OBJECT = new ReferenceType("java/lang/Object");
    public static final ReferenceType STRING = new ReferenceType("java/lang/String");
    public static final ReferenceType CLASS = new ReferenceType("java/lang/Class");
    public static final ReferenceType THROWABLE = new ReferenceType("java/lang/Throwable");

    private final String internalName;

    /**
     * Creates a reference type with the given internal name.
     * @param internalName the internal class name
     */
    public ReferenceType(String internalName) {
        this.internalName = internalName.replace('.', '/');
    }

    @Override
    public String getDescriptor() {
        return "L" + internalName + ";";
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isVoid() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isTwoSlot() {
        return false;
    }

    /**
     * Gets the simple class name without package.
     * @return the simple name
     */
    public String getSimpleName() {
        return ClassNameUtil.getSimpleNameWithInnerClasses(internalName);
    }

    @Override
    public String toString() {
        return internalName;
    }
}
