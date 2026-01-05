package com.tonic.analysis.cpg.taint;

import com.tonic.analysis.cpg.node.CPGNode;
import lombok.Getter;

import java.util.*;

@Getter
public class TaintPath {

    private final TaintSource source;
    private final TaintSink sink;
    private final CPGNode sourceNode;
    private final CPGNode sinkNode;
    private final List<CPGNode> path;
    private final Set<String> sanitizers;

    private TaintPath(TaintSource source, TaintSink sink, CPGNode sourceNode,
                      CPGNode sinkNode, List<CPGNode> path) {
        this.source = source;
        this.sink = sink;
        this.sourceNode = sourceNode;
        this.sinkNode = sinkNode;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        this.sanitizers = new LinkedHashSet<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getPathLength() {
        return path.size();
    }

    public boolean isSanitized() {
        return !sanitizers.isEmpty();
    }

    public void addSanitizer(String sanitizer) {
        sanitizers.add(sanitizer);
    }

    public VulnerabilityType getVulnerabilityType() {
        return sink.getVulnerabilityType();
    }

    public Severity getSeverity() {
        return isSanitized() ? Severity.INFO : sink.getSeverity();
    }

    public String getSourceLocation() {
        if (sourceNode == null) return "unknown";
        Object line = sourceNode.getProperty("line");
        Object method = sourceNode.getProperty("methodName");
        if (line != null && method != null) {
            return method + ":" + line;
        }
        return sourceNode.getLabel();
    }

    public String getSinkLocation() {
        if (sinkNode == null) return "unknown";
        Object line = sinkNode.getProperty("line");
        Object method = sinkNode.getProperty("methodName");
        if (line != null && method != null) {
            return method + ":" + line;
        }
        return sinkNode.getLabel();
    }

    public String formatPath() {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(source.getName()).append(" at ").append(getSourceLocation());
        sb.append("\n");

        for (int i = 0; i < path.size(); i++) {
            CPGNode node = path.get(i);
            sb.append("  ").append(i + 1).append(". ").append(node.getLabel());
            sb.append("\n");
        }

        sb.append("Sink: ").append(sink.getName()).append(" at ").append(getSinkLocation());

        if (!sanitizers.isEmpty()) {
            sb.append("\n[SANITIZED by: ").append(String.join(", ", sanitizers)).append("]");
        }

        return sb.toString();
    }

    public String toShortString() {
        return String.format("%s -> %s (%s, %d hops%s)",
            source.getName(),
            sink.getName(),
            getSeverity(),
            getPathLength(),
            isSanitized() ? ", sanitized" : "");
    }

    @Override
    public String toString() {
        return formatPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaintPath that = (TaintPath) o;
        return Objects.equals(sourceNode, that.sourceNode) &&
               Objects.equals(sinkNode, that.sinkNode) &&
               Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNode, sinkNode, path);
    }

    public static class Builder {
        private TaintSource source;
        private TaintSink sink;
        private CPGNode sourceNode;
        private CPGNode sinkNode;
        private final List<CPGNode> path = new ArrayList<>();

        public Builder source(TaintSource source) {
            this.source = source;
            return this;
        }

        public Builder sink(TaintSink sink) {
            this.sink = sink;
            return this;
        }

        public Builder sourceNode(CPGNode node) {
            this.sourceNode = node;
            return this;
        }

        public Builder sinkNode(CPGNode node) {
            this.sinkNode = node;
            return this;
        }

        public Builder addToPath(CPGNode node) {
            this.path.add(node);
            return this;
        }

        public Builder path(List<CPGNode> path) {
            this.path.clear();
            this.path.addAll(path);
            return this;
        }

        public TaintPath build() {
            return new TaintPath(source, sink, sourceNode, sinkNode, path);
        }
    }
}
