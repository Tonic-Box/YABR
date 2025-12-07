package com.tonic.analysis.ssa.value;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import lombok.Getter;

/**
 * Represents a string constant.
 */
@Getter
public final class StringConstant extends Constant {

    private final String value;

    /**
     * Creates a string constant with the given value.
     *
     * @param value the string value
     */
    public StringConstant(String value) {
        this.value = value;
    }

    @Override
    public IRType getType() {
        return ReferenceType.STRING;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringConstant that)) return false;
        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
