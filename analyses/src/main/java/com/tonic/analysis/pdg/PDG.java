package com.tonic.analysis.pdg;

import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.pdg.node.PDGRegionNode;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import lombok.Getter;

import java.util.*;

@Getter
public class PDG {

    private final IRMethod method;
    private final String methodName;
    private PDGRegionNode entryNode;
    private PDGRegionNode exitNode;

    private final List<PDGNode> nodes = new ArrayList<>();
    private final List<PDGEdge> edges = new ArrayList<>();

    private final Map<IRInstruction, PDGInstructionNode> instructionToNode = new HashMap<>();
    private final Map<SSAValue, PDGNode> valueToNode = new HashMap<>();
    private final Map<IRBlock, List<PDGNode>> blockToNodes = new HashMap<>();

    private int nextNodeId = 0;

    public PDG(IRMethod method) {
        this.method = method;
        this.methodName = method.getName();
    }

    public int allocateNodeId() {
        return nextNodeId++;
    }

    public void setEntryNode(PDGRegionNode entryNode) {
        this.entryNode = entryNode;
    }

    public void setExitNode(PDGRegionNode exitNode) {
        this.exitNode = exitNode;
    }

    public void addNode(PDGNode node) {
        if (!nodes.contains(node)) {
            nodes.add(node);

            if (node instanceof PDGInstructionNode) {
                PDGInstructionNode instrNode = (PDGInstructionNode) node;
                instructionToNode.put(instrNode.getInstruction(), instrNode);
                SSAValue defined = instrNode.getDefinedValue();
                if (defined != null) {
                    valueToNode.put(defined, instrNode);
                }
            }

            IRBlock block = node.getBlock();
            if (block != null) {
                blockToNodes.computeIfAbsent(block, k -> new ArrayList<>()).add(node);
            }
        }
    }

    public void addEdge(PDGEdge edge) {
        if (!edges.contains(edge)) {
            edges.add(edge);
            edge.getSource().addOutgoingEdge(edge);
            edge.getTarget().addIncomingEdge(edge);
        }
    }

    public void addEdge(PDGNode source, PDGNode target, PDGDependenceType type) {
        addEdge(new PDGEdge(source, target, type));
    }

    public void addEdge(PDGNode source, PDGNode target, PDGDependenceType type, SSAValue dependentValue) {
        addEdge(new PDGEdge(source, target, type, dependentValue));
    }

    public void removeEdge(PDGEdge edge) {
        edges.remove(edge);
        edge.getSource().removeOutgoingEdge(edge);
        edge.getTarget().removeIncomingEdge(edge);
    }

    public PDGNode getNodeForInstruction(IRInstruction instruction) {
        return instructionToNode.get(instruction);
    }

    public PDGNode getNodeForValue(SSAValue value) {
        return valueToNode.get(value);
    }

    public List<PDGNode> getNodesInBlock(IRBlock block) {
        return blockToNodes.getOrDefault(block, Collections.emptyList());
    }

    public List<PDGNode> getNodesByType(PDGNodeType type) {
        List<PDGNode> result = new ArrayList<>();
        for (PDGNode node : nodes) {
            if (node.getType() == type) {
                result.add(node);
            }
        }
        return result;
    }

    public List<PDGEdge> getEdgesByType(PDGDependenceType type) {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : edges) {
            if (edge.getType() == type) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<PDGEdge> getControlDependenceEdges() {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : edges) {
            if (edge.isControlDependence()) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<PDGEdge> getDataDependenceEdges() {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : edges) {
            if (edge.isDataDependence()) {
                result.add(edge);
            }
        }
        return result;
    }

    public Set<PDGNode> getControlDependentOn(PDGNode node) {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getOutgoingEdges()) {
            if (edge.isControlDependence()) {
                result.add(edge.getTarget());
            }
        }
        return result;
    }

    public Set<PDGNode> getControllingNodes(PDGNode node) {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getIncomingEdges()) {
            if (edge.isControlDependence()) {
                result.add(edge.getSource());
            }
        }
        return result;
    }

    public Set<PDGNode> getDataDependentOn(PDGNode node) {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getOutgoingEdges()) {
            if (edge.isDataDependence()) {
                result.add(edge.getTarget());
            }
        }
        return result;
    }

    public Set<PDGNode> getDataSources(PDGNode node) {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getIncomingEdges()) {
            if (edge.isDataDependence()) {
                result.add(edge.getSource());
            }
        }
        return result;
    }

    public Set<PDGNode> getAllDependentOn(PDGNode node) {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getOutgoingEdges()) {
            result.add(edge.getTarget());
        }
        return result;
    }

    public Set<PDGNode> getAllSources(PDGNode node) {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getIncomingEdges()) {
            result.add(edge.getSource());
        }
        return result;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public int getControlEdgeCount() {
        int count = 0;
        for (PDGEdge edge : edges) {
            if (edge.isControlDependence()) {
                count++;
            }
        }
        return count;
    }

    public int getDataEdgeCount() {
        int count = 0;
        for (PDGEdge edge : edges) {
            if (edge.isDataDependence()) {
                count++;
            }
        }
        return count;
    }

    public List<PDGNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<PDGEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public boolean hasEntryNode() {
        return entryNode != null;
    }

    public PDGNode getNode(int id) {
        for (PDGNode node : nodes) {
            if (node.getId() == id) {
                return node;
            }
        }
        return null;
    }

    public List<PDGNode> getNodesOfType(PDGNodeType type) {
        return getNodesByType(type);
    }

    public PDGNode getInstructionNode(IRInstruction instruction) {
        return getNodeForInstruction(instruction);
    }

    @Override
    public String toString() {
        return String.format("PDG[%s: %d nodes, %d edges (%d ctrl, %d data)]",
            methodName, getNodeCount(), getEdgeCount(),
            getControlEdgeCount(), getDataEdgeCount());
    }
}
