package com.tonic.analysis.query.ast;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Scope limited to methods matching a pattern or signature.
 * Example: IN method "get.*" or IN method "com/foo/Bar.process(I)V"
 */
public final class MethodScope implements Scope
{

    private final String pattern;
    private final boolean isRegex;

    /**
     * Creates a method scope.
     * @param pattern the method signature or regex to match
     * @param isRegex whether pattern is a regular expression
     */
    public MethodScope(String pattern, boolean isRegex)
    {
        this.pattern = pattern;
        this.isRegex = isRegex;
    }

    /**
     * @return the method signature or regex pattern
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
     * Creates a scope matching one exact method signature.
     * @param signature the method signature to match
     * @return the exact-signature scope
     */
    public static MethodScope exact(String signature)
    {
        return new MethodScope(signature, false);
    }

    /**
     * Creates a scope matching method signatures against a regex.
     * @param pattern the regular expression signatures must match
     * @return the regex scope
     */
    public static MethodScope regex(String pattern)
    {
        return new MethodScope(pattern, true);
    }

    /**
     * Tests a method signature against this scope.
     * @param methodSignature the signature to test
     * @return true if the signature matches the pattern
     */
    public boolean matches(String methodSignature)
    {
        if (isRegex)
        {
            return Pattern.matches(pattern, methodSignature);
        }
        return pattern.equals(methodSignature);
    }

    @Override
    public <T> T accept(ScopeVisitor<T> visitor)
    {
        return visitor.visitMethod(this);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof MethodScope)) return false;
        MethodScope that = (MethodScope) o;
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
        return "MethodScope{pattern='" + pattern + "', isRegex=" + isRegex + "}";
    }
}
