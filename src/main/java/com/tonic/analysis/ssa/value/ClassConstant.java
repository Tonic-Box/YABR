package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import lombok.Getter;

/**
 * Represents a class constant (Class object reference).
 */
@Getter
public final class ClassConstant extends Constant {

    private final IRType classType;

    /**
     * Creates a class constant with the given IR type.
     *
     * @param classType the class type
     */
    public ClassConstant(IRType classType) {
        this.classType = classType;
    }

    /**
     * Creates a class constant with the given class name.
     *
     * @param className the internal class name
     */
    public ClassConstant(String className) {
        this.classType = new ReferenceType(className);
    }

    @Override
    public IRType getType() {
        return ReferenceType.CLASS;
    }

    @Override
    public IRType getValue() {
        return classType;
    }

    /**
     * Gets the class name in internal format.
     *
     * @return the internal class name
     */
    public String getClassName() {
        if (classType instanceof ReferenceType ref) {
            return ref.getInternalName();
        }
        return classType.getDescriptor();
    }

    @Override
    public String toString() {
        return classType.toString() + ".class";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassConstant that)) return false;
        return classType.equals(that.classType);
    }

    @Override
    public int hashCode() {
        return classType.hashCode();
    }
}
