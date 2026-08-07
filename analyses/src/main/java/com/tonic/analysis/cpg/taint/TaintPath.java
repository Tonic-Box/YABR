package com.tonic.analysis.cpg.taint;

import com.tonic.analysis.cpg.node.CPGNode;

import java.util.*;

/**
 * A single tainted data flow from a source to a sink, with the CPG nodes traversed and any sanitizers seen along the way.
 */
public class TaintPath
{

    private final TaintSource source;
    private final TaintSink sink;
    private final CPGNode sourceNode;
    private final CPGNode sinkNode;
    private final List<CPGNode> path;
    private final Set<String> sanitizers;

    private TaintPath(TaintSource source, TaintSink sink, CPGNode sourceNode, CPGNode sinkNode, List<CPGNode> path)
    {
        this.source = source;
        this.sink = sink;
        this.sourceNode = sourceNode;
        this.sinkNode = sinkNode;
        this.path = List.copyOf(path);
        this.sanitizers = new LinkedHashSet<>();
    }

    /**
     * @return the source
     */
    public TaintSource getSource()
    {
        return source;
    }

    /**
     * @return the sink
     */
    public TaintSink getSink()
    {
        return sink;
    }

    /**
     * @return the source node
     */
    public CPGNode getSourceNode()
    {
        return sourceNode;
    }

    /**
     * @return the sink node
     */
    public CPGNode getSinkNode()
    {
        return sinkNode;
    }

    /**
     * @return the path
     */
    public List<CPGNode> getPath()
    {
        return path;
    }

    /**
     * @return the sanitizers
     */
    public Set<String> getSanitizers()
    {
        return sanitizers;
    }

    /**
     * @return a new empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * @return the number of nodes on the path
     */
    public int getPathLength()
    {
        return path.size();
    }

    /**
     * @return true if at least one sanitizer was recorded
     */
    public boolean isSanitized()
    {
        return !sanitizers.isEmpty();
    }

    /**
     * Records a sanitizer applied along this path.
     * @param sanitizer name of the sanitizing routine
     */
    public void addSanitizer(String sanitizer)
    {
        sanitizers.add(sanitizer);
    }

    /**
     * @return the vulnerability type reported by the sink
     */
    public VulnerabilityType getVulnerabilityType()
    {
        return sink.getVulnerabilityType();
    }

    /**
     * Severity of the sink, downgraded to INFO once the path is sanitized.
     * @return the effective severity
     */
    public Severity getSeverity()
    {
        return isSanitized() ? Severity.INFO : sink.getSeverity();
    }

    /**
     * Renders the source node position as "method:line", falling back to the node label.
     * @return the formatted location, or "unknown" when there is no source node
     */
    public String getSourceLocation()
    {
        if (sourceNode == null) return "unknown";
        Object line = sourceNode.getProperty("line");
        Object method = sourceNode.getProperty("methodName");
        if (line != null && method != null)
        {
            return method + ":" + line;
        }
        return sourceNode.getLabel();
    }

    /**
     * Renders the sink node position as "method:line", falling back to the node label.
     * @return the formatted location, or "unknown" when there is no sink node
     */
    public String getSinkLocation()
    {
        if (sinkNode == null) return "unknown";
        Object line = sinkNode.getProperty("line");
        Object method = sinkNode.getProperty("methodName");
        if (line != null && method != null)
        {
            return method + ":" + line;
        }
        return sinkNode.getLabel();
    }

    /**
     * Builds a multi-line report listing the source, every hop, the sink, and any sanitizers.
     * @return the formatted report
     */
    public String formatPath()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(source.getName()).append(" at ").append(getSourceLocation());
        sb.append("\n");

        for (int i = 0; i < path.size(); i++)
        {
            CPGNode node = path.get(i);
            sb.append("  ").append(i + 1).append(". ").append(node.getLabel());
            sb.append("\n");
        }

        sb.append("Sink: ").append(sink.getName()).append(" at ").append(getSinkLocation());

        if (!sanitizers.isEmpty())
        {
            sb.append("\n[SANITIZED by: ").append(String.join(", ", sanitizers)).append("]");
        }

        return sb.toString();
    }

    /**
     * Builds a one-line summary of the flow, its severity, hop count, and sanitization state.
     * @return the summary line
     */
    public String toShortString()
    {
        return String.format("%s -> %s (%s, %d hops%s)",
            source.getName(),
            sink.getName(),
            getSeverity(),
            getPathLength(),
            isSanitized() ? ", sanitized" : "");
    }

    @Override
    public String toString()
    {
        return formatPath();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaintPath that = (TaintPath) o;
        return Objects.equals(sourceNode, that.sourceNode) &&
               Objects.equals(sinkNode, that.sinkNode) &&
               Objects.equals(path, that.path);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(sourceNode, sinkNode, path);
    }

    /**
     * Accumulator for the endpoints and hop list of a TaintPath.
     */
    public static class Builder
    {
        private TaintSource source;
        private TaintSink sink;
        private CPGNode sourceNode;
        private CPGNode sinkNode;
        private final List<CPGNode> path = new ArrayList<>();

        /**
         * Sets the source definition the flow originates from.
         * @param source the matched taint source
         * @return this builder
         */
        public Builder source(TaintSource source)
        {
            this.source = source;
            return this;
        }

        /**
         * Sets the sink definition the flow terminates at.
         * @param sink the matched taint sink
         * @return this builder
         */
        public Builder sink(TaintSink sink)
        {
            this.sink = sink;
            return this;
        }

        /**
         * Sets the CPG node where the taint enters.
         * @param node the source call site
         * @return this builder
         */
        public Builder sourceNode(CPGNode node)
        {
            this.sourceNode = node;
            return this;
        }

        /**
         * Sets the CPG node where the taint is consumed.
         * @param node the sink call site
         * @return this builder
         */
        public Builder sinkNode(CPGNode node)
        {
            this.sinkNode = node;
            return this;
        }

        /**
         * Appends one hop to the end of the path.
         * @param node the node traversed
         * @return this builder
         */
        public Builder addToPath(CPGNode node)
        {
            this.path.add(node);
            return this;
        }

        /**
         * Replaces the accumulated hops with the given sequence.
         * @param path the nodes traversed, in order
         * @return this builder
         */
        public Builder path(List<CPGNode> path)
        {
            this.path.clear();
            this.path.addAll(path);
            return this;
        }

        /**
         * Creates the path from the accumulated state; the hop list is copied.
         * @return the new path, with no sanitizers recorded yet
         */
        public TaintPath build()
        {
            return new TaintPath(source, sink, sourceNode, sinkNode, path);
        }
    }
}
