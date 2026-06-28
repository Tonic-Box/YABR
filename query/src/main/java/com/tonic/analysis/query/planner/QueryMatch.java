package com.tonic.analysis.query.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single query match: the code location it points to, its computed attributes (e.g. {@code class},
 * {@code method}, {@code matches}), and any nested evidence matches (the bytecode sites that
 * satisfied the query). A domain model with no presentation concerns — display labels are the
 * caller's responsibility.
 */
public class QueryMatch {

    private final QueryTarget target;
    private final Map<String, Object> attributes;
    private final List<QueryMatch> evidence;

    public QueryMatch(QueryTarget target, Map<String, Object> attributes, List<QueryMatch> evidence) {
        this.target = target;
        this.attributes = attributes != null ? new LinkedHashMap<>(attributes) : new LinkedHashMap<>();
        this.evidence = evidence != null ? List.copyOf(evidence) : List.of();
    }

    public QueryTarget getTarget() {
        return target;
    }

    public List<QueryMatch> getEvidence() {
        return evidence;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public boolean hasEvidence() {
        return !evidence.isEmpty();
    }

    public static Builder builder(QueryTarget target) {
        return new Builder(target);
    }

    public static class Builder {
        private final QueryTarget target;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private List<QueryMatch> evidence = new ArrayList<>();

        Builder(QueryTarget target) {
            this.target = target;
        }

        public Builder attribute(String name, Object value) {
            attributes.put(name, value);
            return this;
        }

        public Builder evidence(List<QueryMatch> evidence) {
            this.evidence = evidence;
            return this;
        }

        public QueryMatch build() {
            return new QueryMatch(target, attributes, evidence);
        }
    }
}
