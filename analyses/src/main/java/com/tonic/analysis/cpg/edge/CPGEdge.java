package com.tonic.analysis.cpg.edge;

import com.tonic.analysis.cpg.node.CPGNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Directed, typed edge between two CPG nodes; identity is (source id, target id, type).
 */
public class CPGEdge
{

    private final CPGNode source;
    private final CPGNode target;
    private final CPGEdgeType type;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    private boolean tainted;

    /**
     * Creates an edge of the given type.
     * @param source the edge source
     * @param target the edge target
     * @param type the edge type
     */
    public CPGEdge(CPGNode source, CPGNode target, CPGEdgeType type)
    {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    /**
     * Creates an edge of the given type carrying the supplied properties.
     * @param source the edge source
     * @param target the edge target
     * @param type the edge type
     * @param properties initial edge properties, may be null
     */
    public CPGEdge(CPGNode source, CPGNode target, CPGEdgeType type, Map<String, Object> properties)
    {
        this.source = source;
        this.target = target;
        this.type = type;
        if (properties != null)
        {
            this.properties.putAll(properties);
        }
    }

    /**
     * @return the source
     */
    public CPGNode getSource()
    {
        return source;
    }

    /**
     * @return the target
     */
    public CPGNode getTarget()
    {
        return target;
    }

    /**
     * @return the type
     */
    public CPGEdgeType getType()
    {
        return type;
    }

    /**
     * @return the properties
     */
    public Map<String, Object> getProperties()
    {
        return properties;
    }

    /**
     * @return whether tainted
     */
    public boolean isTainted()
    {
        return tainted;
    }

    /**
     * Marks or clears this edge as part of a taint flow.
     * @param tainted whether the edge is tainted
     */
    public void setTainted(boolean tainted)
    {
        this.tainted = tainted;
    }

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
     * @return the edge type's short display name
     */
    public String getLabel()
    {
        return type.getShortName();
    }

    /**
     * @return whether this is an AST structure edge
     */
    public boolean isASTEdge()
    {
        return type.isASTEdge();
    }

    /**
     * @return whether this is a control-flow edge
     */
    public boolean isCFGEdge()
    {
        return type.isCFGEdge();
    }

    /**
     * @return whether this is a data-flow edge
     */
    public boolean isDataFlowEdge()
    {
        return type.isDataFlowEdge();
    }

    /**
     * @return whether this is a control-dependence edge
     */
    public boolean isControlDependenceEdge()
    {
        return type.isControlDependenceEdge();
    }

    /**
     * @return whether this is a call-graph edge
     */
    public boolean isCallGraphEdge()
    {
        return type.isCallGraphEdge();
    }

    /**
     * @return whether this is an interprocedural parameter or summary edge
     */
    public boolean isInterproceduralEdge()
    {
        return type.isInterproceduralEdge();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CPGEdge cpgEdge = (CPGEdge) o;
        return source.getId() == cpgEdge.source.getId()
            && target.getId() == cpgEdge.target.getId()
            && type == cpgEdge.type;
    }

    @Override
    public int hashCode()
    {
        int result = Long.hashCode(source.getId());
        result = 31 * result + Long.hashCode(target.getId());
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return String.format("CPGEdge[%d --[%s]--> %d]", source.getId(), type.getShortName(), target.getId());
    }
}
