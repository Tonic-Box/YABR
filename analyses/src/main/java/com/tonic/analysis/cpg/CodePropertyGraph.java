package com.tonic.analysis.cpg;

import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.cpg.query.CPGQuery;
import com.tonic.parser.ClassPool;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.*;
import java.util.stream.Stream;

/**
 * Unified code property graph over a class pool, combining AST, CFG, dependence, and call edges
 * in one node/edge store with an index and a fluent query entry point.
 */
public class CodePropertyGraph
{

    private final ClassPool classPool;
    private final Map<Long, CPGNode> nodes = new LinkedHashMap<>();
    private final Set<CPGEdge> edges = new LinkedHashSet<>();
    private final CPGIndex index = new CPGIndex();

    private long nextNodeId = 0;

    /**
     * Creates an empty graph over the given class pool.
     * @param classPool the classes this graph describes
     */
    public CodePropertyGraph(ClassPool classPool)
    {
        this.classPool = classPool;
    }

    /**
     * @return the class pool
     */
    public ClassPool getClassPool()
    {
        return classPool;
    }

    /**
     * @return the nodes
     */
    public Map<Long, CPGNode> getNodes()
    {
        return nodes;
    }

    /**
     * @return the edges
     */
    public Set<CPGEdge> getEdges()
    {
        return edges;
    }

    /**
     * @return the index
     */
    public CPGIndex getIndex()
    {
        return index;
    }

    /**
     * @return the next node id
     */
    public long getNextNodeId()
    {
        return nextNodeId;
    }

    /**
     * Reserves the next unique node id.
     * @return the allocated id
     */
    public long allocateNodeId()
    {
        return nextNodeId++;
    }

    /**
     * Adds a node to the graph and its index.
     * @param node the node to add
     */
    public void addNode(CPGNode node)
    {
        nodes.put(node.getId(), node);
        index.index(node);
    }

    /**
     * Removes a node along with every edge touching it.
     * @param node the node to remove
     */
    public void removeNode(CPGNode node)
    {
        nodes.remove(node.getId());
        index.remove(node);

        edges.removeIf(edge -> edge.getSource().equals(node) || edge.getTarget().equals(node));
    }

    /**
     * Adds an edge and links it into both endpoint nodes; duplicates are ignored.
     * @param edge the edge to add
     */
    public void addEdge(CPGEdge edge)
    {
        if (edges.add(edge))
        {
            edge.getSource().addOutgoingEdge(edge);
            edge.getTarget().addIncomingEdge(edge);
        }
    }

    /**
     * Adds an edge of the given type between two nodes.
     * @param source the edge source
     * @param target the edge target
     * @param type the edge type
     */
    public void addEdge(CPGNode source, CPGNode target, CPGEdgeType type)
    {
        addEdge(new CPGEdge(source, target, type));
    }

    /**
     * Adds an edge of the given type carrying the supplied properties.
     * @param source the edge source
     * @param target the edge target
     * @param type the edge type
     * @param properties initial edge properties
     */
    public void addEdge(CPGNode source, CPGNode target, CPGEdgeType type, Map<String, Object> properties)
    {
        addEdge(new CPGEdge(source, target, type, properties));
    }

    /**
     * Removes an edge and unlinks it from both endpoint nodes.
     * @param edge the edge to remove
     */
    public void removeEdge(CPGEdge edge)
    {
        if (edges.remove(edge))
        {
            edge.getSource().removeOutgoingEdge(edge);
            edge.getTarget().removeIncomingEdge(edge);
        }
    }

    /**
     * Looks up a node by id.
     * @param id the node id
     * @return the node, or null if absent
     */
    public CPGNode getNode(long id)
    {
        return nodes.get(id);
    }

    /**
     * Streams all nodes that are instances of the given class.
     * @param nodeType the node class to filter by
     * @param <T> the node class
     * @return a stream of matching nodes
     */
    @SuppressWarnings("unchecked")
    public <T extends CPGNode> Stream<T> nodes(Class<T> nodeType)
    {
        return nodes.values().stream()
            .filter(nodeType::isInstance)
            .map(n -> (T) n);
    }

    /**
     * Streams all nodes of the given node type via the index.
     * @param type the node type
     * @return a stream of matching nodes
     */
    public Stream<CPGNode> nodes(CPGNodeType type)
    {
        return index.getByType(type).stream();
    }

    /**
     * Streams all edges of the given edge type.
     * @param type the edge type
     * @return a stream of matching edges
     */
    public Stream<CPGEdge> edges(CPGEdgeType type)
    {
        return edges.stream().filter(e -> e.getType() == type);
    }

    /**
     * Looks up a method node by its exact signature.
     * @param owner the declaring class internal name
     * @param name the method name
     * @param descriptor the method descriptor
     * @return the method node, if present
     */
    public Optional<MethodNode> getMethod(String owner, String name, String descriptor)
    {
        return Optional.ofNullable(index.getMethod(owner, name, descriptor));
    }

    /**
     * Streams call sites targeting a method name regardless of descriptor.
     * @param owner the target class internal name
     * @param name the target method name
     * @return a stream of matching call sites
     */
    public Stream<CallSiteNode> getCallsTo(String owner, String name)
    {
        return index.getCallsTo(owner, name).stream();
    }

    /**
     * Streams call sites targeting an exact method signature.
     * @param owner the target class internal name
     * @param name the target method name
     * @param descriptor the target method descriptor
     * @return a stream of matching call sites
     */
    public Stream<CallSiteNode> getCallsTo(String owner, String name, String descriptor)
    {
        return index.getCallsTo(owner, name, descriptor).stream();
    }

    /**
     * Streams instruction nodes wrapping a specific IR instruction class.
     * @param type the IR instruction class
     * @return a stream of matching instruction nodes
     */
    public Stream<InstructionNode> getInstructionsOfType(Class<? extends IRInstruction> type)
    {
        return index.getInstructionsOfType(type).stream();
    }

    /**
     * Starts a fluent query over this graph.
     * @return a new query
     */
    public CPGQuery query()
    {
        return new CPGQuery(this);
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
     * @return the number of method nodes
     */
    public int getMethodCount()
    {
        return index.getMethodCount();
    }

    /**
     * Counts edges of one type.
     * @param type the edge type
     * @return the number of edges of that type
     */
    public int getEdgeCount(CPGEdgeType type)
    {
        int count = 0;
        for (CPGEdge edge : edges)
        {
            if (edge.getType() == type)
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Tallies edge counts per edge type.
     * @return a map from edge type to occurrence count
     */
    public Map<CPGEdgeType, Integer> getEdgeTypeCounts()
    {
        Map<CPGEdgeType, Integer> counts = new EnumMap<>(CPGEdgeType.class);
        for (CPGEdge edge : edges)
        {
            counts.merge(edge.getType(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * @return an unmodifiable view of all nodes
     */
    public Collection<CPGNode> getAllNodes()
    {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * @return an unmodifiable view of all edges
     */
    public Set<CPGEdge> getAllEdges()
    {
        return Collections.unmodifiableSet(edges);
    }

    @Override
    public String toString()
    {
        return String.format("CPG[%d nodes, %d edges, %d methods]", getNodeCount(), getEdgeCount(), getMethodCount());
    }
}
