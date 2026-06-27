package com.tonic.analysis.cpg.edge;

import com.tonic.analysis.cpg.node.CPGNode;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class CPGEdge {

    private final CPGNode source;
    private final CPGNode target;
    private final CPGEdgeType type;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    @Setter
    private boolean tainted;

    public CPGEdge(CPGNode source, CPGNode target, CPGEdgeType type) {
        this.source = source;
        this.target = target;
        this.type = type;
    }

    public CPGEdge(CPGNode source, CPGNode target, CPGEdgeType type, Map<String, Object> properties) {
        this.source = source;
        this.target = target;
        this.type = type;
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    public String getLabel() {
        return type.getShortName();
    }

    public boolean isASTEdge() {
        return type.isASTEdge();
    }

    public boolean isCFGEdge() {
        return type.isCFGEdge();
    }

    public boolean isDataFlowEdge() {
        return type.isDataFlowEdge();
    }

    public boolean isControlDependenceEdge() {
        return type.isControlDependenceEdge();
    }

    public boolean isCallGraphEdge() {
        return type.isCallGraphEdge();
    }

    public boolean isInterproceduralEdge() {
        return type.isInterproceduralEdge();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CPGEdge cpgEdge = (CPGEdge) o;
        return source.getId() == cpgEdge.source.getId()
            && target.getId() == cpgEdge.target.getId()
            && type == cpgEdge.type;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(source.getId());
        result = 31 * result + Long.hashCode(target.getId());
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("CPGEdge[%d --[%s]--> %d]",
            source.getId(), type.getShortName(), target.getId());
    }
}
