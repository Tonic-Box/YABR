package com.tonic.analysis.query.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single query match.
 */
public class QueryMatch
{

    private final QueryTarget target;
    private final Map<String, Object> attributes;
    private final List<QueryMatch> evidence;

    /**
     * Creates a match, copying the attributes and evidence defensively.
     * @param target the matched code location
     * @param attributes the computed attributes, may be null for none
     * @param evidence the nested evidence matches, may be null for none
     */
    public QueryMatch(QueryTarget target, Map<String, Object> attributes, List<QueryMatch> evidence)
    {
        this.target = target;
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
        this.evidence = evidence != null ? List.copyOf(evidence) : List.of();
    }

    /**
     * @return the target
     */
    public QueryTarget getTarget()
    {
        return target;
    }

    /**
     * @return the evidence
     */
    public List<QueryMatch> getEvidence()
    {
        return evidence;
    }

    /**
     * @return an unmodifiable view of the computed attributes, in insertion order
     */
    public Map<String, Object> getAttributes()
    {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Looks up one computed attribute.
     * @param name the attribute name
     * @return the value, or null if the attribute was not computed
     */
    public Object getAttribute(String name)
    {
        return attributes.get(name);
    }

    /**
     * @return true if this match carries nested evidence matches
     */
    public boolean hasEvidence()
    {
        return !evidence.isEmpty();
    }

    /**
     * Starts a builder for a match at a code location.
     * @param target the matched code location
     * @return the new builder
     */
    public static Builder builder(QueryTarget target)
    {
        return new Builder(target);
    }

    /**
     * Fluent builder that accumulates attributes and evidence for one match.
     */
    public static class Builder
    {
        private final QueryTarget target;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private List<QueryMatch> evidence = new ArrayList<>();

        Builder(QueryTarget target)
        {
            this.target = target;
        }

        /**
         * Adds one attribute, replacing any previous value under the same name.
         * @param name the attribute name
         * @param value the attribute value
         * @return this builder
         */
        public Builder attribute(String name, Object value)
        {
            attributes.put(name, value);
            return this;
        }

        /**
         * Sets the nested matches that back this one.
         * @param evidence the evidence matches, replacing the current list
         * @return this builder
         */
        public Builder evidence(List<QueryMatch> evidence)
        {
            this.evidence = evidence;
            return this;
        }

        /**
         * @return a match holding the accumulated target, attributes and evidence
         */
        public QueryMatch build()
        {
            return new QueryMatch(target, attributes, evidence);
        }
    }
}
