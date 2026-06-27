package com.tonic.analysis.source.ast.decl;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

@Getter
public enum Modifier {
    PUBLIC("public"),
    PROTECTED("protected"),
    PRIVATE("private"),
    STATIC("static"),
    FINAL("final"),
    ABSTRACT("abstract"),
    SYNCHRONIZED("synchronized"),
    NATIVE("native"),
    STRICTFP("strictfp"),
    TRANSIENT("transient"),
    VOLATILE("volatile"),
    DEFAULT("default");

    private final String keyword;

    Modifier(String keyword) {
        this.keyword = keyword;
    }

    public static Set<Modifier> fromAccessFlags(int flags) {
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

    public static String toSourceString(Set<Modifier> modifiers) {
        if (modifiers.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Modifier mod : Modifier.values()) {
            if (modifiers.contains(mod)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(mod.keyword);
            }
        }
        return sb.toString();
    }

    public boolean isAccessModifier() {
        return this == PUBLIC || this == PROTECTED || this == PRIVATE;
    }
}
