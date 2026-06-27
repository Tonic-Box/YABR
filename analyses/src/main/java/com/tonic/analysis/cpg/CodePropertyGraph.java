package com.tonic.analysis.cpg;

import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.cpg.query.CPGQuery;
import com.tonic.parser.ClassPool;
import com.tonic.analysis.ssa.ir.IRInstruction;
import lombok.Getter;

import java.util.*;
import java.util.stream.Stream;

@Getter
public class CodePropertyGraph {

    private final ClassPool classPool;
    private final Map<Long, CPGNode> nodes = new LinkedHashMap<>();
    private final Set<CPGEdge> edges = new LinkedHashSet<>();
    private final CPGIndex index = new CPGIndex();

    private long nextNodeId = 0;

    public CodePropertyGraph(ClassPool classPool) {
        this.classPool = classPool;
    }

    public long allocateNodeId() {
        return nextNodeId++;
    }

    public void addNode(CPGNode node) {
        nodes.put(node.getId(), node);
        index.index(node);
    }

    public void removeNode(CPGNode node) {
        nodes.remove(node.getId());
        index.remove(node);

        Iterator<CPGEdge> it = edges.iterator();
        while (it.hasNext()) {
            CPGEdge edge = it.next();
            if (edge.getSource().equals(node) || edge.getTarget().equals(node)) {
                it.remove();
            }
        }
    }

    public void addEdge(CPGEdge edge) {
        if (edges.add(edge)) {
            edge.getSource().addOutgoingEdge(edge);
            edge.getTarget().addIncomingEdge(edge);
        }
    }

    public void addEdge(CPGNode source, CPGNode target, CPGEdgeType type) {
        addEdge(new CPGEdge(source, target, type));
    }

    public void addEdge(CPGNode source, CPGNode target, CPGEdgeType type, Map<String, Object> properties) {
        addEdge(new CPGEdge(source, target, type, properties));
    }

    public void removeEdge(CPGEdge edge) {
        if (edges.remove(edge)) {
            edge.getSource().removeOutgoingEdge(edge);
            edge.getTarget().removeIncomingEdge(edge);
        }
    }

    public CPGNode getNode(long id) {
        return nodes.get(id);
    }

    @SuppressWarnings("unchecked")
    public <T extends CPGNode> Stream<T> nodes(Class<T> nodeType) {
        return nodes.values().stream()
            .filter(nodeType::isInstance)
            .map(n -> (T) n);
    }

    public Stream<CPGNode> nodes(CPGNodeType type) {
        return index.getByType(type).stream();
    }

    public Stream<CPGEdge> edges(CPGEdgeType type) {
        return edges.stream().filter(e -> e.getType() == type);
    }

    public Optional<MethodNode> getMethod(String owner, String name, String descriptor) {
        return Optional.ofNullable(index.getMethod(owner, name, descriptor));
    }

    public Stream<CallSiteNode> getCallsTo(String owner, String name) {
        return index.getCallsTo(owner, name).stream();
    }

    public Stream<CallSiteNode> getCallsTo(String owner, String name, String descriptor) {
        return index.getCallsTo(owner, name, descriptor).stream();
    }

    public Stream<InstructionNode> getInstructionsOfType(Class<? extends IRInstruction> type) {
        return index.getInstructionsOfType(type).stream();
    }

    public CPGQuery query() {
        return new CPGQuery(this);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public int getMethodCount() {
        return index.getMethodCount();
    }

    public int getEdgeCount(CPGEdgeType type) {
        int count = 0;
        for (CPGEdge edge : edges) {
            if (edge.getType() == type) {
                count++;
            }
        }
        return count;
    }

    public Map<CPGEdgeType, Integer> getEdgeTypeCounts() {
        Map<CPGEdgeType, Integer> counts = new EnumMap<>(CPGEdgeType.class);
        for (CPGEdge edge : edges) {
            counts.merge(edge.getType(), 1, Integer::sum);
        }
        return counts;
    }

    public Collection<CPGNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Set<CPGEdge> getAllEdges() {
        return Collections.unmodifiableSet(edges);
    }

    @Override
    public String toString() {
        return String.format("CPG[%d nodes, %d edges, %d methods]",
            getNodeCount(), getEdgeCount(), getMethodCount());
    }
}
