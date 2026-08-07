package com.tonic.analysis.query.eval;

import com.tonic.analysis.query.ast.Step;
import com.tonic.analysis.query.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Maps {@code (SubjectKind, keyword)} to a resolver - either a scalar {@link Attribute} or a sub-subject
 * {@link Selector}.
 */
public final class AttributeRegistry
{

    /**
     * Resolves a scalar value from a subject.
     */
    @FunctionalInterface
    public interface Attribute
    {
        /**
         * Reads this attribute off a subject.
         *
         * @param subject the subject to read from
         * @return the attribute value
         */
        Value get(Subject subject);
    }

    /**
     * Expands a subject into a stream of sub-subjects (honoring the step's index/wildcard).
     */
    @FunctionalInterface
    public interface Selector
    {
        /**
         * Expands a subject into the sub-subjects this keyword selects.
         *
         * @param subject the subject to expand
         * @param step the accessor step, carrying any index or wildcard to honor
         * @return the selected sub-subjects
         */
        Stream<Subject> expand(Subject subject, Step step);
    }

    /**
     * A registered resolver: exactly one of {@code scalar}/{@code stream} is non-null.
     */
    public static final class Entry
    {
        private final Attribute scalar;
        private final Selector stream;

        private Entry(Attribute scalar, Selector stream)
        {
            this.scalar = scalar;
            this.stream = stream;
        }

        /**
         * @return true if this entry expands into sub-subjects rather than a scalar
         */
        public boolean isStream() { return stream != null; }
        /**
         * @return the scalar resolver, or null if this entry is a stream
         */
        public Attribute scalar() { return scalar; }
        /**
         * @return the sub-subject expansion, or null if this entry is scalar
         */
        public Selector stream() { return stream; }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    /**
     * Registers a keyword that resolves to a scalar value.
     *
     * @param kind the subject kind the keyword applies to
     * @param keyword the attribute keyword
     * @param attribute the resolver
     * @throws IllegalStateException if the kind and keyword are already registered
     */
    public void registerScalar(SubjectKind kind, String keyword, Attribute attribute)
    {
        put(kind, keyword, new Entry(attribute, null));
    }

    /**
     * Registers a keyword that expands a subject into sub-subjects.
     *
     * @param kind the subject kind the keyword applies to
     * @param keyword the attribute keyword
     * @param selector the expansion
     * @throws IllegalStateException if the kind and keyword are already registered
     */
    public void registerStream(SubjectKind kind, String keyword, Selector selector)
    {
        put(kind, keyword, new Entry(null, selector));
    }

    /**
     * Finds the resolver registered for a keyword; keyword matching is case-insensitive.
     *
     * @param kind the subject kind the keyword is used on
     * @param keyword the attribute keyword
     * @return the entry, or null if nothing is registered
     */
    public Entry lookup(SubjectKind kind, String keyword)
    {
        return entries.get(key(kind, keyword));
    }

    private void put(SubjectKind kind, String keyword, Entry entry)
    {
        if (entries.putIfAbsent(key(kind, keyword), entry) != null)
        {
            throw new IllegalStateException("Duplicate attribute: " + kind + "#" + keyword);
        }
    }

    private static String key(SubjectKind kind, String keyword)
    {
        return kind.name() + '#' + keyword.toLowerCase();
    }
}
