package com.tonic.analysis.pdg.edge;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.ssa.value.SSAValue;

/**
 * A directed dependence edge in a program dependence graph.
 */
public class PDGEdge
{

    private final PDGNode source;
    private final PDGNode target;
    private final PDGDependenceType type;
    private final String label;
    private final SSAValue dependentValue;
    private final boolean branchCondition;

    private boolean tainted;

    /**
     * Creates an unlabelled edge that carries no value.
     * @param source the depended-upon node
     * @param target the dependent node
     * @param type the dependence kind
     */
    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type)
    {
        this(source, target, type, null, null, false);
    }

    /**
     * Creates an unlabelled edge that carries the value flowing along it.
     * @param source the depended-upon node
     * @param target the dependent node
     * @param type the dependence kind
     * @param dependentValue the value flowing from source to target
     */
    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type, SSAValue dependentValue)
    {
        this(source, target, type, null, dependentValue, false);
    }

    /**
     * Creates a labelled edge that carries no value.
     * @param source the depended-upon node
     * @param target the dependent node
     * @param type the dependence kind
     * @param label text shown on the edge
     */
    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type, String label)
    {
        this(source, target, type, label, null, false);
    }

    /**
     * Creates an edge with every attribute given explicitly.
     * @param source the depended-upon node
     * @param target the dependent node
     * @param type the dependence kind
     * @param label text shown on the edge, or null
     * @param dependentValue the value flowing from source to target, or null
     * @param branchCondition the branch outcome this control edge is taken on
     */
    public PDGEdge(PDGNode source, PDGNode target, PDGDependenceType type, String label, SSAValue dependentValue, boolean branchCondition)
    {
        this.source = source;
        this.target = target;
        this.type = type;
        this.label = label;
        this.dependentValue = dependentValue;
        this.branchCondition = branchCondition;
    }

    /**
     * @return the source
     */
    public PDGNode getSource()
    {
        return source;
    }

    /**
     * @return the target
     */
    public PDGNode getTarget()
    {
        return target;
    }

    /**
     * @return the type
     */
    public PDGDependenceType getType()
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
     * @return the dependent value
     */
    public SSAValue getDependentValue()
    {
        return dependentValue;
    }

    /**
     * @return whether branch condition
     */
    public boolean isBranchCondition()
    {
        return branchCondition;
    }

    /**
     * @return whether tainted
     */
    public boolean isTainted()
    {
        return tainted;
    }

    /**
     * Marks whether tainted data travels along this edge.
     * @param tainted true if the flow is tainted
     */
    public void setTainted(boolean tainted)
    {
        this.tainted = tainted;
    }

    /**
     * Creates a control dependence edge for one outcome of a branch.
     * @param source the branching node
     * @param target the node controlled by the branch
     * @param condition the branch outcome the target is reached on
     * @return the control edge
     */
    public static PDGEdge controlEdge(PDGNode source, PDGNode target, boolean condition)
    {
        PDGDependenceType edgeType = PDGDependenceType.forBranchCondition(condition);
        return new PDGEdge(source, target, edgeType, null, null, condition);
    }

    /**
     * Creates a def-use data dependence edge.
     * @param source the defining node
     * @param target the using node
     * @param value the value that flows
     * @return the data edge
     */
    public static PDGEdge dataEdge(PDGNode source, PDGNode target, SSAValue value)
    {
        return new PDGEdge(source, target, PDGDependenceType.DATA_DEF_USE, value);
    }

    /**
     * Creates a data dependence edge feeding one operand of a phi.
     * @param source the node defining the incoming operand
     * @param target the phi node
     * @param value the incoming value
     * @param blockLabel the predecessor block the operand arrives from
     * @return the phi edge
     */
    public static PDGEdge phiEdge(PDGNode source, PDGNode target, SSAValue value, String blockLabel)
    {
        return new PDGEdge(source, target, PDGDependenceType.DATA_PHI, blockLabel, value, false);
    }

    /**
     * @return true if the dependence type is a control dependence
     */
    public boolean isControlDependence()
    {
        return type.isControlDependence();
    }

    /**
     * @return true if the dependence type is a data dependence
     */
    public boolean isDataDependence()
    {
        return type.isDataDependence();
    }

    /**
     * @return true if the dependence type crosses a procedure boundary
     */
    public boolean isInterprocedural()
    {
        return type.isInterproceduralEdge();
    }

    /**
     * @return true if a flowing value is attached to this edge
     */
    public boolean hasDependentValue()
    {
        return dependentValue != null;
    }

    /**
     * @return true if a non-empty label is attached to this edge
     */
    public boolean hasLabel()
    {
        return label != null && !label.isEmpty();
    }

    /**
     * @return the name of the flowing value, or null if no value is attached
     */
    public String getVariable()
    {
        if (dependentValue != null)
        {
            return dependentValue.getName();
        }
        return null;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PDGEdge pdgEdge = (PDGEdge) o;
        return source.getId() == pdgEdge.source.getId()
            && target.getId() == pdgEdge.target.getId()
            && type == pdgEdge.type;
    }

    @Override
    public int hashCode()
    {
        int result = source.getId();
        result = 31 * result + target.getId();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("PDGEdge[");
        sb.append(source.getId());
        sb.append(" --[").append(type.getShortName()).append("]--> ");
        sb.append(target.getId());
        if (hasLabel())
        {
            sb.append(" \"").append(label).append("\"");
        }
        if (hasDependentValue())
        {
            sb.append(" via ").append(dependentValue.getName());
        }
        sb.append("]");
        return sb.toString();
    }
}
