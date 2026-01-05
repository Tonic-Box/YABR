package com.tonic.analysis.pdg.node;

import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public abstract class PDGNode {

    private final int id;
    private final PDGNodeType type;
    @Setter
    private IRBlock block;

    private final List<PDGEdge> incomingEdges = new ArrayList<>();
    private final List<PDGEdge> outgoingEdges = new ArrayList<>();

    @Setter
    private boolean tainted;
    @Setter
    private String taintLabel;

    protected PDGNode(int id, PDGNodeType type, IRBlock block) {
        this.id = id;
        this.type = type;
        this.block = block;
    }

    public abstract String getLabel();

    public abstract List<Value> getUsedValues();

    public abstract SSAValue getDefinedValue();

    public void addIncomingEdge(PDGEdge edge) {
        if (!incomingEdges.contains(edge)) {
            incomingEdges.add(edge);
        }
    }

    public void addOutgoingEdge(PDGEdge edge) {
        if (!outgoingEdges.contains(edge)) {
            outgoingEdges.add(edge);
        }
    }

    public void removeIncomingEdge(PDGEdge edge) {
        incomingEdges.remove(edge);
    }

    public void removeOutgoingEdge(PDGEdge edge) {
        outgoingEdges.remove(edge);
    }

    public List<PDGEdge> getIncomingEdges() {
        return Collections.unmodifiableList(incomingEdges);
    }

    public List<PDGEdge> getOutgoingEdges() {
        return Collections.unmodifiableList(outgoingEdges);
    }

    public List<PDGNode> getPredecessors() {
        List<PDGNode> predecessors = new ArrayList<>();
        for (PDGEdge edge : incomingEdges) {
            predecessors.add(edge.getSource());
        }
        return predecessors;
    }

    public List<PDGNode> getSuccessors() {
        List<PDGNode> successors = new ArrayList<>();
        for (PDGEdge edge : outgoingEdges) {
            successors.add(edge.getTarget());
        }
        return successors;
    }

    public List<PDGEdge> getControlDependenceEdges() {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : incomingEdges) {
            if (edge.getType().isControlDependence()) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<PDGEdge> getDataDependenceEdges() {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : incomingEdges) {
            if (edge.getType().isDataDependence()) {
                result.add(edge);
            }
        }
        return result;
    }

    public boolean hasIncomingEdges() {
        return !incomingEdges.isEmpty();
    }

    public boolean hasOutgoingEdges() {
        return !outgoingEdges.isEmpty();
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
        PDGNode pdgNode = (PDGNode) o;
        return id == pdgNode.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return String.format("PDGNode[%d: %s - %s]", id, type.getDisplayName(), getLabel());
    }
}
