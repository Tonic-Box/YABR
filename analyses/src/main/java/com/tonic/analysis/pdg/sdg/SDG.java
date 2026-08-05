package com.tonic.analysis.pdg.sdg;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.node.*;

import java.util.*;

/**
 * System dependence graph - the per-method PDGs joined by parameter and summary
 * edges at resolved call sites.
 */
public class SDG
{

    private final CallGraph callGraph;
    private final Map<MethodReference, PDG> methodPDGs = new LinkedHashMap<>();
    private final Map<MethodReference, SDGEntryNode> methodEntries = new LinkedHashMap<>();

    private final List<PDGNode> allNodes = new ArrayList<>();
    private final List<PDGEdge> allEdges = new ArrayList<>();

    private final List<PDGEdge> parameterEdges = new ArrayList<>();
    private final List<PDGEdge> summaryEdges = new ArrayList<>();

    private final Map<SDGCallNode, Set<SDGEntryNode>> callToTargets = new HashMap<>();

    private int nextNodeId = 0;

    /**
     * Creates an empty graph over a call graph; PDGs and entries are added
     * afterwards.
     * @param callGraph the call graph the procedures come from
     */
    public SDG(CallGraph callGraph)
    {
        this.callGraph = callGraph;
    }

    /**
     * @return the call graph
     */
    public CallGraph getCallGraph()
    {
        return callGraph;
    }

    /**
     * @return the method PD gs
     */
    public Map<MethodReference, PDG> getMethodPDGs()
    {
        return methodPDGs;
    }

    /**
     * @return the method entries
     */
    public Map<MethodReference, SDGEntryNode> getMethodEntries()
    {
        return methodEntries;
    }

    /**
     * @return the parameter edges
     */
    public List<PDGEdge> getParameterEdges()
    {
        return parameterEdges;
    }

    /**
     * @return the summary edges
     */
    public List<PDGEdge> getSummaryEdges()
    {
        return summaryEdges;
    }

    /**
     * @return the call to targets
     */
    public Map<SDGCallNode, Set<SDGEntryNode>> getCallToTargets()
    {
        return callToTargets;
    }

    /**
     * @return the next node id
     */
    public int getNextNodeId()
    {
        return nextNodeId;
    }

    /**
     * Hands out the next unused node id.
     * @return the allocated id
     */
    public int allocateNodeId()
    {
        return nextNodeId++;
    }

    /**
     * Registers a method's PDG and folds its nodes and edges into the graph-wide
     * sets, skipping ones already present.
     * @param methodRef the method
     * @param pdg its dependence graph
     */
    public void addMethodPDG(MethodReference methodRef, PDG pdg)
    {
        methodPDGs.put(methodRef, pdg);

        for (PDGNode node : pdg.getNodes())
        {
            if (!allNodes.contains(node))
            {
                allNodes.add(node);
            }
        }

        for (PDGEdge edge : pdg.getEdges())
        {
            if (!allEdges.contains(edge))
            {
                allEdges.add(edge);
            }
        }
    }

    /**
     * Registers a procedure entry node and adds it to the node set.
     * @param methodRef the method
     * @param entry its entry node
     */
    public void addMethodEntry(MethodReference methodRef, SDGEntryNode entry)
    {
        methodEntries.put(methodRef, entry);
        addNode(entry);
    }

    /**
     * Adds a node if it is not already present.
     * @param node the node to add
     */
    public void addNode(PDGNode node)
    {
        if (!allNodes.contains(node))
        {
            allNodes.add(node);
        }
    }

    /**
     * Adds an edge if it is not already present, links it to its endpoints and
     * files interprocedural edges under the summary or parameter list.
     * @param edge the edge to add
     */
    public void addEdge(PDGEdge edge)
    {
        if (!allEdges.contains(edge))
        {
            allEdges.add(edge);
            edge.getSource().addOutgoingEdge(edge);
            edge.getTarget().addIncomingEdge(edge);

            if (edge.getType().isInterproceduralEdge())
            {
                if (edge.getType() == PDGDependenceType.SUMMARY)
                {
                    summaryEdges.add(edge);
                }
                else
                {
                    parameterEdges.add(edge);
                }
            }
        }
    }

    /**
     * Records a resolved callee for a call site and points the call node at it.
     * @param callNode the call site
     * @param targetEntry the callee entry node
     */
    public void registerCallTarget(SDGCallNode callNode, SDGEntryNode targetEntry)
    {
        callToTargets.computeIfAbsent(callNode, k -> new HashSet<>()).add(targetEntry);
        callNode.setTargetEntry(targetEntry);
    }

    /**
     * @param method the method to look up
     * @return its PDG, or null if none is registered
     */
    public PDG getPDG(MethodReference method)
    {
        return methodPDGs.get(method);
    }

    /**
     * @param method the method to look up
     * @return its entry node, or null if none is registered
     */
    public SDGEntryNode getEntry(MethodReference method)
    {
        return methodEntries.get(method);
    }

    /**
     * @param callNode the call site
     * @return the entry nodes registered as targets, empty if none
     */
    public Set<SDGEntryNode> getCallTargets(SDGCallNode callNode)
    {
        return callToTargets.getOrDefault(callNode, Collections.emptySet());
    }

    /**
     * Finds the call sites appearing in a method's PDG.
     * @param method the caller
     * @return the call sites, empty if the method has no registered PDG
     */
    public Set<SDGCallNode> getCallSitesIn(MethodReference method)
    {
        Set<SDGCallNode> callSites = new LinkedHashSet<>();
        PDG pdg = methodPDGs.get(method);
        if (pdg != null)
        {
            for (PDGNode node : pdg.getNodes())
            {
                if (node instanceof SDGCallNode)
                {
                    callSites.add((SDGCallNode) node);
                }
            }
        }
        return callSites;
    }

    /**
     * Finds the call sites resolved to a method.
     * @param method the callee
     * @return the call sites targeting it, empty if the method has no entry node
     */
    public Set<SDGCallNode> getCallSitesTo(MethodReference method)
    {
        Set<SDGCallNode> callSites = new LinkedHashSet<>();
        SDGEntryNode entry = methodEntries.get(method);
        if (entry == null) return callSites;

        for (Map.Entry<SDGCallNode, Set<SDGEntryNode>> e : callToTargets.entrySet())
        {
            if (e.getValue().contains(entry))
            {
                callSites.add(e.getKey());
            }
        }
        return callSites;
    }

    /**
     * @return a fresh list of the parameter edges followed by the summary edges
     */
    public List<PDGEdge> getInterproceduralEdges()
    {
        List<PDGEdge> result = new ArrayList<>(parameterEdges);
        result.addAll(summaryEdges);
        return result;
    }

    /**
     * Selects the summary edges whose source is an actual-in of a call site.
     * @param callNode the call site
     * @return the matching summary edges
     */
    public List<PDGEdge> getSummaryEdges(SDGCallNode callNode)
    {
        List<PDGEdge> result = new ArrayList<>();
        for (PDGEdge edge : summaryEdges)
        {
            if (edge.getSource() instanceof SDGActualInNode)
            {
                SDGActualInNode actualIn = (SDGActualInNode) edge.getSource();
                if (actualIn.getCallNode() == callNode)
                {
                    result.add(edge);
                }
            }
        }
        return result;
    }

    /**
     * @return an unmodifiable view of the methods with a registered PDG
     */
    public Set<MethodReference> getMethods()
    {
        return Collections.unmodifiableSet(methodPDGs.keySet());
    }

    /**
     * @return the number of methods with a registered PDG
     */
    public int getMethodCount()
    {
        return methodPDGs.size();
    }

    /**
     * @return the number of nodes
     */
    public int getTotalNodeCount()
    {
        return allNodes.size();
    }

    /**
     * @return the number of edges
     */
    public int getTotalEdgeCount()
    {
        return allEdges.size();
    }

    /**
     * @return the number of parameter and summary edges combined
     */
    public int getInterproceduralEdgeCount()
    {
        return parameterEdges.size() + summaryEdges.size();
    }

    /**
     * @return the number of summary edges
     */
    public int getSummaryEdgeCount()
    {
        return summaryEdges.size();
    }

    /**
     * @return an unmodifiable view of every node
     */
    public List<PDGNode> getAllNodes()
    {
        return Collections.unmodifiableList(allNodes);
    }

    /**
     * @return an unmodifiable view of every edge
     */
    public List<PDGEdge> getAllEdges()
    {
        return Collections.unmodifiableList(allEdges);
    }

    /**
     * @return an unmodifiable view of every procedure entry node
     */
    public Collection<SDGEntryNode> getEntryNodes()
    {
        return Collections.unmodifiableCollection(methodEntries.values());
    }

    /**
     * @param entry the procedure entry node
     * @return the formal-in nodes of the procedure
     */
    public List<SDGFormalInNode> getFormalIns(SDGEntryNode entry)
    {
        return entry.getFormalIns();
    }

    /**
     * Collects the formal-out node of a procedure, if it has one.
     * @param entry the procedure entry node
     * @return a list holding the formal-out node, or empty
     */
    public List<SDGFormalOutNode> getFormalOuts(SDGEntryNode entry)
    {
        List<SDGFormalOutNode> result = new ArrayList<>();
        if (entry.getFormalOut() != null)
        {
            result.add(entry.getFormalOut());
        }
        return result;
    }

    /**
     * Collects the call sites of a procedure from its PDG, plus any registered
     * call node not already in that PDG.
     * @param entry the procedure entry node
     * @return the call sites
     */
    public List<SDGCallNode> getCallNodes(SDGEntryNode entry)
    {
        List<SDGCallNode> result = new ArrayList<>();
        PDG pdg = entry.getProcedurePDG();
        if (pdg != null)
        {
            for (PDGNode node : pdg.getNodes())
            {
                if (node instanceof SDGCallNode)
                {
                    result.add((SDGCallNode) node);
                }
            }
        }
        for (SDGCallNode callNode : callToTargets.keySet())
        {
            if (!result.contains(callNode))
            {
                for (PDGNode node : allNodes)
                {
                    if (node instanceof SDGCallNode && node.equals(callNode))
                    {
                        result.add((SDGCallNode) node);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * @param callNode the call site
     * @return the actual-in nodes of the call site
     */
    public List<SDGActualInNode> getActualIns(SDGCallNode callNode)
    {
        return callNode.getActualIns();
    }

    /**
     * Collects the actual-out node of a call site, if it has one.
     * @param callNode the call site
     * @return a list holding the actual-out node, or empty
     */
    public List<SDGActualOutNode> getActualOuts(SDGCallNode callNode)
    {
        List<SDGActualOutNode> result = new ArrayList<>();
        if (callNode.getActualOut() != null)
        {
            result.add(callNode.getActualOut());
        }
        return result;
    }

    /**
     * Counts the call nodes across every registered node.
     * @return the number of call nodes
     */
    public int getCallNodesCount()
    {
        int count = 0;
        for (PDGNode node : allNodes)
        {
            if (node instanceof SDGCallNode)
            {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString()
    {
        return String.format("SDG[%d methods, %d nodes, %d edges (%d interprocedural, %d summary)]",
            getMethodCount(), getTotalNodeCount(), getTotalEdgeCount(),
            getInterproceduralEdgeCount(), getSummaryEdgeCount());
    }
}
