package com.tonic.analysis.cpg.node;

import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;

import java.util.*;

/**
 * Base class for CPG nodes: an id, a node type, a property map, and the incident edges;
 * identity is the id alone.
 */
public abstract class CPGNode
{

    private final long id;
    private final CPGNodeType nodeType;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    private final Set<CPGEdge> outgoingEdges = new LinkedHashSet<>();
    private final Set<CPGEdge> incomingEdges = new LinkedHashSet<>();

    private boolean tainted;
    private String taintLabel;

    protected CPGNode(long id, CPGNodeType nodeType)
    {
        this.id = id;
        this.nodeType = nodeType;
    }

    /**
     * @return the id
     */
    public long getId()
    {
        return id;
    }

    /**
     * @return the node type
     */
    public CPGNodeType getNodeType()
    {
        return nodeType;
    }

    /**
     * @return the properties
     */
    public Map<String, Object> getProperties()
    {
        return properties;
    }

    /**
     * @return the outgoing edges
     */
    public Set<CPGEdge> getOutgoingEdges()
    {
        return outgoingEdges;
    }

    /**
     * @return the incoming edges
     */
    public Set<CPGEdge> getIncomingEdges()
    {
        return incomingEdges;
    }

    /**
     * @return whether tainted
     */
    public boolean isTainted()
    {
        return tainted;
    }

    /**
     * @return the taint label
     */
    public String getTaintLabel()
    {
        return taintLabel;
    }

    /**
     * Marks or clears this node as taint-reached.
     * @param tainted whether the node is tainted
     */
    public void setTainted(boolean tainted)
    {
        this.tainted = tainted;
    }

    /**
     * Records which taint source reached this node.
     * @param taintLabel the taint label
     */
    public void setTaintLabel(String taintLabel)
    {
        this.taintLabel = taintLabel;
    }

    /**
     * @return a short human-readable display label
     */
    public abstract String getLabel();

    /**
     * @param <T> the expected underlying type
     * @return the wrapped IR or analysis object
     */
    public abstract <T> T getUnderlying();

    /**
     * Reads a property value.
     * @param key the property key
     * @return the value, or null if absent
     */
    public Object getProperty(String key)
    {
        return properties.get(key);
    }

    /**
     * Sets a property value.
     * @param key the property key
     * @param value the value to store
     */
    public void setProperty(String key, Object value)
    {
        properties.put(key, value);
    }

    /**
     * Tests whether a property is present.
     * @param key the property key
     * @return whether the property exists
     */
    public boolean hasProperty(String key)
    {
        return properties.containsKey(key);
    }

    /**
     * Removes a property.
     * @param key the property key
     */
    public void removeProperty(String key)
    {
        properties.remove(key);
    }

    /**
     * Registers an edge leaving this node.
     * @param edge the edge to add
     */
    public void addOutgoingEdge(CPGEdge edge)
    {
        outgoingEdges.add(edge);
    }

    /**
     * Registers an edge entering this node.
     * @param edge the edge to add
     */
    public void addIncomingEdge(CPGEdge edge)
    {
        incomingEdges.add(edge);
    }

    /**
     * Unregisters an edge leaving this node.
     * @param edge the edge to remove
     */
    public void removeOutgoingEdge(CPGEdge edge)
    {
        outgoingEdges.remove(edge);
    }

    /**
     * Unregisters an edge entering this node.
     * @param edge the edge to remove
     */
    public void removeIncomingEdge(CPGEdge edge)
    {
        incomingEdges.remove(edge);
    }

    /**
     * Collects incident edges of one type in either direction.
     * @param type the edge type
     * @return the matching edges
     */
    public Set<CPGEdge> getEdges(CPGEdgeType type)
    {
        Set<CPGEdge> result = new LinkedHashSet<>();
        for (CPGEdge edge : outgoingEdges)
        {
            if (edge.getType() == type)
            {
                result.add(edge);
            }
        }
        for (CPGEdge edge : incomingEdges)
        {
            if (edge.getType() == type)
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Collects outgoing edges of one type.
     * @param type the edge type
     * @return the matching edges
     */
    public Set<CPGEdge> getOutgoingEdges(CPGEdgeType type)
    {
        Set<CPGEdge> result = new LinkedHashSet<>();
        for (CPGEdge edge : outgoingEdges)
        {
            if (edge.getType() == type)
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Collects incoming edges of one type.
     * @param type the edge type
     * @return the matching edges
     */
    public Set<CPGEdge> getIncomingEdges(CPGEdgeType type)
    {
        Set<CPGEdge> result = new LinkedHashSet<>();
        for (CPGEdge edge : incomingEdges)
        {
            if (edge.getType() == type)
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Follows outgoing control-flow edges.
     * @return the CFG successor nodes
     */
    public List<CPGNode> cfgSuccessors()
    {
        List<CPGNode> successors = new ArrayList<>();
        for (CPGEdge edge : outgoingEdges)
        {
            if (edge.getType().isCFGEdge())
            {
                successors.add(edge.getTarget());
            }
        }
        return successors;
    }

    /**
     * Follows incoming control-flow edges.
     * @return the CFG predecessor nodes
     */
    public List<CPGNode> cfgPredecessors()
    {
        List<CPGNode> predecessors = new ArrayList<>();
        for (CPGEdge edge : incomingEdges)
        {
            if (edge.getType().isCFGEdge())
            {
                predecessors.add(edge.getSource());
            }
        }
        return predecessors;
    }

    /**
     * Follows the incoming AST child edge to the parent.
     * @return the AST parent node, if any
     */
    public Optional<CPGNode> astParent()
    {
        for (CPGEdge edge : incomingEdges)
        {
            if (edge.getType() == CPGEdgeType.AST_CHILD)
            {
                return Optional.of(edge.getSource());
            }
        }
        return Optional.empty();
    }

    /**
     * Follows outgoing AST child edges.
     * @return the AST child nodes
     */
    public List<CPGNode> astChildren()
    {
        List<CPGNode> children = new ArrayList<>();
        for (CPGEdge edge : outgoingEdges)
        {
            if (edge.getType() == CPGEdgeType.AST_CHILD)
            {
                children.add(edge.getTarget());
            }
        }
        return children;
    }

    /**
     * Follows incoming data-flow edges to the values this node depends on.
     * @return the data dependency sources
     */
    public Set<CPGNode> dataDependencies()
    {
        Set<CPGNode> deps = new LinkedHashSet<>();
        for (CPGEdge edge : incomingEdges)
        {
            if (edge.getType().isDataFlowEdge())
            {
                deps.add(edge.getSource());
            }
        }
        return deps;
    }

    /**
     * Follows incoming control-dependence edges to the nodes controlling this one.
     * @return the control dependency sources
     */
    public Set<CPGNode> controlDependencies()
    {
        Set<CPGNode> deps = new LinkedHashSet<>();
        for (CPGEdge edge : incomingEdges)
        {
            if (edge.getType().isControlDependenceEdge())
            {
                deps.add(edge.getSource());
            }
        }
        return deps;
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
        CPGNode cpgNode = (CPGNode) o;
        return id == cpgNode.id;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(id);
    }

    @Override
    public String toString()
    {
        return String.format("CPGNode[%d: %s - %s]", id, nodeType.getShortName(), getLabel());
    }
}
