package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;

/**
 * A single import declaration, either plain or static and either single-type or
 * on-demand.
 */
public final class ImportDecl implements ASTNode
{

    private String name;
    private final boolean isStatic;
    private final boolean isWildcard;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates an import.
     * @param name the imported name
     * @param isStatic true for a static import
     * @param isWildcard true for an on-demand import
     * @param location the source location, null becomes UNKNOWN
     */
    public ImportDecl(String name, boolean isStatic, boolean isWildcard, SourceLocation location)
    {
        this.name = name;
        this.isStatic = isStatic;
        this.isWildcard = isWildcard;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    /**
     * Creates an import at an unknown location.
     * @param name the imported name
     * @param isStatic true for a static import
     * @param isWildcard true for an on-demand import
     */
    public ImportDecl(String name, boolean isStatic, boolean isWildcard)
    {
        this(name, isStatic, isWildcard, SourceLocation.UNKNOWN);
    }

    /**
     * Creates a plain single-type import at an unknown location.
     * @param name the imported name
     */
    public ImportDecl(String name)
    {
        this(name, false, false);
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @param name the imported name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return whether static
     */
    public boolean isStatic()
    {
        return isStatic;
    }

    /**
     * @return whether wildcard
     */
    public boolean isWildcard()
    {
        return isWildcard;
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return the parent
     */
    public ASTNode getParent()
    {
        return parent;
    }

    /**
     * @param parent the enclosing node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    /**
     * Creates a single-type import.
     * @param name the fully qualified type name
     * @return the import
     */
    public static ImportDecl regular(String name)
    {
        return new ImportDecl(name, false, false);
    }

    /**
     * Creates a single static member import.
     * @param name the fully qualified member name
     * @return the import
     */
    public static ImportDecl staticImport(String name)
    {
        return new ImportDecl(name, true, false);
    }

    /**
     * Creates an on-demand import.
     * @param packageName the package to import from
     * @return the import
     */
    public static ImportDecl wildcard(String packageName)
    {
        return new ImportDecl(packageName, false, true);
    }

    /**
     * Creates a static on-demand import.
     * @param className the class whose static members are imported
     * @return the import
     */
    public static ImportDecl staticWildcard(String className)
    {
        return new ImportDecl(className, true, true);
    }

    /**
     * @return everything before the last dot, or "" if the name has no dot
     */
    public String getPackageName()
    {
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) return "";
        return name.substring(0, lastDot);
    }

    /**
     * @return "*" for a wildcard import, otherwise the segment after the last dot
     */
    public String getSimpleName()
    {
        if (isWildcard) return "*";
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) return name;
        return name.substring(lastDot + 1);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return null;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("import ");
        if (isStatic) sb.append("static ");
        sb.append(name);
        if (isWildcard) sb.append(".*");
        return sb.toString();
    }
}
