package com.tonic.analysis.query.value;

import java.util.Map;

/**
 * Normalizes the many spellings of a Java type to one canonical descriptor form.
 */
public final class TypeNames
{

    private static final Map<String, String> PRIMITIVES = Map.of(
            "void", "V", "boolean", "Z", "byte", "B", "char", "C",
            "short", "S", "int", "I", "long", "J", "float", "F", "double", "D");

    private TypeNames()
    {
    }

    /**
     * Reduces any accepted type spelling to its descriptor form.
     *
     * @param type the spelling to normalize, may be null
     * @return the canonical descriptor form of the type
     */
    public static String canonical(String type)
    {
        if (type == null)
        {
            return null;
        }
        String t = type.trim();
        if (t.isEmpty())
        {
            return t;
        }
        String prim = PRIMITIVES.get(t.toLowerCase());
        if (prim != null)
        {
            return prim;
        }
        if (t.startsWith("["))
        {
            return t.replace('.', '/');
        }
        if (t.length() == 1 && "VZBCSIJFD".indexOf(t.charAt(0)) >= 0)
        {
            return t;
        }
        if (t.startsWith("L") && t.endsWith(";"))
        {
            return t.replace('.', '/');
        }
        // A class name in internal (a/b/C) or dotted (a.b.C) form.
        return "L" + t.replace('.', '/') + ";";
    }
}
