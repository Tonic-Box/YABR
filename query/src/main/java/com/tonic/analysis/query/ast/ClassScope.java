package com.tonic.analysis.query.ast;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Scope limited to classes matching a pattern or exact name.
 * Example: IN class "Config.*" or IN class "com/example/Config"
 */
public final class ClassScope implements Scope
{

    private final String pattern;
    private final boolean isRegex;

    /**
     * Creates a class scope.
     * @param pattern the class name or regex to match
     * @param isRegex whether pattern is a regular expression
     */
    public ClassScope(String pattern, boolean isRegex)
    {
        this.pattern = pattern;
        this.isRegex = isRegex;
    }

    /**
     * @return the class name or regex pattern
     */
    public String pattern()
    {
        return pattern;
    }

    /**
     * @return whether the pattern is a regular expression
     */
    public boolean isRegex()
    {
        return isRegex;
    }

    /**
     * Creates a scope matching one exact class name.
     * @param className the internal class name to match
     * @return the exact-name scope
     */
    public static ClassScope exact(String className)
    {
        return new ClassScope(className, false);
    }

    /**
     * Creates a scope matching class names against a regex.
     * @param pattern the regular expression class names must match
     * @return the regex scope
     */
    public static ClassScope regex(String pattern)
    {
        return new ClassScope(pattern, true);
    }

    /**
     * Tests a class name against this scope.
     * @param className the internal class name to test
     * @return true if the name matches the pattern
     */
    public boolean matches(String className)
    {
        if (isRegex)
        {
            return Pattern.matches(pattern, className);
        }
        return pattern.equals(className);
    }

    @Override
    public <T> T accept(ScopeVisitor<T> visitor)
    {
        return visitor.visitClass(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ClassScope)) return false;
        ClassScope that = (ClassScope) o;
        return isRegex == that.isRegex && Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(pattern, isRegex);
    }

    @Override
    public String toString()
    {
        return "ClassScope{pattern='" + pattern + "', isRegex=" + isRegex + "}";
    }
}
