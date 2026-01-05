package com.tonic.analysis.pdg.sdg;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.node.*;
import lombok.Getter;

import java.util.*;

@Getter
public class SDG {

    private final CallGraph callGraph;
    private final Map<MethodReference, PDG> methodPDGs = new LinkedHashMap<>();
    private final Map<MethodReference, SDGEntryNode> methodEntries = new LinkedHashMap<>();

    private final List<PDGNode> allNodes = new ArrayList<>();
    private final List<PDGEdge> allEdges = new ArrayList<>();

    private final List<PDGEdge> parameterEdges = new ArrayList<>();
    private final List<PDGEdge> summaryEdges = new ArrayList<>();

    private final Map<SDGCallNode, Set<SDGEntryNode>> callToTargets = new HashMap<>();

    private int nextNodeId = 0;

    public SDG(CallGraph callGraph) {
        this.callGraph = callGraph;
    }

    public int allocateNodeId() {
        return nextNodeId++;
    }

    public void addMethodPDG(MethodReference methodRef, PDG pdg) {
        methodPDGs.put(methodRef, pdg);

        for (PDGNode node : pdg.getNodes()) {
            if (!allNodes.contains(node)) {
                allNodes.add(node);
            }
        }

        for (PDGEdge edge : pdg.getEdges()) {
            if (!allEdges.contains(edge)) {
                allEdges.add(edge);
            }
        }
    }

    public void addMethodEntry(MethodReference methodRef, SDGEntryNode entry) {
        methodEntries.put(methodRef, entry);
        addNode(entry);
    }

    public void addNode(PDGNode node) {
        if (!allNodes.contains(node)) {
            allNodes.add(node);
        }
    }

    public void addEdge(PDGEdge edge) {
        if (!allEdges.contains(edge)) {
            allEdges.add(edge);
            edge.getSource().addOutgoingEdge(edge);
            edge.getTarget().addIncomingEdge(edge);

            if (edge.getType().isInterproceduralEdge()) {
                if (edge.getType() == PDGDependenceType.SUMMARY) {
                    summaryEdges.add(edge);
                } else {
                    parameterEdges.add(edge);
                }
            }
        }
    }

    public void registerCallTarget(SDGCallNode callNode, SDGEntryNode targetEntry) {
        callToTargets.computeIfAbsent(callNode, k -> new HashSet<>()).add(targetEntry);
        callNode.setTargetEntry(targetEntry);
    }

    public PDG getPDG(MethodReference method) {
        return methodPDGs.get(method);
    }

    public SDGEntryNode getEntry(MethodReference method) {
        return methodEntries.get(method);
    }

    public Set<SDGEntryNode> getCallTargets(SDGCallNode callNode) {
        return callToTargets.getOrDefault(callNode, Collections.emptySet());
    }

    public Set<SDGCallNode> getCallSitesIn(MethodReference method) {
        Set<SDGCallNode> callSites = new LinkedHashSet<>();
        PDG pdg = methodPDGs.get(method);
        if (pdg != null) {
            for (PDGNode node : pdg.getNodes()) {
                if (node instanceof SDGCallNode) {
                    callSites.add((SDGCallNode) node);
                }
            }
        }
        return callSites;
    }

    public Set<SDGCallNode> getCallSitesTo(MethodReference method) {
        Set<SDGCallNode> callSites = new LinkedHashSet<>();
        SDGEntryNode entry = methodEntries.get(method);
        if (entry == null) return callSites;

        for (Map.Entry<SDGCallNode, Set<SDGEntryNode>> e : callToTargets.entrySet()) {
            if (e.getValue().contains(entry)) {
                callSites.add(e.getKey());
            }
        }
        return callSites;
    }

    public List<PDGEdge> getInterproceduralEdges() {
        List<PDGEdge> result = new ArrayList<>(parameterEdges);
        result.addAll(summaryEdges);
        return result;
    }

    public List<PDGEdge> getSummaryEdges(SDGCallNode callNode) {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : summaryEdges) {
            if (edge.getSource() instanceof SDGActualInNode) {
                SDGActualInNode actualIn = (SDGActualInNode) edge.getSource();
                if (actualIn.getCallNode() == callNode) {
                    result.add(edge);
                }
            }
        }
        return result;
    }

    public Set<MethodReference> getMethods() {
        return Collections.unmodifiableSet(methodPDGs.keySet());
    }

    public int getMethodCount() {
        return methodPDGs.size();
    }

    public int getTotalNodeCount() {
        return allNodes.size();
    }

    public int getTotalEdgeCount() {
        return allEdges.size();
    }

    public int getInterproceduralEdgeCount() {
        return parameterEdges.size() + summaryEdges.size();
    }

    public int getSummaryEdgeCount() {
        return summaryEdges.size();
    }

    public List<PDGNode> getAllNodes() {
        return Collections.unmodifiableList(allNodes);
    }

    public List<PDGEdge> getAllEdges() {
        return Collections.unmodifiableList(allEdges);
    }

    public Collection<SDGEntryNode> getEntryNodes() {
        return Collections.unmodifiableCollection(methodEntries.values());
    }

    public List<SDGFormalInNode> getFormalIns(SDGEntryNode entry) {
        return entry.getFormalIns();
    }

    public List<SDGFormalOutNode> getFormalOuts(SDGEntryNode entry) {
        List<SDGFormalOutNode> result = new ArrayList<>();
        if (entry.getFormalOut() != null) {
            result.add(entry.getFormalOut());
        }
        return result;
    }

    public List<SDGCallNode> getCallNodes(SDGEntryNode entry) {
        List<SDGCallNode> result = new ArrayList<>();
        PDG pdg = entry.getProcedurePDG();
        if (pdg != null) {
            for (PDGNode node : pdg.getNodes()) {
                if (node instanceof SDGCallNode) {
                    result.add((SDGCallNode) node);
                }
            }
        }
        for (SDGCallNode callNode : callToTargets.keySet()) {
            if (!result.contains(callNode)) {
                for (PDGNode node : allNodes) {
                    if (node instanceof SDGCallNode && node.equals(callNode)) {
                        result.add((SDGCallNode) node);
                        break;
                    }
                }
            }
        }
        return result;
    }

    public List<SDGActualInNode> getActualIns(SDGCallNode callNode) {
        return callNode.getActualIns();
    }

    public List<SDGActualOutNode> getActualOuts(SDGCallNode callNode) {
        List<SDGActualOutNode> result = new ArrayList<>();
        if (callNode.getActualOut() != null) {
            result.add(callNode.getActualOut());
        }
        return result;
    }

    public int getCallNodesCount() {
        int count = 0;
        for (PDGNode node : allNodes) {
            if (node instanceof SDGCallNode) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("SDG[%d methods, %d nodes, %d edges (%d interprocedural, %d summary)]",
            getMethodCount(), getTotalNodeCount(), getTotalEdgeCount(),
            getInterproceduralEdgeCount(), getSummaryEdgeCount());
    }
}
