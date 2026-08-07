package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;

import java.util.Objects;

/**
 * A wildcard type argument such as "?", "? extends Number" or "? super Integer";
 * erases to java/lang/Object.
 */
public final class WildcardSourceType implements SourceType
{

    /**
     * Which bound form a wildcard carries.
     */
    public enum BoundKind
    {
        /**
         * A bare {@code ?}, the only form that carries no bound type.
         */
        UNBOUNDED,
        /**
         * An upper bound, printed {@code ? extends T}.
         */
        EXTENDS,
        /**
         * A lower bound, printed {@code ? super T}.
         */
        SUPER
    }

    private final BoundKind boundKind;
    private final SourceType bound;

    /**
     * Creates a wildcard type.
     * @param boundKind which form of wildcard this is
     * @param bound the bound, required unless the kind is UNBOUNDED
     * @throws NullPointerException if the bound kind is null
     * @throws IllegalArgumentException if a bounded kind is given no bound
     */
    public WildcardSourceType(BoundKind boundKind, SourceType bound)
    {
        this.boundKind = Objects.requireNonNull(boundKind, "boundKind cannot be null");
        if (boundKind != BoundKind.UNBOUNDED && bound == null)
        {
            throw new IllegalArgumentException("bound is required for " + boundKind);
        }
        this.bound = bound;
    }

    /**
     * @return a bare "?" wildcard
     */
    public static WildcardSourceType unbounded()
    {
        return new WildcardSourceType(BoundKind.UNBOUNDED, null);
    }

    /**
     * Creates an upper-bounded wildcard.
     * @param bound the extends bound
     * @return the wildcard
     * @throws IllegalArgumentException if the bound is null
     */
    public static WildcardSourceType extendsType(SourceType bound)
    {
        return new WildcardSourceType(BoundKind.EXTENDS, bound);
    }

    /**
     * Creates a lower-bounded wildcard.
     * @param bound the super bound
     * @return the wildcard
     * @throws IllegalArgumentException if the bound is null
     */
    public static WildcardSourceType superType(SourceType bound)
    {
        return new WildcardSourceType(BoundKind.SUPER, bound);
    }

    /**
     * @return the bound kind
     */
    public BoundKind getBoundKind()
    {
        return boundKind;
    }

    /**
     * @return the bound
     */
    public SourceType getBound()
    {
        return bound;
    }

    /**
     * @return true for a bare "?" wildcard
     */
    public boolean isUnbounded()
    {
        return boundKind == BoundKind.UNBOUNDED;
    }

    /**
     * @return true for a "? extends" wildcard
     */
    public boolean hasUpperBound()
    {
        return boundKind == BoundKind.EXTENDS;
    }

    /**
     * @return true for a "? super" wildcard
     */
    public boolean hasLowerBound()
    {
        return boundKind == BoundKind.SUPER;
    }

    @Override
    public String toJavaSource()
    {
        switch (boundKind)
        {
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
    public IRType toIRType()
    {
        return new ReferenceType("java/lang/Object");
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitReferenceType(ReferenceSourceType.OBJECT);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof WildcardSourceType)) return false;
        WildcardSourceType that = (WildcardSourceType) o;
        return boundKind == that.boundKind && Objects.equals(bound, that.bound);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(boundKind, bound);
    }

    @Override
    public String toString()
    {
        return toJavaSource();
    }
}
