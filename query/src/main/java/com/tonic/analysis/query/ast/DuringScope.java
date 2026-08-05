package com.tonic.analysis.query.ast;

import java.util.Objects;

/**
 * Scope limited to events during execution of specific methods.
 * Example: DURING clinit OF classes matching /Config/
 *          DURING method "Auth.login"
 */
public final class DuringScope implements Scope
{

    private final String methodPattern;
    private final boolean isClinit;
    private final ClassScope classFilter;

    /**
     * Creates a during scope.
     * @param methodPattern the method name pattern events must occur in
     * @param isClinit whether the scope targets static initializers
     * @param classFilter optional class filter, may be null
     */
    public DuringScope(String methodPattern, boolean isClinit, ClassScope classFilter)
    {
        this.methodPattern = methodPattern;
        this.isClinit = isClinit;
        this.classFilter = classFilter;
    }

    /**
     * @return the method name pattern
     */
    public String methodPattern()
    {
        return methodPattern;
    }

    /**
     * @return whether the scope targets static initializers
     */
    public boolean isClinit()
    {
        return isClinit;
    }

    /**
     * @return the class filter, or null when unrestricted
     */
    public ClassScope classFilter()
    {
        return classFilter;
    }

    /**
     * Creates a scope covering static initializers of the filtered classes.
     * @param classFilter the classes whose clinit methods are in scope
     * @return the clinit scope
     */
    public static DuringScope clinitOf(ClassScope classFilter)
    {
        return new DuringScope("<clinit>", true, classFilter);
    }

    /**
     * Creates a scope covering methods matching a pattern, with no class filter.
     * @param methodPattern the method name pattern
     * @return the method scope
     */
    public static DuringScope method(String methodPattern)
    {
        return new DuringScope(methodPattern, false, null);
    }

    @Override
    public <T> T accept(ScopeVisitor<T> visitor)
    {
        return visitor.visitDuring(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof DuringScope)) return false;
        DuringScope that = (DuringScope) o;
        return isClinit == that.isClinit &&
               Objects.equals(methodPattern, that.methodPattern) &&
               Objects.equals(classFilter, that.classFilter);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(methodPattern, isClinit, classFilter);
    }

    @Override
    public String toString()
    {
        return "DuringScope{methodPattern='" + methodPattern + "', isClinit=" + isClinit +
               ", classFilter=" + classFilter + "}";
    }
}
