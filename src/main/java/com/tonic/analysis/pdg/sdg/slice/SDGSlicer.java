package com.tonic.analysis.pdg.sdg.slice;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.node.*;
import com.tonic.analysis.pdg.slice.SliceResult;
import lombok.Getter;

import java.util.*;

@Getter
public class SDGSlicer {

    private final SDG sdg;
    private boolean useSummaryEdges = true;
    private boolean contextSensitive = true;

    public SDGSlicer(SDG sdg) {
        this.sdg = sdg;
    }

    public SDGSlicer withSummaryEdges(boolean use) {
        this.useSummaryEdges = use;
        return this;
    }

    public SDGSlicer withContextSensitivity(boolean contextSensitive) {
        this.contextSensitive = contextSensitive;
        return this;
    }

    public SliceResult interproceduralBackwardSlice(PDGNode criterion) {
        if (contextSensitive) {
            return contextSensitiveBackwardSlice(criterion);
        } else {
            return contextInsensitiveBackwardSlice(criterion);
        }
    }

    public SliceResult interproceduralForwardSlice(PDGNode criterion) {
        if (contextSensitive) {
            return contextSensitiveForwardSlice(criterion);
        } else {
            return contextInsensitiveForwardSlice(criterion);
        }
    }

    private SliceResult contextSensitiveBackwardSlice(PDGNode criterion) {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, Set.of(criterion));

        Set<PDGNode> phase1Visited = new LinkedHashSet<>();
        Set<PDGNode> phase2Visited = new LinkedHashSet<>();

        ascendingPhase(criterion, phase1Visited, result);
        descendingPhase(phase1Visited, phase2Visited, result);

        return result;
    }

    private void ascendingPhase(PDGNode start, Set<PDGNode> visited, SliceResult result) {
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) continue;

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges()) {
                PDGNode source = edge.getSource();

                if (edge.getType() == PDGDependenceType.PARAMETER_OUT) {
                    continue;
                }

                if (edge.getType() == PDGDependenceType.CALL) {
                    continue;
                }

                if (edge.getType() == PDGDependenceType.PARAMETER_IN) {
                    if (source instanceof SDGActualInNode) {
                        SDGActualInNode actualIn = (SDGActualInNode) source;
                        SDGCallNode callNode = actualIn.getCallNode();
                        if (callNode != null && !visited.contains(callNode)) {
                            result.addEdge(edge);
                            worklist.add(callNode);
                        }
                    }
                    continue;
                }

                if (useSummaryEdges && edge.getType() == PDGDependenceType.SUMMARY) {
                    result.addEdge(edge);
                    if (!visited.contains(source)) {
                        worklist.add(source);
                    }
                    continue;
                }

                if (!edge.isInterprocedural()) {
                    result.addEdge(edge);
                    if (!visited.contains(source)) {
                        worklist.add(source);
                    }
                }
            }
        }
    }

    private void descendingPhase(Set<PDGNode> phase1Nodes, Set<PDGNode> visited, SliceResult result) {
        Deque<PDGNode> worklist = new ArrayDeque<>();

        for (PDGNode node : phase1Nodes) {
            if (node instanceof SDGCallNode) {
                SDGCallNode callNode = (SDGCallNode) node;
                SDGEntryNode targetEntry = callNode.getTargetEntry();
                if (targetEntry != null) {
                    worklist.add(targetEntry);
                }
            }
        }

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) continue;

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges()) {
                PDGNode source = edge.getSource();

                if (edge.getType() == PDGDependenceType.PARAMETER_IN) {
                    continue;
                }

                if (edge.getType() == PDGDependenceType.CALL) {
                    result.addEdge(edge);
                    continue;
                }

                if (!edge.isInterprocedural()) {
                    result.addEdge(edge);
                    if (!visited.contains(source)) {
                        worklist.add(source);
                    }
                }
            }
        }
    }

    private SliceResult contextInsensitiveBackwardSlice(PDGNode criterion) {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(criterion);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) continue;

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges()) {
                if (shouldFollowEdgeBackward(edge)) {
                    result.addEdge(edge);
                    PDGNode source = edge.getSource();
                    if (!visited.contains(source)) {
                        worklist.add(source);
                    }
                }
            }
        }

        return result;
    }

    private SliceResult contextSensitiveForwardSlice(PDGNode criterion) {
        SliceResult result = new SliceResult(SliceResult.SliceType.FORWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<SliceContext> worklist = new ArrayDeque<>();
        worklist.add(new SliceContext(criterion, new ArrayDeque<>()));

        while (!worklist.isEmpty()) {
            SliceContext ctx = worklist.poll();
            PDGNode current = ctx.node;

            if (!visited.add(current)) continue;
            result.addNode(current);

            for (PDGEdge edge : current.getOutgoingEdges()) {
                PDGNode target = edge.getTarget();

                if (edge.getType() == PDGDependenceType.CALL) {
                    Deque<SDGCallNode> newStack = new ArrayDeque<>(ctx.callStack);
                    if (current instanceof SDGCallNode) {
                        newStack.push((SDGCallNode) current);
                    }
                    result.addEdge(edge);
                    worklist.add(new SliceContext(target, newStack));
                    continue;
                }

                if (edge.getType() == PDGDependenceType.PARAMETER_OUT) {
                    if (!ctx.callStack.isEmpty()) {
                        SDGCallNode expectedCaller = ctx.callStack.peek();
                        if (target instanceof SDGActualOutNode) {
                            SDGActualOutNode actualOut = (SDGActualOutNode) target;
                            if (actualOut.getCallNode() == expectedCaller) {
                                Deque<SDGCallNode> newStack = new ArrayDeque<>(ctx.callStack);
                                newStack.pop();
                                result.addEdge(edge);
                                worklist.add(new SliceContext(target, newStack));
                            }
                        }
                    }
                    continue;
                }

                if (!edge.isInterprocedural()) {
                    result.addEdge(edge);
                    worklist.add(new SliceContext(target, ctx.callStack));
                }
            }
        }

        return result;
    }

    private SliceResult contextInsensitiveForwardSlice(PDGNode criterion) {
        SliceResult result = new SliceResult(SliceResult.SliceType.FORWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(criterion);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) continue;

            result.addNode(current);

            for (PDGEdge edge : current.getOutgoingEdges()) {
                if (shouldFollowEdgeForward(edge)) {
                    result.addEdge(edge);
                    PDGNode target = edge.getTarget();
                    if (!visited.contains(target)) {
                        worklist.add(target);
                    }
                }
            }
        }

        return result;
    }

    public SliceResult sliceWithCallingContext(PDGNode criterion, List<SDGCallNode> callingContext) {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<ContextualNode> worklist = new ArrayDeque<>();

        Deque<SDGCallNode> initialContext = new ArrayDeque<>(callingContext);
        worklist.add(new ContextualNode(criterion, initialContext));

        while (!worklist.isEmpty()) {
            ContextualNode ctx = worklist.poll();
            PDGNode current = ctx.node;

            String contextKey = current.getId() + ":" + ctx.context.hashCode();
            if (!visited.add(current)) continue;

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges()) {
                PDGNode source = edge.getSource();

                if (edge.getType() == PDGDependenceType.PARAMETER_IN) {
                    if (source instanceof SDGActualInNode) {
                        SDGActualInNode actualIn = (SDGActualInNode) source;
                        SDGCallNode callNode = actualIn.getCallNode();

                        if (!ctx.context.isEmpty() && ctx.context.peek() == callNode) {
                            Deque<SDGCallNode> newContext = new ArrayDeque<>(ctx.context);
                            newContext.pop();
                            result.addEdge(edge);
                            worklist.add(new ContextualNode(source, newContext));
                        }
                    }
                    continue;
                }

                if (edge.getType() == PDGDependenceType.CALL) {
                    if (source instanceof SDGCallNode) {
                        Deque<SDGCallNode> newContext = new ArrayDeque<>(ctx.context);
                        newContext.push((SDGCallNode) source);
                        result.addEdge(edge);
                        worklist.add(new ContextualNode(source, newContext));
                    }
                    continue;
                }

                if (!edge.isInterprocedural()) {
                    result.addEdge(edge);
                    worklist.add(new ContextualNode(source, ctx.context));
                }
            }
        }

        return result;
    }

    public List<List<SDGCallNode>> findCallingContexts(PDGNode node, int maxDepth) {
        List<List<SDGCallNode>> contexts = new ArrayList<>();
        findContextsDFS(node, new ArrayList<>(), contexts, new HashSet<>(), maxDepth);
        return contexts;
    }

    private void findContextsDFS(PDGNode current, List<SDGCallNode> currentContext,
                                 List<List<SDGCallNode>> allContexts,
                                 Set<PDGNode> visited, int remainingDepth) {
        if (remainingDepth < 0) return;
        if (!visited.add(current)) return;

        if (current instanceof SDGEntryNode) {
            SDGEntryNode entry = (SDGEntryNode) current;
            MethodReference methodRef = entry.getMethodRef();
            Set<SDGCallNode> callSites = sdg.getCallSitesTo(methodRef);

            if (callSites.isEmpty()) {
                allContexts.add(new ArrayList<>(currentContext));
            } else {
                for (SDGCallNode callSite : callSites) {
                    List<SDGCallNode> newContext = new ArrayList<>(currentContext);
                    newContext.add(0, callSite);
                    findContextsDFS(callSite, newContext, allContexts, new HashSet<>(visited), remainingDepth - 1);
                }
            }
        } else {
            for (PDGEdge edge : current.getIncomingEdges()) {
                if (edge.getType() == PDGDependenceType.CALL) {
                    PDGNode source = edge.getSource();
                    if (source instanceof SDGCallNode) {
                        List<SDGCallNode> newContext = new ArrayList<>(currentContext);
                        newContext.add(0, (SDGCallNode) source);
                        findContextsDFS(source, newContext, allContexts, new HashSet<>(visited), remainingDepth - 1);
                    }
                }
            }
        }

        visited.remove(current);
    }

    private boolean shouldFollowEdgeBackward(PDGEdge edge) {
        if (useSummaryEdges && edge.getType() == PDGDependenceType.SUMMARY) {
            return true;
        }
        return edge.getType() != PDGDependenceType.RETURN;
    }

    private boolean shouldFollowEdgeForward(PDGEdge edge) {
        if (useSummaryEdges && edge.getType() == PDGDependenceType.SUMMARY) {
            return true;
        }
        return edge.getType() != PDGDependenceType.PARAMETER_IN;
    }

    private static class SliceContext {
        final PDGNode node;
        final Deque<SDGCallNode> callStack;

        SliceContext(PDGNode node, Deque<SDGCallNode> callStack) {
            this.node = node;
            this.callStack = callStack;
        }
    }

    private static class ContextualNode {
        final PDGNode node;
        final Deque<SDGCallNode> context;

        ContextualNode(PDGNode node, Deque<SDGCallNode> context) {
            this.node = node;
            this.context = context;
        }
    }
}
