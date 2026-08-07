package com.tonic.analysis.query.ast;

/**
 * Classification of how a call argument value is produced, with DYNAMIC covering any non-literal source.
 */
public enum ArgumentType
{
    /**
     * Wildcard that accepts every other classification; also the fallback for an unrecognized
     * type keyword.
     */
    ANY,
    /**
     * The argument is a compile-time constant pushed at the call site.
     */
    LITERAL,
    /**
     * Anything computed at run time; as an expectation it accepts FIELD, LOCAL and CALL.
     */
    DYNAMIC,
    /**
     * The argument comes straight from a field read.
     */
    FIELD,
    /**
     * The argument comes straight from a local variable.
     */
    LOCAL,
    /**
     * The argument is the return value of another call.
     */
    CALL;

    /**
     * Tests whether an actual argument classification satisfies this expected one.
     * @param actual the classification observed at the call site
     * @return true if this type accepts the actual type, with ANY accepting everything and DYNAMIC accepting FIELD, LOCAL, and CALL
     */
    public boolean matches(ArgumentType actual)
    {
        if (this == ANY) return true;
        if (this == DYNAMIC)
        {
            return actual == FIELD || actual == LOCAL || actual == CALL;
        }
        return this == actual;
    }

    /**
     * Parses a case-insensitive type keyword.
     * @param s the keyword to parse, may be null
     * @return the matching type, or ANY for null or unknown input
     */
    public static ArgumentType fromString(String s)
    {
        if (s == null) return ANY;
        switch (s.toLowerCase())
        {
            case "literal": return LITERAL;
            case "dynamic": return DYNAMIC;
            case "field": return FIELD;
            case "local": return LOCAL;
            case "call": return CALL;
            default: return ANY;
        }
    }
}
