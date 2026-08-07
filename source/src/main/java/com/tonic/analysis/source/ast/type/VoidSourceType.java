package com.tonic.analysis.source.ast.type;

import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.VoidType;

/**
 * The void type in the source AST, held as a singleton.
 */
public final class VoidSourceType implements SourceType
{

    /**
     * The singleton instance.
     */
    public static final VoidSourceType INSTANCE = new VoidSourceType();

    private VoidSourceType()
    {
    }

    @Override
    public String toJavaSource()
    {
        return "void";
    }

    @Override
    public IRType toIRType()
    {
        return VoidType.INSTANCE;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitVoidType(this);
    }

    @Override
    public String toString()
    {
        return "void";
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof VoidSourceType;
    }

    @Override
    public int hashCode()
    {
        return VoidSourceType.class.hashCode();
    }
}
