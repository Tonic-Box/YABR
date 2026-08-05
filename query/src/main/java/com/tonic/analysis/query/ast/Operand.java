package com.tonic.analysis.query.ast;

import com.tonic.analysis.query.value.Value;

/**
 * The right-hand side of a {@link Condition.Comparison}: either a literal {@link Value} or another
 * {@link Accessor} (so {@code arg(0).value == arg(1).value} works). Closed set, two nested variants.
 */
public interface Operand
{

    /**
     * @param value the literal to compare against
     * @return the wrapping operand
     */
    static Operand literal(Value value)
    {
        return new Literal(value);
    }

    /**
     * @param accessor the accessor path to compare against
     * @return the wrapping operand
     */
    static Operand accessor(Accessor accessor)
    {
        return new Ref(accessor);
    }

    final class Literal implements Operand
    {
        private final Value value;
        /**
         * @param value the literal to compare against
         */
        public Literal(Value value) { this.value = value; }
        /**
         * @return the literal value
         */
        public Value value() { return value; }
        @Override public String toString() { return value.toString(); }
    }

    final class Ref implements Operand
    {
        private final Accessor accessor;
        /**
         * @param accessor the accessor path to compare against
         */
        public Ref(Accessor accessor) { this.accessor = accessor; }
        /**
         * @return the referenced accessor path
         */
        public Accessor accessor() { return accessor; }
        @Override public String toString() { return accessor.toString(); }
    }
}
