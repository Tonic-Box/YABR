package com.tonic.analysis.cpg.node;

import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public abstract class CPGNode {

    private final long id;
    private final CPGNodeType nodeType;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    private final Set<CPGEdge> outgoingEdges = new LinkedHashSet<>();
    private final Set<CPGEdge> incomingEdges = new LinkedHashSet<>();

    @Setter
    private boolean tainted;
    @Setter
    private String taintLabel;

    protected CPGNode(long id, CPGNodeType nodeType) {
        this.id = id;
        this.nodeType = nodeType;
    }

    public abstract String getLabel();

    public abstract <T> T getUnderlying();

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    public void removeProperty(String key) {
        properties.remove(key);
    }

    public void addOutgoingEdge(CPGEdge edge) {
        outgoingEdges.add(edge);
    }

    public void addIncomingEdge(CPGEdge edge) {
        incomingEdges.add(edge);
    }

    public void removeOutgoingEdge(CPGEdge edge) {
        outgoingEdges.remove(edge);
    }

    public void removeIncomingEdge(CPGEdge edge) {
        incomingEdges.remove(edge);
    }

    public Set<CPGEdge> getEdges(CPGEdgeType type) {
        Set<CPGEdge> result = new LinkedHashSet<>();
        for (CPGEdge edge : outgoingEdges) {
            if (edge.getType() == type) {
                result.add(edge);
            }
        }
        for (CPGEdge edge : incomingEdges) {
            if (edge.getType() == type) {
                result.add(edge);
            }
        }
        return result;
    }

    public Set<CPGEdge> getOutgoingEdges(CPGEdgeType type) {
        Set<CPGEdge> result = new LinkedHashSet<>();
        for (CPGEdge edge : outgoingEdges) {
            if (edge.getType() == type) {
                result.add(edge);
            }
        }
        return result;
    }

    public Set<CPGEdge> getIncomingEdges(CPGEdgeType type) {
        Set<CPGEdge> result = new LinkedHashSet<>();
        for (CPGEdge edge : incomingEdges) {
            if (edge.getType() == type) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<CPGNode> cfgSuccessors() {
        List<CPGNode> successors = new ArrayList<>();
        for (CPGEdge edge : outgoingEdges) {
            if (edge.getType().isCFGEdge()) {
                successors.add(edge.getTarget());
            }
        }
        return successors;
    }

    public List<CPGNode> cfgPredecessors() {
        List<CPGNode> predecessors = new ArrayList<>();
        for (CPGEdge edge : incomingEdges) {
            if (edge.getType().isCFGEdge()) {
                predecessors.add(edge.getSource());
            }
        }
        return predecessors;
    }

    public Optional<CPGNode> astParent() {
        for (CPGEdge edge : incomingEdges) {
            if (edge.getType() == CPGEdgeType.AST_CHILD) {
                return Optional.of(edge.getSource());
            }
        }
        return Optional.empty();
    }

    public List<CPGNode> astChildren() {
        List<CPGNode> children = new ArrayList<>();
        for (CPGEdge edge : outgoingEdges) {
            if (edge.getType() == CPGEdgeType.AST_CHILD) {
                children.add(edge.getTarget());
            }
        }
        return children;
    }

    public Set<CPGNode> dataDependencies() {
        Set<CPGNode> deps = new LinkedHashSet<>();
        for (CPGEdge edge : incomingEdges) {
            if (edge.getType().isDataFlowEdge()) {
                deps.add(edge.getSource());
            }
        }
        return deps;
    }

    public Set<CPGNode> controlDependencies() {
        Set<CPGNode> deps = new LinkedHashSet<>();
        for (CPGEdge edge : incomingEdges) {
            if (edge.getType().isControlDependenceEdge()) {
                deps.add(edge.getSource());
            }
        }
        return deps;
    }

    public int getInDegree() {
        return incomingEdges.size();
    }

    public int getOutDegree() {
        return outgoingEdges.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CPGNode cpgNode = (CPGNode) o;
        return id == cpgNode.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("CPGNode[%d: %s - %s]", id, nodeType.getShortName(), getLabel());
    }
}
