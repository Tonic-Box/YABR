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

import java.util.*;

/**
 * Program dependence graph for a single method, holding the control and data dependence
 * edges between its instruction nodes along with lookup indexes by instruction, value and block.
 */
public class PDG
{

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

    /**
     * Creates an empty graph for the given method.
     * @param method the method this graph describes
     */
    public PDG(IRMethod method)
    {
        this.method = method;
        this.methodName = method.getName();
    }

    /**
     * @return the method
     */
    public IRMethod getMethod()
    {
        return method;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the entry node
     */
    public PDGRegionNode getEntryNode()
    {
        return entryNode;
    }

    /**
     * @return the exit node
     */
    public PDGRegionNode getExitNode()
    {
        return exitNode;
    }

    /**
     * @return the instruction to node
     */
    public Map<IRInstruction, PDGInstructionNode> getInstructionToNode()
    {
        return instructionToNode;
    }

    /**
     * @return the value to node
     */
    public Map<SSAValue, PDGNode> getValueToNode()
    {
        return valueToNode;
    }

    /**
     * @return the block to nodes
     */
    public Map<IRBlock, List<PDGNode>> getBlockToNodes()
    {
        return blockToNodes;
    }

    /**
     * @return the next node id
     */
    public int getNextNodeId()
    {
        return nextNodeId;
    }

    /**
     * Hands out the next node id and advances the counter.
     * @return the allocated node id
     */
    public int allocateNodeId()
    {
        return nextNodeId++;
    }

    /**
     * Sets the region node all top-level statements are control dependent on.
     * @param entryNode the entry region node
     */
    public void setEntryNode(PDGRegionNode entryNode)
    {
        this.entryNode = entryNode;
    }

    /**
     * Sets the region node representing method exit.
     * @param exitNode the exit region node
     */
    public void setExitNode(PDGRegionNode exitNode)
    {
        this.exitNode = exitNode;
    }

    /**
     * Adds a node if not already present, indexing it by instruction, defined value and block.
     * @param node the node to add
     */
    public void addNode(PDGNode node)
    {
        if (!nodes.contains(node))
        {
            nodes.add(node);

            if (node instanceof PDGInstructionNode)
            {
                PDGInstructionNode instrNode = (PDGInstructionNode) node;
                instructionToNode.put(instrNode.getInstruction(), instrNode);
                SSAValue defined = instrNode.getDefinedValue();
                if (defined != null)
                {
                    valueToNode.put(defined, instrNode);
                }
            }

            IRBlock block = node.getBlock();
            if (block != null)
            {
                blockToNodes.computeIfAbsent(block, k -> new ArrayList<>()).add(node);
            }
        }
    }

    /**
     * Adds an edge if not already present and links it into both endpoints.
     * @param edge the edge to add
     */
    public void addEdge(PDGEdge edge)
    {
        if (!edges.contains(edge))
        {
            edges.add(edge);
            edge.getSource().addOutgoingEdge(edge);
            edge.getTarget().addIncomingEdge(edge);
        }
    }

    /**
     * Adds a dependence edge between two nodes.
     * @param source the node depended upon
     * @param target the dependent node
     * @param type the kind of dependence
     */
    public void addEdge(PDGNode source, PDGNode target, PDGDependenceType type)
    {
        addEdge(new PDGEdge(source, target, type));
    }

    /**
     * Adds a dependence edge carrying the value that induced it.
     * @param source the node depended upon
     * @param target the dependent node
     * @param type the kind of dependence
     * @param dependentValue the value flowing along the edge
     */
    public void addEdge(PDGNode source, PDGNode target, PDGDependenceType type, SSAValue dependentValue)
    {
        addEdge(new PDGEdge(source, target, type, dependentValue));
    }

    /**
     * Removes an edge and unlinks it from both endpoints.
     * @param edge the edge to remove
     */
    public void removeEdge(PDGEdge edge)
    {
        edges.remove(edge);
        edge.getSource().removeOutgoingEdge(edge);
        edge.getTarget().removeIncomingEdge(edge);
    }

    /**
     * Looks up the node built for an instruction.
     * @param instruction the instruction to look up
     * @return the node, or null if the instruction has none
     */
    public PDGNode getNodeForInstruction(IRInstruction instruction)
    {
        return instructionToNode.get(instruction);
    }

    /**
     * Looks up the node that defines a value.
     * @param value the SSA value to look up
     * @return the defining node, or null if the value has none
     */
    public PDGNode getNodeForValue(SSAValue value)
    {
        return valueToNode.get(value);
    }

    /**
     * Lists the nodes built from a block, in insertion order.
     * @param block the block to query
     * @return the nodes in that block, empty if none
     */
    public List<PDGNode> getNodesInBlock(IRBlock block)
    {
        return blockToNodes.getOrDefault(block, Collections.emptyList());
    }

    /**
     * Collects every node of a given kind.
     * @param type the node kind to match
     * @return the matching nodes
     */
    public List<PDGNode> getNodesByType(PDGNodeType type)
    {
        List<PDGNode> result = new ArrayList<>();
        for (PDGNode node : nodes)
        {
            if (node.getType() == type)
            {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Collects every edge of a given dependence kind.
     * @param type the dependence kind to match
     * @return the matching edges
     */
    public List<PDGEdge> getEdgesByType(PDGDependenceType type)
    {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : edges)
        {
            if (edge.getType() == type)
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * @return every control dependence edge in the graph
     */
    public List<PDGEdge> getControlDependenceEdges()
    {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : edges)
        {
            if (edge.isControlDependence())
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * @return every data dependence edge in the graph
     */
    public List<PDGEdge> getDataDependenceEdges()
    {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : edges)
        {
            if (edge.isDataDependence())
            {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * Finds the nodes whose execution is controlled by the given node.
     * @param node the controlling node
     * @return the nodes control dependent on it
     */
    public Set<PDGNode> getControlDependentOn(PDGNode node)
    {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getOutgoingEdges())
        {
            if (edge.isControlDependence())
            {
                result.add(edge.getTarget());
            }
        }
        return result;
    }

    /**
     * Finds the nodes that control whether the given node executes.
     * @param node the dependent node
     * @return the nodes it is control dependent on
     */
    public Set<PDGNode> getControllingNodes(PDGNode node)
    {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getIncomingEdges())
        {
            if (edge.isControlDependence())
            {
                result.add(edge.getSource());
            }
        }
        return result;
    }

    /**
     * Finds the nodes that consume values produced by the given node.
     * @param node the producing node
     * @return the nodes data dependent on it
     */
    public Set<PDGNode> getDataDependentOn(PDGNode node)
    {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getOutgoingEdges())
        {
            if (edge.isDataDependence())
            {
                result.add(edge.getTarget());
            }
        }
        return result;
    }

    /**
     * Finds the nodes producing the values the given node consumes.
     * @param node the consuming node
     * @return the nodes it is data dependent on
     */
    public Set<PDGNode> getDataSources(PDGNode node)
    {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getIncomingEdges())
        {
            if (edge.isDataDependence())
            {
                result.add(edge.getSource());
            }
        }
        return result;
    }

    /**
     * Finds every node reachable from the given node over one outgoing edge, of any kind.
     * @param node the node to query
     * @return the immediate successors
     */
    public Set<PDGNode> getAllDependentOn(PDGNode node)
    {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getOutgoingEdges())
        {
            result.add(edge.getTarget());
        }
        return result;
    }

    /**
     * Finds every node reaching the given node over one incoming edge, of any kind.
     * @param node the node to query
     * @return the immediate predecessors
     */
    public Set<PDGNode> getAllSources(PDGNode node)
    {
        Set<PDGNode> result = new LinkedHashSet<>();
        for (PDGEdge edge : node.getIncomingEdges())
        {
            result.add(edge.getSource());
        }
        return result;
    }

    /**
     * @return the number of nodes
     */
    public int getNodeCount()
    {
        return nodes.size();
    }

    /**
     * @return the number of edges
     */
    public int getEdgeCount()
    {
        return edges.size();
    }

    /**
     * @return the number of control dependence edges
     */
    public int getControlEdgeCount()
    {
        int count = 0;
        for (PDGEdge edge : edges)
        {
            if (edge.isControlDependence())
            {
                count++;
            }
        }
        return count;
    }

    /**
     * @return the number of data dependence edges
     */
    public int getDataEdgeCount()
    {
        int count = 0;
        for (PDGEdge edge : edges)
        {
            if (edge.isDataDependence())
            {
                count++;
            }
        }
        return count;
    }

    /**
     * @return an unmodifiable view of the nodes
     */
    public List<PDGNode> getNodes()
    {
        return Collections.unmodifiableList(nodes);
    }

    /**
     * @return an unmodifiable view of the edges
     */
    public List<PDGEdge> getEdges()
    {
        return Collections.unmodifiableList(edges);
    }

    /**
     * @return whether an entry node has been set
     */
    public boolean hasEntryNode()
    {
        return entryNode != null;
    }

    /**
     * Searches the node list for a given id.
     * @param id the node id
     * @return the node, or null if no node carries that id
     */
    public PDGNode getNode(int id)
    {
        for (PDGNode node : nodes)
        {
            if (node.getId() == id)
            {
                return node;
            }
        }
        return null;
    }

    /**
     * Alias for {@link #getNodesByType(PDGNodeType)}.
     * @param type the node kind to match
     * @return the matching nodes
     */
    public List<PDGNode> getNodesOfType(PDGNodeType type)
    {
        return getNodesByType(type);
    }

    /**
     * Alias for {@link #getNodeForInstruction(IRInstruction)}.
     * @param instruction the instruction to look up
     * @return the node, or null if the instruction has none
     */
    public PDGNode getInstructionNode(IRInstruction instruction)
    {
        return getNodeForInstruction(instruction);
    }

    @Override
    public String toString()
    {
        return String.format("PDG[%s: %d nodes, %d edges (%d ctrl, %d data)]",
            methodName, getNodeCount(), getEdgeCount(),
            getControlEdgeCount(), getDataEdgeCount());
    }
}
