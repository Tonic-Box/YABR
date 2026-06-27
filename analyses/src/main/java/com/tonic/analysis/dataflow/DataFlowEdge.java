package com.tonic.analysis.dataflow;

import java.util.Objects;

/**
 * An edge in the data flow graph connecting two nodes.
 * Represents data flowing from source to target.
 */
public class DataFlowEdge {

    private final DataFlowNode source;
    private final DataFlowNode target;
    private final DataFlowEdgeType type;
    private final String label;

    public DataFlowEdge(DataFlowNode source, DataFlowNode target, DataFlowEdgeType type) {
        this(source, target, type, null);
    }

    public DataFlowEdge(DataFlowNode source, DataFlowNode target, DataFlowEdgeType type, String label) {
        this.source = source;
        this.target = target;
        this.type = type;
        this.label = label;
    }

    public DataFlowNode getSource() {
        return source;
    }

    public DataFlowNode getTarget() {
        return target;
    }

    public DataFlowEdgeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Get display label for the edge.
     */
    public String getDisplayLabel() {
        if (label != null && !label.isEmpty()) {
            return label;
        }
        return type.getDisplayName();
    }

    /**
     * Get tooltip text for the edge.
     */
    public String getTooltip() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDisplayName());
        sb.append("\n").append(source.getLabel()).append(" → ").append(target.getLabel());
        if (label != null) {
            sb.append("\n").append(label);
        }
        return sb.toString();
    }

    /**
     * Check if taint should propagate through this edge.
     */
    public boolean propagatesTaint() {
        return type.propagatesTaint();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFlowEdge that = (DataFlowEdge) o;
        return Objects.equals(source, that.source) &&
               Objects.equals(target, that.target) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, type);
    }

    @Override
    public String toString() {
        return source.getLabel() + " --[" + type.name() + "]--> " + target.getLabel();
    }
}
