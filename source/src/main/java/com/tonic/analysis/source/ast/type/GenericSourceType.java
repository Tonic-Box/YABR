package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A parameterized reference type such as List&lt;String&gt;; the type arguments are
 * source-only and erase away in toIRType.
 */
public final class GenericSourceType implements SourceType
{

    private final ReferenceSourceType rawType;
    private final List<SourceType> typeArguments;

    /**
     * Creates a generic type over a copy of the type argument list.
     * @param rawType the erased reference type
     * @param typeArguments the type arguments in order
     * @throws NullPointerException if either argument is null
     */
    public GenericSourceType(ReferenceSourceType rawType, List<SourceType> typeArguments)
    {
        this.rawType = Objects.requireNonNull(rawType, "rawType cannot be null");
        this.typeArguments = List.copyOf(Objects.requireNonNull(typeArguments, "typeArguments cannot be null"));
    }

    /**
     * Creates a generic type from a class name and a type argument list.
     * @param className the raw class name
     * @param typeArguments the type arguments in order
     * @throws NullPointerException if the type argument list is null
     */
    public GenericSourceType(String className, List<SourceType> typeArguments)
    {
        this(new ReferenceSourceType(className), typeArguments);
    }

    /**
     * Creates a generic type from a class name and type arguments.
     * @param className the raw class name
     * @param typeArguments the type arguments in order
     */
    public GenericSourceType(String className, SourceType... typeArguments)
    {
        this(new ReferenceSourceType(className), List.of(typeArguments));
    }

    /**
     * @return the raw type
     */
    public ReferenceSourceType getRawType()
    {
        return rawType;
    }

    /**
     * @return the type arguments
     */
    public List<SourceType> getTypeArguments()
    {
        return typeArguments;
    }

    /**
     * @return the number of type arguments
     */
    public int getTypeArgumentCount()
    {
        return typeArguments.size();
    }

    /**
     * @param index position in the type argument list
     * @return the type argument at that position
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public SourceType getTypeArgument(int index)
    {
        return typeArguments.get(index);
    }

    @Override
    public String toJavaSource()
    {
        if (typeArguments.isEmpty())
        {
            return rawType.toJavaSource();
        }
        String args = typeArguments.stream()
            .map(SourceType::toJavaSource)
            .collect(Collectors.joining(", "));
        return rawType.toJavaSource() + "<" + args + ">";
    }

    @Override
    public IRType toIRType()
    {
        return new ReferenceType(rawType.getInternalName());
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitReferenceType(rawType);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof GenericSourceType)) return false;
        GenericSourceType that = (GenericSourceType) o;
        return rawType.equals(that.rawType) && typeArguments.equals(that.typeArguments);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(rawType, typeArguments);
    }

    @Override
    public String toString()
    {
        return toJavaSource();
    }
}
