package com.tonic.analysis.pdg.slice;

import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.*;

/**
 * The nodes and edges reached by a program slice, together with the criterion it
 * was taken from.
 */
public class SliceResult
{

    /**
     * Direction a slice was taken in.
     */
    public enum SliceType
    {
        /**
         * Walked against the dependence edges, reaching everything the
         * criterion depends on.
         */
        BACKWARD,
        /**
         * Walked along the dependence edges, reaching everything the criterion
         * can affect.
         */
        FORWARD,
        /**
         * The intersection of a backward and a forward slice, as produced by
         * {@link SliceResult#intersect}.
         */
        CHOP
    }

    private final SliceType type;
    private final Set<PDGNode> criterion;
    private final Set<PDGNode> nodes;
    private final Set<PDGEdge> edges;

    /**
     * Creates an empty slice over a copy of the given criterion set.
     * @param type the slice direction
     * @param criterion the nodes the slice starts from
     */
    public SliceResult(SliceType type, Set<PDGNode> criterion)
    {
        this.type = type;
        this.criterion = new LinkedHashSet<>(criterion);
        this.nodes = new LinkedHashSet<>();
        this.edges = new LinkedHashSet<>();
    }

    /**
     * Creates an empty slice over a single criterion node.
     * @param type the slice direction
     * @param singleCriterion the node the slice starts from
     */
    public SliceResult(SliceType type, PDGNode singleCriterion)
    {
        this.type = type;
        this.criterion = new LinkedHashSet<>();
        this.criterion.add(singleCriterion);
        this.nodes = new LinkedHashSet<>();
        this.edges = new LinkedHashSet<>();
    }

    /**
     * @return the type
     */
    public SliceType getType()
    {
        return type;
    }

    /**
     * @return the criterion
     */
    public Set<PDGNode> getCriterion()
    {
        return criterion;
    }

    /**
     * Adds a node to the slice.
     * @param node the node to add
     */
    public void addNode(PDGNode node)
    {
        nodes.add(node);
    }

    /**
     * Adds an edge to the slice.
     * @param edge the edge to add
     */
    public void addEdge(PDGEdge edge)
    {
        edges.add(edge);
    }

    /**
     * Adds several nodes to the slice.
     * @param nodesToAdd the nodes to add
     */
    public void addNodes(Collection<PDGNode> nodesToAdd)
    {
        nodes.addAll(nodesToAdd);
    }

    /**
     * Adds several edges to the slice.
     * @param edgesToAdd the edges to add
     */
    public void addEdges(Collection<PDGEdge> edgesToAdd)
    {
        edges.addAll(edgesToAdd);
    }

    /**
     * @param node the node to test
     * @return true if the node is in the slice
     */
    public boolean contains(PDGNode node)
    {
        return nodes.contains(node);
    }

    /**
     * Tests whether an instruction backs one of the sliced nodes, by identity.
     * @param instruction the instruction to look for
     * @return true if a sliced node wraps it
     */
    public boolean containsInstruction(IRInstruction instruction)
    {
        for (PDGNode node : nodes)
        {
            if (node instanceof PDGInstructionNode)
            {
                PDGInstructionNode instrNode =
                    (PDGInstructionNode) node;
                if (instrNode.getInstruction() == instruction)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collects the blocks the sliced nodes belong to, skipping nodes with no
     * block.
     * @return the blocks, in insertion order
     */
    public Set<IRBlock> getAffectedBlocks()
    {
        Set<IRBlock> blocks = new LinkedHashSet<>();
        for (PDGNode node : nodes)
        {
            IRBlock block = node.getBlock();
            if (block != null)
            {
                blocks.add(block);
            }
        }
        return blocks;
    }

    /**
     * Collects the instructions behind the sliced instruction nodes.
     * @return the instructions, in insertion order
     */
    public Set<IRInstruction> getInstructions()
    {
        Set<IRInstruction> instructions = new LinkedHashSet<>();
        for (PDGNode node : nodes)
        {
            if (node instanceof PDGInstructionNode)
            {
                PDGInstructionNode instrNode =
                    (PDGInstructionNode) node;
                instructions.add(instrNode.getInstruction());
            }
        }
        return instructions;
    }

    /**
     * @return the number of sliced nodes
     */
    public int getNodeCount()
    {
        return nodes.size();
    }

    /**
     * @return the number of sliced nodes
     */
    public int getSize()
    {
        return nodes.size();
    }

    /**
     * @param node the node to test
     * @return true if the node is in the slice
     */
    public boolean containsNode(PDGNode node)
    {
        return nodes.contains(node);
    }

    /**
     * @return the number of sliced edges
     */
    public int getEdgeCount()
    {
        return edges.size();
    }

    /**
     * @return the number of distinct blocks the sliced nodes sit in
     */
    public int getBlockCount()
    {
        return getAffectedBlocks().size();
    }

    /**
     * @return true if the slice holds no nodes
     */
    public boolean isEmpty()
    {
        return nodes.isEmpty();
    }

    /**
     * @return an unmodifiable view of the sliced nodes
     */
    public Set<PDGNode> getNodes()
    {
        return Collections.unmodifiableSet(nodes);
    }

    /**
     * @return an unmodifiable view of the sliced edges
     */
    public Set<PDGEdge> getEdges()
    {
        return Collections.unmodifiableSet(edges);
    }

    /**
     * Builds the chop of two slices - the nodes in both, with this slice's edges
     * whose endpoints both survive.
     * @param other the slice to intersect with
     * @return a CHOP slice over the combined criteria
     */
    public SliceResult intersect(SliceResult other)
    {
        Set<PDGNode> combinedCriterion = new LinkedHashSet<>(criterion);
        combinedCriterion.addAll(other.criterion);

        SliceResult result = new SliceResult(SliceType.CHOP, combinedCriterion);
        for (PDGNode node : nodes)
        {
            if (other.contains(node))
            {
                result.addNode(node);
            }
        }

        for (PDGEdge edge : edges)
        {
            if (result.contains(edge.getSource()) && result.contains(edge.getTarget()))
            {
                result.addEdge(edge);
            }
        }

        return result;
    }

    /**
     * Merges two slices, keeping this slice's type and the union of both
     * criteria, nodes and edges.
     * @param other the slice to merge in
     * @return the merged slice
     */
    public SliceResult union(SliceResult other)
    {
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
    public String toString()
    {
        return String.format("SliceResult[%s: %d nodes, %d edges, %d blocks, criterion=%d]",
            type, getNodeCount(), getEdgeCount(), getBlockCount(), criterion.size());
    }
}
