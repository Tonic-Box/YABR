package com.tonic.analysis.query.ast;

import com.tonic.analysis.query.value.Operator;

import java.util.List;

/**
 * The boolean expression tree of a query's {@code WHERE} clause - the only place booleans live.
 * A closed composite ({@link And}, {@link Or}, {@link Not}, {@link Group}, {@link Comparison},
 * {@link Quantifier}, {@link True}) visited via {@link Visitor} for evaluation/planning. Adding a new
 * queryable fact adds a registry entry, never a node here.
 */
public interface Condition
{

    /**
     * Dispatches to the visitor method for this node's variant.
     *
     * @param visitor the visitor to dispatch to
     * @param <R> the visitor's result type
     * @return whatever the visitor returns
     */
    <R> R accept(Visitor<R> visitor);

    interface Visitor<R>
    {
        /**
         * Visits a conjunction.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitAnd(And c);
        /**
         * Visits a disjunction.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitOr(Or c);
        /**
         * Visits a negation.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitNot(Not c);
        /**
         * Visits a parenthesized group.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitGroup(Group c);
        /**
         * Visits a comparison leaf.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitComparison(Comparison c);
        /**
         * Visits a quantification over a sub-subject stream.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitQuantifier(Quantifier c);
        /**
         * Visits a stream cardinality test.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitCount(Count c);
        /**
         * Visits an instruction-sequence pattern.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitSequence(Sequence c);
        /**
         * Visits the constant-true leaf.
         *
         * @param c the node
         * @return the visitor result
         */
        R visitTrue(True c);
    }

    /**
     * Existence/universality of a sub-condition over a sub-subject stream. {@code ANY == has}.
     */
    enum Quant { ANY, ALL, NONE }

    final class And implements Condition
    {
        private final List<Condition> terms;
        /**
         * Creates a conjunction over a defensive copy of the terms.
         *
         * @param terms the terms that must all hold
         */
        public And(List<Condition> terms) { this.terms = List.copyOf(terms); }
        /**
         * @return the conjoined terms
         */
        public List<Condition> terms() { return terms; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitAnd(this); }
        @Override public String toString() { return "(" + join(terms, " and ") + ")"; }
    }

    final class Or implements Condition
    {
        private final List<Condition> terms;
        /**
         * Creates a disjunction over a defensive copy of the terms.
         *
         * @param terms the terms of which at least one must hold
         */
        public Or(List<Condition> terms) { this.terms = List.copyOf(terms); }
        /**
         * @return the disjoined terms
         */
        public List<Condition> terms() { return terms; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitOr(this); }
        @Override public String toString() { return "(" + join(terms, " or ") + ")"; }
    }

    final class Not implements Condition
    {
        private final Condition inner;
        /**
         * Creates a negation.
         *
         * @param inner the condition to invert
         */
        public Not(Condition inner) { this.inner = inner; }
        /**
         * @return the negated condition
         */
        public Condition inner() { return inner; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitNot(this); }
        @Override public String toString() { return "not " + inner; }
    }

    final class Group implements Condition
    {
        private final Condition inner;
        /**
         * Creates an explicit grouping, preserving the source parentheses.
         *
         * @param inner the grouped condition
         */
        public Group(Condition inner) { this.inner = inner; }
        /**
         * @return the parenthesized condition
         */
        public Condition inner() { return inner; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitGroup(this); }
        @Override public String toString() { return "(" + inner + ")"; }
    }

    /**
     * {@code accessor OP operand} - the single generic leaf. A bare accessor uses op == null.
     */
    final class Comparison implements Condition
    {
        private final Accessor accessor;
        private final Operator op;     // null => boolean coercion of the accessor (truthy)
        private final Operand operand; // null when op is null
        /**
         * Creates a comparison leaf, or a truthiness test when no operator is given.
         *
         * @param accessor the left-hand accessor path
         * @param op the operator, or null to coerce the accessor to boolean
         * @param operand the right-hand operand, ignored when the operator is null
         */
        public Comparison(Accessor accessor, Operator op, Operand operand)
        {
            this.accessor = accessor;
            this.op = op;
            this.operand = operand;
        }
        /**
         * @return the left-hand accessor path
         */
        public Accessor accessor() { return accessor; }
        /**
         * @return the comparison operator, or null for boolean coercion
         */
        public Operator op() { return op; }
        /**
         * @return the right-hand operand, or null when op is null
         */
        public Operand operand() { return operand; }
        /**
         * @return true when this is a bare accessor coerced to boolean
         */
        public boolean isBoolean() { return op == null; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitComparison(this); }
        @Override public String toString() { return op == null ? accessor.toString() : accessor + " " + op + " " + operand; }
    }

    /**
     * {@code has|any|all|none <stream> where (body)}. {@code count(...) OP n} desugars to a Comparison.
     */
    final class Quantifier implements Condition
    {
        private final Quant quant;
        private final Accessor stream;
        private final Condition body;   // null => no `where`, i.e. plain existence
        /**
         * Creates a quantification over a sub-subject stream.
         *
         * @param quant whether any, all, or none of the stream must satisfy the body
         * @param stream the accessor producing the sub-subjects
         * @param body the per-element condition, or null to test existence alone
         */
        public Quantifier(Quant quant, Accessor stream, Condition body)
        {
            this.quant = quant;
            this.stream = stream;
            this.body = body;
        }
        /**
         * @return the quantification mode
         */
        public Quant quant() { return quant; }
        /**
         * @return the sub-subject stream accessor
         */
        public Accessor stream() { return stream; }
        /**
         * @return the where-body condition, or null for plain existence
         */
        public Condition body() { return body; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitQuantifier(this); }
        @Override public String toString()
        {
            return quant.name().toLowerCase() + " " + stream + (body == null ? "" : " where (" + body + ")");
        }
    }

    /**
     * {@code count(<stream> [where (body)]) OP n} - cardinality of a (optionally filtered) stream.
     */
    final class Count implements Condition
    {
        private final Accessor stream;
        private final Condition body;   // null => count all
        private final Operator op;
        private final Operand operand;
        /**
         * Creates a cardinality test over a sub-subject stream.
         *
         * @param stream the accessor producing the sub-subjects
         * @param body the filter applied before counting, or null to count everything
         * @param op the operator the count is tested with
         * @param operand the value the count is tested against
         */
        public Count(Accessor stream, Condition body, Operator op, Operand operand)
        {
            this.stream = stream;
            this.body = body;
            this.op = op;
            this.operand = operand;
        }
        /**
         * @return the counted stream accessor
         */
        public Accessor stream() { return stream; }
        /**
         * @return the filter condition, or null to count all
         */
        public Condition body() { return body; }
        /**
         * @return the operator applied to the count
         */
        public Operator op() { return op; }
        /**
         * @return the operand the count is compared against
         */
        public Operand operand() { return operand; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitCount(this); }
        @Override public String toString()
        {
            return "count(" + stream + (body == null ? "" : " where (" + body + ")") + ") " + op + " " + operand;
        }
    }

    /**
     * {@code SEQUENCE [ e1, e2, .. ]} - an ordered, unanchored regex over the method's instruction
     * stream. Each {@link Element} carries a per-instruction matcher condition plus repetition bounds;
     * a gap ({@code ..}) and any-one ({@code _}) are just {@link True} matchers with different bounds.
     */
    final class Sequence implements Condition
    {
        private final List<Element> elements;
        /**
         * Creates an instruction-sequence pattern over a defensive copy of the elements.
         *
         * @param elements the pattern elements, in match order
         */
        public Sequence(List<Element> elements) { this.elements = List.copyOf(elements); }
        /**
         * @return the ordered pattern elements
         */
        public List<Element> elements() { return elements; }
        @Override public <R> R accept(Visitor<R> v) { return v.visitSequence(this); }
        @Override public String toString()
        {
            StringBuilder sb = new StringBuilder("sequence [");
            for (int i = 0; i < elements.size(); i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(elements.get(i));
            }
            return sb.append("]").toString();
        }

        /**
         * One pattern element: a matcher evaluated against an instruction, repeated {@code [min,max]} times.
         */
        public static final class Element
        {
            public static final int UNBOUNDED = Integer.MAX_VALUE;
            private final Condition matcher;
            private final int min;
            private final int max;
            /**
             * Creates a pattern element with explicit repetition bounds.
             *
             * @param matcher the condition each matched instruction must satisfy
             * @param min the fewest consecutive matches accepted
             * @param max the most consecutive matches accepted, UNBOUNDED for no limit
             */
            public Element(Condition matcher, int min, int max)
            {
                this.matcher = matcher;
                this.min = min;
                this.max = max;
            }
            /**
             * @return the per-instruction matcher condition
             */
            public Condition matcher() { return matcher; }
            /**
             * @return the minimum repetition count
             */
            public int min() { return min; }
            /**
             * @return the maximum repetition count, UNBOUNDED for no limit
             */
            public int max() { return max; }
            /**
             * Creates the gap element that matches any run of instructions.
             * @return an element matching zero or more arbitrary instructions
             */
            public static Element gap() { return new Element(True.INSTANCE, 0, UNBOUNDED); }
            @Override public String toString()
            {
                String base = matcher == True.INSTANCE && min == 0 ? ".." : matcher.toString();
                if (matcher == True.INSTANCE)
                {
                    return min == 0 ? ".." : "_";
                }
                String rep = (min == 1 && max == 1) ? ""
                        : (min == 0 && max == UNBOUNDED) ? "*"
                        : (min == 1 && max == UNBOUNDED) ? "+"
                        : "{" + min + "," + (max == UNBOUNDED ? "" : max) + "}";
                return "(" + base + ")" + rep;
            }
        }
    }

    /**
     * Constant truth, used as an identity element when simplifying/splitting trees.
     */
    final class True implements Condition
    {
        public static final True INSTANCE = new True();
        /**
         * Withheld so {@link #INSTANCE} stays the only value, which lets code compare by identity.
         */
        private True() { }
        @Override public <R> R accept(Visitor<R> v) { return v.visitTrue(this); }
        @Override public String toString() { return "true"; }
    }

    /**
     * Renders conditions in order, separated by the given text.
     *
     * @param terms the conditions to render
     * @param sep the text placed between consecutive terms
     * @return the rendered text
     */
    static String join(List<Condition> terms, String sep)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.size(); i++)
        {
            if (i > 0) sb.append(sep);
            sb.append(terms.get(i));
        }
        return sb.toString();
    }
}
