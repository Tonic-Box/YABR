package com.tonic.analysis.dataflow;

import java.util.Objects;

/**
 * An edge in the data flow graph connecting two nodes.
 * Represents data flowing from source to target.
 */
public class DataFlowEdge
{

    private final DataFlowNode source;
    private final DataFlowNode target;
    private final DataFlowEdgeType type;
    private final String label;

    /**
     * Creates an edge with no explicit label.
     * @param source the node the data flows from
     * @param target the node the data flows to
     * @param type the kind of data flow
     */
    public DataFlowEdge(DataFlowNode source, DataFlowNode target, DataFlowEdgeType type)
    {
        this(source, target, type, null);
    }

    /**
     * Creates a labeled edge.
     * @param source the node the data flows from
     * @param target the node the data flows to
     * @param type the kind of data flow
     * @param label display label, or null to fall back to the type name
     */
    public DataFlowEdge(DataFlowNode source, DataFlowNode target, DataFlowEdgeType type, String label)
    {
        this.source = source;
        this.target = target;
        this.type = type;
        this.label = label;
    }

    /**
     * @return the source
     */
    public DataFlowNode getSource()
    {
        return source;
    }

    /**
     * @return the target
     */
    public DataFlowNode getTarget()
    {
        return target;
    }

    /**
     * @return the type
     */
    public DataFlowEdgeType getType()
    {
        return type;
    }

    /**
     * @return the label
     */
    public String getLabel()
    {
        return label;
    }

    /**
     * Returns the explicit label, falling back to the edge type's display name when unset.
     * @return the label to render for this edge
     */
    public String getDisplayLabel()
    {
        if (label != null && !label.isEmpty())
        {
            return label;
        }
        return type.getDisplayName();
    }

    /**
     * Builds multi-line tooltip text naming the edge type, its endpoints and the label.
     * @return the tooltip text
     */
    public String getTooltip()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDisplayName());
        sb.append("\n").append(source.getLabel()).append(" → ").append(target.getLabel());
        if (label != null)
        {
            sb.append("\n").append(label);
        }
        return sb.toString();
    }

    /**
     * Reports whether taint propagates along this edge, as decided by the edge type.
     * @return true if the edge type propagates taint
     */
    public boolean propagatesTaint()
    {
        return type.propagatesTaint();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataFlowEdge that = (DataFlowEdge) o;
        return Objects.equals(source, that.source) &&
               Objects.equals(target, that.target) &&
               type == that.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(source, target, type);
    }

    @Override
    public String toString()
    {
        return source.getLabel() + " --[" + type.name() + "]--> " + target.getLabel();
    }
}
