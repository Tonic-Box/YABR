package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.ssa.type.*;

/**
 * Sealed interface representing source-level types.
 * Provides bidirectional conversion with the IR type system.
 */
public interface SourceType extends ASTNode {

    /**
     * Converts this source type to Java source code representation.
     *
     * @return the Java source representation (e.g., "int", "String", "int[]")
     */
    String toJavaSource();

    /**
     * Converts this source type to the equivalent IR type.
     *
     * @return the corresponding IRType
     */
    IRType toIRType();

    /**
     * Creates a SourceType from an IR type.
     *
     * @param irType the IR type to convert
     * @return the corresponding SourceType
     */
    static SourceType fromIRType(IRType irType) {
        if (irType == null) {
            return VoidSourceType.INSTANCE;
        }

        if (irType instanceof PrimitiveType) {
            PrimitiveType p = (PrimitiveType) irType;
            return PrimitiveSourceType.fromPrimitive(p);
        } else if (irType instanceof ReferenceType) {
            ReferenceType r = (ReferenceType) irType;
            return new ReferenceSourceType(r.getInternalName());
        } else if (irType instanceof ArrayType) {
            ArrayType a = (ArrayType) irType;
            return new ArraySourceType(fromIRType(a.getElementType()), a.getDimensions());
        } else if (irType instanceof VoidType) {
            return VoidSourceType.INSTANCE;
        }
        throw new IllegalArgumentException("Unknown IR type: " + irType.getClass());
    }

    /**
     * Checks if this type is a primitive type.
     */
    default boolean isPrimitive() {
        return this instanceof PrimitiveSourceType;
    }

    /**
     * Checks if this type is a reference type (class or interface).
     */
    default boolean isReference() {
        return this instanceof ReferenceSourceType;
    }

    /**
     * Checks if this type is an array type.
     */
    default boolean isArray() {
        return this instanceof ArraySourceType;
    }

    /**
     * Checks if this type is void.
     */
    default boolean isVoid() {
        return this instanceof VoidSourceType;
    }

    @Override
    default ASTNode getParent() {
        return null;
    }

    @Override
    default void setParent(ASTNode parent) {
    }

    @Override
    default SourceLocation getLocation() {
        return SourceLocation.UNKNOWN;
    }
}
