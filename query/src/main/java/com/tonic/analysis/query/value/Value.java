package com.tonic.analysis.query.value;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The universal scalar produced by attribute resolution and carried by query literals, as a closed set of
 * nested variants discriminated by {@link #kind()}.
 */
public interface Value
{

    /**
     * @return the discriminator identifying which variant this is
     */
    ValueKind kind();

    /**
     * The sentinel for "could not be determined statically".
     */
    Value ABSENT = new Absent();

    /**
     * Wraps a whole number.
     * @param v the number to wrap
     * @return an INT-kinded value
     */
    static Value of(long v)
    {
        return new IntValue(v);
    }

    /**
     * Wraps a floating point number.
     * @param v the number to wrap
     * @return a REAL-kinded value
     */
    static Value of(double v)
    {
        return new RealValue(v);
    }

    /**
     * Wraps a flag, reusing the two shared instances.
     * @param v the flag to wrap
     * @return a BOOL-kinded value
     */
    static Value of(boolean v)
    {
        return v ? BoolValue.TRUE : BoolValue.FALSE;
    }

    /**
     * Wraps a string, mapping null onto the null value rather than failing.
     * @param v the string to wrap, may be null
     * @return a STRING-kinded value, or the NULL-kinded value when v is null
     */
    static Value of(String v)
    {
        return v == null ? ofNull() : new StrValue(v);
    }

    /**
     * Wraps a type reference.
     * @param internalNameOrDescriptor an internal name such as {@code java/lang/String}, or a descriptor such as {@code I}
     * @return a TYPE-kinded value
     */
    static Value ofType(String internalNameOrDescriptor)
    {
        return new TypeValue(internalNameOrDescriptor);
    }

    /**
     * Wraps a compiled pattern for use as the right-hand side of a match.
     * @param pattern the pattern to wrap
     * @return a REGEX-kinded value
     */
    static Value ofRegex(Pattern pattern)
    {
        return new RegexValue(pattern);
    }

    /**
     * Wraps a member list for use as the right-hand side of a membership test.
     * @param members the members, copied defensively
     * @return a SET-kinded value
     */
    static Value ofSet(List<Value> members)
    {
        return new SetValue(members);
    }

    /**
     * @return the shared NULL-kinded value
     */
    static Value ofNull()
    {
        return NullValue.INSTANCE;
    }

    final class IntValue implements Value
    {
        private final long value;
        /**
         * @param value the whole number to carry
         */
        public IntValue(long value) { this.value = value; }
        /**
         * @return the integral value
         */
        public long get() { return value; }
        @Override public ValueKind kind() { return ValueKind.INT; }
        @Override public String toString() { return Long.toString(value); }
        @Override public boolean equals(Object o) { return o instanceof IntValue && ((IntValue) o).value == value; }
        @Override public int hashCode() { return Long.hashCode(value); }
    }

    final class RealValue implements Value
    {
        private final double value;
        /**
         * @param value the floating point number to carry
         */
        public RealValue(double value) { this.value = value; }
        /**
         * @return the floating point value
         */
        public double get() { return value; }
        @Override public ValueKind kind() { return ValueKind.REAL; }
        @Override public String toString() { return Double.toString(value); }
        @Override public boolean equals(Object o) { return o instanceof RealValue && Double.compare(((RealValue) o).value, value) == 0; }
        @Override public int hashCode() { return Double.hashCode(value); }
    }

    final class StrValue implements Value
    {
        private final String value;
        /**
         * @param value the string to carry
         * @throws NullPointerException if value is null; use {@link Value#ofNull()} instead
         */
        public StrValue(String value) { this.value = Objects.requireNonNull(value); }
        /**
         * @return the string value, never null
         */
        public String get() { return value; }
        @Override public ValueKind kind() { return ValueKind.STRING; }
        @Override public String toString() { return '"' + value + '"'; }
        @Override public boolean equals(Object o) { return o instanceof StrValue && ((StrValue) o).value.equals(value); }
        @Override public int hashCode() { return value.hashCode(); }
    }

    /**
     * A type reference: either an internal name ({@code java/lang/String}) or a descriptor ({@code I}).
     */
    final class TypeValue implements Value
    {
        private final String type;
        /**
         * @param type an internal name or a descriptor
         * @throws NullPointerException if type is null
         */
        public TypeValue(String type) { this.type = Objects.requireNonNull(type); }
        /**
         * @return the internal name or descriptor, never null
         */
        public String get() { return type; }
        @Override public ValueKind kind() { return ValueKind.TYPE; }
        @Override public String toString() { return type; }
        @Override public boolean equals(Object o) { return o instanceof TypeValue && ((TypeValue) o).type.equals(type); }
        @Override public int hashCode() { return type.hashCode(); }
    }

    final class BoolValue implements Value
    {
        public static final BoolValue TRUE = new BoolValue(true);
        public static final BoolValue FALSE = new BoolValue(false);
        private final boolean value;
        /**
         * Use the shared {@link #TRUE} and {@link #FALSE} instances.
         * @param value the flag to carry
         */
        private BoolValue(boolean value) { this.value = value; }
        /**
         * @return the boolean value
         */
        public boolean get() { return value; }
        @Override public ValueKind kind() { return ValueKind.BOOL; }
        @Override public String toString() { return Boolean.toString(value); }
    }

    final class RegexValue implements Value
    {
        private final Pattern pattern;
        /**
         * @param pattern the compiled pattern to match with
         * @throws NullPointerException if pattern is null
         */
        public RegexValue(Pattern pattern) { this.pattern = Objects.requireNonNull(pattern); }
        /**
         * @return the compiled pattern, never null
         */
        public Pattern get() { return pattern; }
        @Override public ValueKind kind() { return ValueKind.REGEX; }
        @Override public String toString() { return "/" + pattern.pattern() + "/"; }
    }

    final class SetValue implements Value
    {
        private final List<Value> members;
        /**
         * @param members the members, copied into an immutable list
         * @throws NullPointerException if members is null or holds a null
         */
        public SetValue(List<Value> members) { this.members = List.copyOf(members); }
        /**
         * @return the immutable member list
         */
        public List<Value> get() { return members; }
        @Override public ValueKind kind() { return ValueKind.SET; }
        @Override public String toString() { return members.toString(); }
    }

    final class NullValue implements Value
    {
        static final NullValue INSTANCE = new NullValue();
        /**
         * Use the shared instance handed out by {@link Value#ofNull()}.
         */
        private NullValue() { }
        @Override public ValueKind kind() { return ValueKind.NULL; }
        @Override public String toString() { return "null"; }
    }

    final class Absent implements Value
    {
        /**
         * Use the shared {@link Value#ABSENT} instance.
         */
        private Absent() { }
        @Override public ValueKind kind() { return ValueKind.ABSENT; }
        @Override public String toString() { return "<absent>"; }
    }
}
