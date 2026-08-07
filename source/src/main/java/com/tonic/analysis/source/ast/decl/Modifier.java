package com.tonic.analysis.source.ast.decl;

import java.util.EnumSet;
import java.util.Set;

/**
 * A Java source modifier keyword, paired with the keyword text it prints as.
 */
public enum Modifier
{
    /**
     * Access flag {@code 0x0001}, visible everywhere; prints first because
     * declaration order is print order.
     */
    PUBLIC("public"),
    /**
     * Access flag {@code 0x0004}, visible to the package and to subclasses.
     */
    PROTECTED("protected"),
    /**
     * Access flag {@code 0x0002}, visible only within the declaring class.
     */
    PRIVATE("private"),
    /**
     * Access flag {@code 0x0008}, bound to the class rather than an instance.
     */
    STATIC("static"),
    /**
     * Access flag {@code 0x0010}, forbidding reassignment, overriding or
     * subclassing depending on what it is applied to.
     */
    FINAL("final"),
    /**
     * Access flag {@code 0x0400}, declaring a class or method with no body of
     * its own.
     */
    ABSTRACT("abstract"),
    /**
     * Access flag {@code 0x0020} on a method, taking the receiver's monitor
     * for the duration of the call.
     */
    SYNCHRONIZED("synchronized"),
    /**
     * Access flag {@code 0x0100}, marking a method implemented outside the
     * class file.
     */
    NATIVE("native"),
    /**
     * Access flag {@code 0x0800}, pinning floating point to strict IEEE 754
     * semantics.
     */
    STRICTFP("strictfp"),
    /**
     * Access flag {@code 0x0080} on a field, excluding it from default
     * serialization.
     */
    TRANSIENT("transient"),
    /**
     * Access flag {@code 0x0040} on a field, forcing every access to reach
     * main memory.
     */
    VOLATILE("volatile"),
    /**
     * A default interface method body; source-only, with no access flag bit,
     * so decoding flags never yields it.
     */
    DEFAULT("default");

    private final String keyword;

    Modifier(String keyword)
    {
        this.keyword = keyword;
    }

    /**
     * @return the keyword
     */
    public String getKeyword()
    {
        return keyword;
    }

    /**
     * Decodes class file access flags into modifiers; DEFAULT has no flag bit and is never
     * produced.
     * @param flags the access flag bits
     * @return the modifiers the bits set
     */
    public static Set<Modifier> fromAccessFlags(int flags)
    {
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);

        if ((flags & 0x0001) != 0) modifiers.add(PUBLIC);
        if ((flags & 0x0002) != 0) modifiers.add(PRIVATE);
        if ((flags & 0x0004) != 0) modifiers.add(PROTECTED);
        if ((flags & 0x0008) != 0) modifiers.add(STATIC);
        if ((flags & 0x0010) != 0) modifiers.add(FINAL);
        if ((flags & 0x0020) != 0) modifiers.add(SYNCHRONIZED);
        if ((flags & 0x0040) != 0) modifiers.add(VOLATILE);
        if ((flags & 0x0080) != 0) modifiers.add(TRANSIENT);
        if ((flags & 0x0100) != 0) modifiers.add(NATIVE);
        if ((flags & 0x0400) != 0) modifiers.add(ABSTRACT);
        if ((flags & 0x0800) != 0) modifiers.add(STRICTFP);

        return modifiers;
    }

    /**
     * Joins modifiers with spaces in declaration order, regardless of the set's own order.
     * @param modifiers the modifiers to print
     * @return the keyword text, empty if there are none
     */
    public static String toSourceString(Set<Modifier> modifiers)
    {
        if (modifiers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Modifier mod : Modifier.values())
        {
            if (modifiers.contains(mod))
            {
                if (sb.length() > 0) sb.append(" ");
                sb.append(mod.keyword);
            }
        }
        return sb.toString();
    }

    /**
     * @return whether this is public, protected or private
     */
    public boolean isAccessModifier()
    {
        return this == PUBLIC || this == PROTECTED || this == PRIVATE;
    }
}
