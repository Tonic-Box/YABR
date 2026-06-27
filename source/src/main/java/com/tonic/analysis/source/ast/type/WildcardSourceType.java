package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.Objects;

/**
 * Represents a wildcard type, e.g., ?, ? extends Number, ? super Integer.
 */
public final class WildcardSourceType implements SourceType {

    public enum BoundKind {
        UNBOUNDED,
        EXTENDS,
        SUPER
    }

    private final BoundKind boundKind;
    private final SourceType bound;

    public WildcardSourceType(BoundKind boundKind, SourceType bound) {
        this.boundKind = Objects.requireNonNull(boundKind, "boundKind cannot be null");
        if (boundKind != BoundKind.UNBOUNDED && bound == null) {
            throw new IllegalArgumentException("bound is required for " + boundKind);
        }
        this.bound = bound;
    }

    public static WildcardSourceType unbounded() {
        return new WildcardSourceType(BoundKind.UNBOUNDED, null);
    }

    public static WildcardSourceType extendsType(SourceType bound) {
        return new WildcardSourceType(BoundKind.EXTENDS, bound);
    }

    public static WildcardSourceType superType(SourceType bound) {
        return new WildcardSourceType(BoundKind.SUPER, bound);
    }

    public BoundKind getBoundKind() {
        return boundKind;
    }

    public SourceType getBound() {
        return bound;
    }

    public boolean isUnbounded() {
        return boundKind == BoundKind.UNBOUNDED;
    }

    public boolean hasUpperBound() {
        return boundKind == BoundKind.EXTENDS;
    }

    public boolean hasLowerBound() {
        return boundKind == BoundKind.SUPER;
    }

    @Override
    public String toJavaSource() {
        switch (boundKind) {
            case UNBOUNDED:
                return "?";
            case EXTENDS:
                return "? extends " + bound.toJavaSource();
            case SUPER:
                return "? super " + bound.toJavaSource();
            default:
                throw new IllegalStateException("Unknown bound kind: " + boundKind);
        }
    }

    @Override
    public IRType toIRType() {
        return new ReferenceType("java/lang/Object");
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitReferenceType(ReferenceSourceType.OBJECT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WildcardSourceType)) return false;
        WildcardSourceType that = (WildcardSourceType) o;
        return boundKind == that.boundKind && Objects.equals(bound, that.bound);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boundKind, bound);
    }

    @Override
    public String toString() {
        return toJavaSource();
    }
}
