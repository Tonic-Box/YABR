package com.tonic.analysis.pdg.node;

import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base node of a program dependence graph, identified by an id and holding its own
 * incoming and outgoing dependence edges plus optional taint marking.
 */
public abstract class PDGNode
{

    private final int id;
    private final PDGNodeType type;
    private IRBlock block;

    private final List<PDGEdge> incomingEdges = new ArrayList<>();
    private final List<PDGEdge> outgoingEdges = new ArrayList<>();

    private boolean tainted;
    private String taintLabel;

    protected PDGNode(int id, PDGNodeType type, IRBlock block)
    {
        this.id = id;
        this.type = type;
        this.block = block;
    }

    /**
     * @return the id
     */
    public int getId()
    {
        return id;
    }

    /**
     * @return the type
     */
    public PDGNodeType getType()
    {
        return type;
    }

    /**
     * @return the block
     */
    public IRBlock getBlock()
    {
        return block;
    }

    /**
     * Sets the block this node belongs to.
     * @param block the owning block, may be null for region nodes
     */
    public void setBlock(IRBlock block)
    {
        this.block = block;
    }

    /**
     * @return whether tainted
     */
    public boolean isTainted()
    {
        return tainted;
    }

    /**
     * Marks or clears this node as carrying tainted data.
     * @param tainted whether the node is tainted
     */
    public void setTainted(boolean tainted)
    {
        this.tainted = tainted;
    }

    /**
     * @return the taint label
     */
    public String getTaintLabel()
    {
        return taintLabel;
    }

    /**
     * Sets the label describing where the taint on this node came from.
     * @param taintLabel the taint label
     */
    public void setTaintLabel(String taintLabel)
    {
        this.taintLabel = taintLabel;
    }

    /**
     * @return a short human-readable description of what this node represents
     */
    public abstract String getLabel();

    /**
     * @return the values this node reads
     */
    public abstract List<Value> getUsedValues();

    /**
     * @return the value this node defines, or null if it defines none
     */
    public abstract SSAValue getDefinedValue();

    /**
     * Records an edge arriving at this node, ignoring duplicates.
     * @param edge the incoming edge
     */
    public void addIncomingEdge(PDGEdge edge)
    {
        if (!incomingEdges.contains(edge))
        {
            incomingEdges.add(edge);
        }
    }

    /**
     * Records an edge leaving this node, ignoring duplicates.
     * @param edge the outgoing edge
     */
    public void addOutgoingEdge(PDGEdge edge)
    {
        if (!outgoingEdges.contains(edge))
        {
            outgoingEdges.add(edge);
        }
    }

    /**
     * Drops an edge arriving at this node.
     * @param edge the incoming edge to remove
     */
    public void removeIncomingEdge(PDGEdge edge)
    {
        incomingEdges.remove(edge);
    }

    /**
     * Drops an edge leaving this node.
     * @param edge the outgoing edge to remove
     */
    public void removeOutgoingEdge(PDGEdge edge)
    {
        outgoingEdges.remove(edge);
    }

    /**
     * @return an unmodifiable view of the edges arriving at this node
     */
    public List<PDGEdge> getIncomingEdges()
    {
        return Collections.unmodifiableList(incomingEdges);
    }

    /**
     * @return an unmodifiable view of the edges leaving this node
     */
    public List<PDGEdge> getOutgoingEdges()
    {
        return Collections.unmodifiableList(outgoingEdges);
    }

    /**
     * @return the source node of each incoming edge, one entry per edge
     */
    public List<PDGNode> getPredecessors()
    {
        List<PDGNode> predecessors = new ArrayList<>();
        for (PDGEdge edge : incomingEdges)
        {
            predecessors.add(edge.getSource());
        }
        return predecessors;
    }

    /**
     * @return the target node of each outgoing edge, one entry per edge
     */
    public List<PDGNode> getSuccessors()
    {
        List<PDGNode> successors = new ArrayList<>();
        for (PDGEdge edge : outgoingEdges)
        {
            successors.add(edge.getTarget());
        }
        return successors;
    }

    /**
     * @return the incoming edges that are control dependences
     */
    public List<PDGEdge> getControlDependenceEdges()
    {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : incomingEdges)
        {
            if (edge.getType().isControlDependence())
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * @return the incoming edges that are data dependences
     */
    public List<PDGEdge> getDataDependenceEdges()
    {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : incomingEdges)
        {
            if (edge.getType().isDataDependence())
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * @return whether any edge arrives at this node
     */
    public boolean hasIncomingEdges()
    {
        return !incomingEdges.isEmpty();
    }

    /**
     * @return whether any edge leaves this node
     */
    public boolean hasOutgoingEdges()
    {
        return !outgoingEdges.isEmpty();
    }

    /**
     * @return the number of incoming edges
     */
    public int getInDegree()
    {
        return incomingEdges.size();
    }

    /**
     * @return the number of outgoing edges
     */
    public int getOutDegree()
    {
        return outgoingEdges.size();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PDGNode pdgNode = (PDGNode) o;
        return id == pdgNode.id;
    }

    @Override
    public int hashCode()
    {
        return Integer.hashCode(id);
    }

    @Override
    public String toString()
    {
        return String.format("PDGNode[%d: %s - %s]", id, type.getDisplayName(), getLabel());
    }
}
