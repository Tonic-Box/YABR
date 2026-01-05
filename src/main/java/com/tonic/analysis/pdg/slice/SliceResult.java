package com.tonic.analysis.pdg.slice;

import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;
import lombok.Getter;

import java.util.*;

@Getter
public class SliceResult {

    public enum SliceType {
        BACKWARD,
        FORWARD,
        CHOP
    }

    private final SliceType type;
    private final Set<PDGNode> criterion;
    private final Set<PDGNode> nodes;
    private final Set<PDGEdge> edges;

    public SliceResult(SliceType type, Set<PDGNode> criterion) {
        this.type = type;
        this.criterion = new LinkedHashSet<>(criterion);
        this.nodes = new LinkedHashSet<>();
        this.edges = new LinkedHashSet<>();
    }

    public SliceResult(SliceType type, PDGNode singleCriterion) {
        this.type = type;
        this.criterion = new LinkedHashSet<>();
        this.criterion.add(singleCriterion);
        this.nodes = new LinkedHashSet<>();
        this.edges = new LinkedHashSet<>();
    }

    public void addNode(PDGNode node) {
        nodes.add(node);
    }

    public void addEdge(PDGEdge edge) {
        edges.add(edge);
    }

    public void addNodes(Collection<PDGNode> nodesToAdd) {
        nodes.addAll(nodesToAdd);
    }

    public void addEdges(Collection<PDGEdge> edgesToAdd) {
        edges.addAll(edgesToAdd);
    }

    public boolean contains(PDGNode node) {
        return nodes.contains(node);
    }

    public boolean containsInstruction(IRInstruction instruction) {
        for (PDGNode node : nodes) {
            if (node instanceof com.tonic.analysis.pdg.node.PDGInstructionNode) {
                com.tonic.analysis.pdg.node.PDGInstructionNode instrNode =
                    (com.tonic.analysis.pdg.node.PDGInstructionNode) node;
                if (instrNode.getInstruction() == instruction) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<IRBlock> getAffectedBlocks() {
        Set<IRBlock> blocks = new LinkedHashSet<>();
        for (PDGNode node : nodes) {
            IRBlock block = node.getBlock();
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    public Set<IRInstruction> getInstructions() {
        Set<IRInstruction> instructions = new LinkedHashSet<>();
        for (PDGNode node : nodes) {
            if (node instanceof com.tonic.analysis.pdg.node.PDGInstructionNode) {
                com.tonic.analysis.pdg.node.PDGInstructionNode instrNode =
                    (com.tonic.analysis.pdg.node.PDGInstructionNode) node;
                instructions.add(instrNode.getInstruction());
            }
        }
        return instructions;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getSize() {
        return nodes.size();
    }

    public boolean containsNode(PDGNode node) {
        return nodes.contains(node);
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public int getBlockCount() {
        return getAffectedBlocks().size();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public Set<PDGNode> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    public Set<PDGEdge> getEdges() {
        return Collections.unmodifiableSet(edges);
    }

    public SliceResult intersect(SliceResult other) {
        Set<PDGNode> combinedCriterion = new LinkedHashSet<>(criterion);
        combinedCriterion.addAll(other.criterion);

        SliceResult result = new SliceResult(SliceType.CHOP, combinedCriterion);
        for (PDGNode node : nodes) {
            if (other.contains(node)) {
                result.addNode(node);
            }
        }

        for (PDGEdge edge : edges) {
            if (result.contains(edge.getSource()) && result.contains(edge.getTarget())) {
                result.addEdge(edge);
            }
        }

        return result;
    }

    public SliceResult union(SliceResult other) {
        Set<PDGNode> combinedCriterion = new LinkedHashSet<>(criterion);
        combinedCriterion.addAll(other.criterion);

        SliceResult result = new SliceResult(type, combinedCriterion);
        result.addNodes(nodes);
        result.addNodes(other.nodes);
        result.addEdges(edges);
        result.addEdges(other.edges);
        return result;
    }

    @Override
    public String toString() {
        return String.format("SliceResult[%s: %d nodes, %d edges, %d blocks, criterion=%d]",
            type, getNodeCount(), getEdgeCount(), getBlockCount(), criterion.size());
    }
}
