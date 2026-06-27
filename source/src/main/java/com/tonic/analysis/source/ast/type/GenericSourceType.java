package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a generic type with type arguments, e.g., List&lt;String&gt; or Map&lt;K, V&gt;.
 */
public final class GenericSourceType implements SourceType {

    private final ReferenceSourceType rawType;
    private final List<SourceType> typeArguments;

    public GenericSourceType(ReferenceSourceType rawType, List<SourceType> typeArguments) {
        this.rawType = Objects.requireNonNull(rawType, "rawType cannot be null");
        this.typeArguments = Collections.unmodifiableList(
            new ArrayList<>(Objects.requireNonNull(typeArguments, "typeArguments cannot be null"))
        );
    }

    public GenericSourceType(String className, List<SourceType> typeArguments) {
        this(new ReferenceSourceType(className), typeArguments);
    }

    public GenericSourceType(String className, SourceType... typeArguments) {
        this(new ReferenceSourceType(className), List.of(typeArguments));
    }

    public ReferenceSourceType getRawType() {
        return rawType;
    }

    public List<SourceType> getTypeArguments() {
        return typeArguments;
    }

    public int getTypeArgumentCount() {
        return typeArguments.size();
    }

    public SourceType getTypeArgument(int index) {
        return typeArguments.get(index);
    }

    @Override
    public String toJavaSource() {
        if (typeArguments.isEmpty()) {
            return rawType.toJavaSource();
        }
        String args = typeArguments.stream()
            .map(SourceType::toJavaSource)
            .collect(Collectors.joining(", "));
        return rawType.toJavaSource() + "<" + args + ">";
    }

    @Override
    public IRType toIRType() {
        return new ReferenceType(rawType.getInternalName());
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitReferenceType(rawType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenericSourceType)) return false;
        GenericSourceType that = (GenericSourceType) o;
        return rawType.equals(that.rawType) && typeArguments.equals(that.typeArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawType, typeArguments);
    }

    @Override
    public String toString() {
        return toJavaSource();
    }
}
