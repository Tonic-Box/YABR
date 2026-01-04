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
 * Represents a union type, e.g., catch (IOException | SQLException e).
 * Used in multi-catch clauses.
 */
public final class UnionSourceType implements SourceType {

    private final List<SourceType> alternatives;

    public UnionSourceType(List<SourceType> alternatives) {
        if (alternatives == null || alternatives.size() < 2) {
            throw new IllegalArgumentException("Union type requires at least 2 alternatives");
        }
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
    }

    public UnionSourceType(SourceType... alternatives) {
        this(List.of(alternatives));
    }

    public List<SourceType> getAlternatives() {
        return alternatives;
    }

    public int getAlternativeCount() {
        return alternatives.size();
    }

    public SourceType getAlternative(int index) {
        return alternatives.get(index);
    }

    public SourceType getFirstAlternative() {
        return alternatives.get(0);
    }

    @Override
    public String toJavaSource() {
        return alternatives.stream()
            .map(SourceType::toJavaSource)
            .collect(Collectors.joining(" | "));
    }

    @Override
    public IRType toIRType() {
        SourceType first = alternatives.get(0);
        if (first instanceof ReferenceSourceType) {
            return new ReferenceType(((ReferenceSourceType) first).getInternalName());
        }
        return new ReferenceType("java/lang/Throwable");
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        SourceType first = alternatives.get(0);
        if (first instanceof ReferenceSourceType) {
            return visitor.visitReferenceType((ReferenceSourceType) first);
        }
        return visitor.visitReferenceType(new ReferenceSourceType("java/lang/Throwable"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnionSourceType)) return false;
        UnionSourceType that = (UnionSourceType) o;
        return alternatives.equals(that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alternatives);
    }

    @Override
    public String toString() {
        return toJavaSource();
    }
}
