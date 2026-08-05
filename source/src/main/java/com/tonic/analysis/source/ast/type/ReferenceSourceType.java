package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.util.ClassNameUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a reference type (class or interface) in the source AST.
 * Supports generic type arguments for representing parameterized types.
 */
public final class ReferenceSourceType implements SourceType
{

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

    public static final ReferenceSourceType OBJECT = new ReferenceSourceType("java/lang/Object");
    public static final ReferenceSourceType STRING = new ReferenceSourceType("java/lang/String");
    public static final ReferenceSourceType CLASS = new ReferenceSourceType("java/lang/Class");

    /**
     * Creates a raw reference type printed by its simple name.
     *
     * @param internalName the class name in internal format
     * @throws NullPointerException if internalName is null
     */
    public ReferenceSourceType(String internalName)
    {
        this(internalName, Collections.emptyList(), true);
    }

    /**
     * Creates a parameterized reference type printed by its simple name.
     *
     * @param internalName the class name in internal format
     * @param typeArguments the generic arguments, or null for none
     * @throws NullPointerException if internalName is null
     */
    public ReferenceSourceType(String internalName, List<SourceType> typeArguments)
    {
        this(internalName, typeArguments, true);
    }

    /**
     * Creates a reference type, choosing how the name is printed.
     *
     * @param internalName the class name in internal format
     * @param typeArguments the generic arguments, or null for none
     * @param useSimpleName true to print the simple name, false for the qualified one
     * @throws NullPointerException if internalName is null
     */
    public ReferenceSourceType(String internalName, List<SourceType> typeArguments, boolean useSimpleName)
    {
        this.internalName = Objects.requireNonNull(internalName);
        this.typeArguments = typeArguments != null ? List.copyOf(typeArguments) : Collections.emptyList();
        this.useSimpleName = useSimpleName;
    }

    /**
     * @return the internal name
     */
    public String getInternalName()
    {
        return internalName;
    }

    /**
     * @return the type arguments
     */
    public List<SourceType> getTypeArguments()
    {
        return typeArguments;
    }

    /**
     * @return whether use simple name
     */
    public boolean isUseSimpleName()
    {
        return useSimpleName;
    }

    /**
     * Gets the fully qualified name in Java format (e.g., "java.lang.String", or "Outer.Inner" for a
     * nested class). A {@code $} separating a named nested class is rendered as {@code .}; an
     * anonymous/local marker ({@code $1}) is left intact since it cannot be named in source anyway.
     * @return the dotted source-form name
     */
    public String getFullyQualifiedName()
    {
        String source = ClassNameUtil.toSourceName(internalName);
        StringBuilder sb = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++)
        {
            char c = source.charAt(i);
            if (c == '$' && i + 1 < source.length()
                    && (Character.isJavaIdentifierStart(source.charAt(i + 1)) && source.charAt(i + 1) != '$')
                    && !Character.isDigit(source.charAt(i + 1)))
            {
                sb.append('.');
            }
            else
            {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Gets the simple class name (e.g., "String").
     * For inner classes, preserves the outer$inner format (e.g., "SessionManager$Session").
     * @return the name with the package stripped
     */
    public String getSimpleName()
    {
        return ClassNameUtil.getSimpleNameWithInnerClasses(internalName);
    }

    /**
     * Gets the package name (e.g., "java.lang").
     * @return the dotted package name, empty for the default package
     */
    public String getPackageName()
    {
        return ClassNameUtil.getPackageNameAsSource(internalName);
    }

    /**
     * Checks if this type has generic type arguments.
     * @return true when at least one type argument is present
     */
    public boolean hasTypeArguments()
    {
        return !typeArguments.isEmpty();
    }

    /**
     * Creates a copy of this type carrying different type arguments.
     * @param typeArgs the type arguments for the copy
     * @return a new type with the same internal name and simple-name preference
     */
    public ReferenceSourceType withTypeArguments(List<SourceType> typeArgs)
    {
        return new ReferenceSourceType(internalName, typeArgs, useSimpleName);
    }

    @Override
    public String toJavaSource()
    {
        StringBuilder sb = new StringBuilder();
        boolean useFullyQualified = !useSimpleName || internalName.contains("$");
        sb.append(useFullyQualified ? getFullyQualifiedName() : getSimpleName());

        if (!typeArguments.isEmpty())
        {
            boolean skipTypeArgs = false;
            if ("java/lang/Class".equals(internalName) && typeArguments.size() == 1)
            {
                SourceType arg = typeArguments.get(0);
                if (arg instanceof ReferenceSourceType)
                {
                    ReferenceSourceType refArg = (ReferenceSourceType) arg;
                    if ("java/lang/Object".equals(refArg.getInternalName()) && !refArg.hasTypeArguments())
                    {
                        skipTypeArgs = true;
                    }
                }
            }

            if (!skipTypeArgs)
            {
                sb.append("<");
                for (int i = 0; i < typeArguments.size(); i++)
                {
                    if (i > 0) sb.append(", ");
                    sb.append(typeArguments.get(i).toJavaSource());
                }
                sb.append(">");
            }
        }

        return sb.toString();
    }

    @Override
    public IRType toIRType()
    {
        return new ReferenceType(internalName);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitReferenceType(this);
    }

    @Override
    public String toString()
    {
        return toJavaSource();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (!(obj instanceof ReferenceSourceType)) return false;
        ReferenceSourceType other = (ReferenceSourceType) obj;
        return internalName.equals(other.internalName) &&
               typeArguments.equals(other.typeArguments);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(internalName, typeArguments);
    }
}
