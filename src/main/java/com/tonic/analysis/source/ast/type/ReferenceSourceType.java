package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a reference type (class or interface) in the source AST.
 * Supports generic type arguments for representing parameterized types.
 */
@Getter
public final class ReferenceSourceType implements SourceType {

    /**
     * The fully qualified class name in internal format (e.g., "java/lang/String").
     */
    private final String internalName;

    /**
     * Generic type arguments, if any.
     */
    private final List<SourceType> typeArguments;

    /**
     * Whether to use simple name in source output.
     */
    private final boolean useSimpleName;

    // Common reference types
    public static final ReferenceSourceType OBJECT = new ReferenceSourceType("java/lang/Object");
    public static final ReferenceSourceType STRING = new ReferenceSourceType("java/lang/String");
    public static final ReferenceSourceType CLASS = new ReferenceSourceType("java/lang/Class");

    public ReferenceSourceType(String internalName) {
        this(internalName, Collections.emptyList(), true);
    }

    public ReferenceSourceType(String internalName, List<SourceType> typeArguments) {
        this(internalName, typeArguments, true);
    }

    public ReferenceSourceType(String internalName, List<SourceType> typeArguments, boolean useSimpleName) {
        this.internalName = Objects.requireNonNull(internalName);
        this.typeArguments = typeArguments != null ? List.copyOf(typeArguments) : Collections.emptyList();
        this.useSimpleName = useSimpleName;
    }

    /**
     * Gets the fully qualified name in Java format (e.g., "java.lang.String").
     */
    public String getFullyQualifiedName() {
        return internalName.replace('/', '.');
    }

    /**
     * Gets the simple class name (e.g., "String").
     */
    public String getSimpleName() {
        int lastSlash = internalName.lastIndexOf('/');
        int lastDollar = internalName.lastIndexOf('$');
        int lastSeparator = Math.max(lastSlash, lastDollar);
        return lastSeparator >= 0 ? internalName.substring(lastSeparator + 1) : internalName;
    }

    /**
     * Gets the package name (e.g., "java.lang").
     */
    public String getPackageName() {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(0, lastSlash).replace('/', '.') : "";
    }

    /**
     * Checks if this type has generic type arguments.
     */
    public boolean hasTypeArguments() {
        return !typeArguments.isEmpty();
    }

    /**
     * Creates a new ReferenceSourceType with the given type arguments.
     */
    public ReferenceSourceType withTypeArguments(List<SourceType> typeArgs) {
        return new ReferenceSourceType(internalName, typeArgs, useSimpleName);
    }

    @Override
    public String toJavaSource() {
        StringBuilder sb = new StringBuilder();
        sb.append(useSimpleName ? getSimpleName() : getFullyQualifiedName());

        if (!typeArguments.isEmpty()) {
            sb.append("<");
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(typeArguments.get(i).toJavaSource());
            }
            sb.append(">");
        }

        return sb.toString();
    }

    @Override
    public IRType toIRType() {
        return new ReferenceType(internalName);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitReferenceType(this);
    }

    @Override
    public String toString() {
        return toJavaSource();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ReferenceSourceType other)) return false;
        return internalName.equals(other.internalName) &&
               typeArguments.equals(other.typeArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(internalName, typeArguments);
    }
}
