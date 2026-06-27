package com.tonic.analysis.cpg.query;

import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.ssa.ir.IRInstruction;
import lombok.Getter;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class CPGQuery {

    private final CodePropertyGraph cpg;
    private final Stream<CPGNode> currentNodes;

    public CPGQuery(CodePropertyGraph cpg) {
        this.cpg = cpg;
        this.currentNodes = Stream.empty();
    }

    private CPGQuery(CodePropertyGraph cpg, Stream<CPGNode> nodes) {
        this.cpg = cpg;
        this.currentNodes = nodes;
    }

    public CPGQuery methods() {
        return new CPGQuery(cpg, cpg.nodes(MethodNode.class).map(n -> n));
    }

    public CPGQuery methods(String namePattern) {
        Pattern p = Pattern.compile(namePattern);
        return new CPGQuery(cpg, cpg.nodes(MethodNode.class)
            .filter(m -> p.matcher(m.getName()).matches())
            .map(n -> n));
    }

    public CPGQuery method(String owner, String name, String descriptor) {
        return cpg.getMethod(owner, name, descriptor)
            .map(m -> new CPGQuery(cpg, Stream.of(m)))
            .orElse(new CPGQuery(cpg, Stream.empty()));
    }

    public CPGQuery instructions() {
        return new CPGQuery(cpg, cpg.nodes(InstructionNode.class).map(n -> n));
    }

    public CPGQuery instructions(Class<? extends IRInstruction> type) {
        return new CPGQuery(cpg, cpg.getInstructionsOfType(type).map(n -> n));
    }

    public CPGQuery callSites() {
        return new CPGQuery(cpg, cpg.nodes(CallSiteNode.class).map(n -> n));
    }

    public CPGQuery callsTo(String owner, String method) {
        return new CPGQuery(cpg, cpg.getCallsTo(owner, method).map(n -> n));
    }

    public CPGQuery callsTo(String owner, String method, String descriptor) {
        return new CPGQuery(cpg, cpg.getCallsTo(owner, method, descriptor).map(n -> n));
    }

    public CPGQuery blocks() {
        return new CPGQuery(cpg, cpg.nodes(BlockNode.class).map(n -> n));
    }

    public CPGQuery all() {
        return new CPGQuery(cpg, cpg.getAllNodes().stream());
    }

    public CPGQuery out(CPGEdgeType... edgeTypes) {
        Set<CPGEdgeType> types = Set.of(edgeTypes);
        return new CPGQuery(cpg, currentNodes.flatMap(node ->
            node.getOutgoingEdges().stream()
                .filter(e -> types.isEmpty() || types.contains(e.getType()))
                .map(CPGEdge::getTarget)));
    }

    public CPGQuery in(CPGEdgeType... edgeTypes) {
        Set<CPGEdgeType> types = Set.of(edgeTypes);
        return new CPGQuery(cpg, currentNodes.flatMap(node ->
            node.getIncomingEdges().stream()
                .filter(e -> types.isEmpty() || types.contains(e.getType()))
                .map(CPGEdge::getSource)));
    }

    public CPGQuery both(CPGEdgeType... edgeTypes) {
        Set<CPGEdgeType> types = Set.of(edgeTypes);
        return new CPGQuery(cpg, currentNodes.flatMap(node -> {
            Stream<CPGNode> outNodes = node.getOutgoingEdges().stream()
                .filter(e -> types.isEmpty() || types.contains(e.getType()))
                .map(CPGEdge::getTarget);
            Stream<CPGNode> inNodes = node.getIncomingEdges().stream()
                .filter(e -> types.isEmpty() || types.contains(e.getType()))
                .map(CPGEdge::getSource);
            return Stream.concat(outNodes, inNodes);
        }));
    }

    public CPGQuery cfgNext() {
        return out(CPGEdgeType.CFG_NEXT, CPGEdgeType.CFG_TRUE, CPGEdgeType.CFG_FALSE);
    }

    public CPGQuery cfgPrev() {
        return in(CPGEdgeType.CFG_NEXT, CPGEdgeType.CFG_TRUE, CPGEdgeType.CFG_FALSE);
    }

    public CPGQuery cfgReachable() {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> reachable = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty()) {
                CPGNode current = worklist.poll();
                if (!reachable.add(current)) continue;

                for (CPGEdge edge : current.getOutgoingEdges()) {
                    if (edge.getType().isCFGEdge()) {
                        worklist.add(edge.getTarget());
                    }
                }
            }
            return reachable.stream();
        }));
    }

    public CPGQuery astParent() {
        return in(CPGEdgeType.AST_CHILD);
    }

    public CPGQuery astChildren() {
        return out(CPGEdgeType.AST_CHILD);
    }

    public CPGQuery astDescendants() {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> descendants = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty()) {
                CPGNode current = worklist.poll();
                for (CPGEdge edge : current.getOutgoingEdges()) {
                    if (edge.getType() == CPGEdgeType.AST_CHILD) {
                        if (descendants.add(edge.getTarget())) {
                            worklist.add(edge.getTarget());
                        }
                    }
                }
            }
            return descendants.stream();
        }));
    }

    public CPGQuery dataFlowIn() {
        return in(CPGEdgeType.DATA_DEF, CPGEdgeType.DATA_USE, CPGEdgeType.REACHING_DEF);
    }

    public CPGQuery dataFlowOut() {
        return out(CPGEdgeType.DATA_DEF, CPGEdgeType.DATA_USE, CPGEdgeType.REACHING_DEF);
    }

    public CPGQuery controlDependents() {
        return out(CPGEdgeType.CONTROL_DEP, CPGEdgeType.CONTROL_DEP_TRUE, CPGEdgeType.CONTROL_DEP_FALSE);
    }

    public CPGQuery callers() {
        return in(CPGEdgeType.CALL);
    }

    public CPGQuery callees() {
        return out(CPGEdgeType.CALL);
    }

    public CPGQuery callersTransitive() {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> callers = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty()) {
                CPGNode current = worklist.poll();
                for (CPGEdge edge : current.getIncomingEdges()) {
                    if (edge.getType() == CPGEdgeType.CALL) {
                        if (callers.add(edge.getSource())) {
                            worklist.add(edge.getSource());
                        }
                    }
                }
            }
            return callers.stream();
        }));
    }

    public CPGQuery calleesTransitive() {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> callees = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty()) {
                CPGNode current = worklist.poll();
                for (CPGEdge edge : current.getOutgoingEdges()) {
                    if (edge.getType() == CPGEdgeType.CALL) {
                        if (callees.add(edge.getTarget())) {
                            worklist.add(edge.getTarget());
                        }
                    }
                }
            }
            return callees.stream();
        }));
    }

    public CPGQuery filter(Predicate<CPGNode> predicate) {
        return new CPGQuery(cpg, currentNodes.filter(predicate));
    }

    public CPGQuery filterType(CPGNodeType... types) {
        Set<CPGNodeType> typeSet = Set.of(types);
        return new CPGQuery(cpg, currentNodes.filter(n -> typeSet.contains(n.getNodeType())));
    }

    public CPGQuery hasProperty(String key) {
        return new CPGQuery(cpg, currentNodes.filter(n -> n.hasProperty(key)));
    }

    public CPGQuery hasProperty(String key, Object value) {
        return new CPGQuery(cpg, currentNodes.filter(n ->
            n.hasProperty(key) && Objects.equals(n.getProperty(key), value)));
    }

    public CPGQuery where(CPGQuery subQuery) {
        List<CPGNode> collected = currentNodes.collect(Collectors.toList());
        return new CPGQuery(cpg, collected.stream().filter(node -> {
            CPGQuery nodeQuery = new CPGQuery(cpg, Stream.of(node));
            return nodeQuery.exists();
        }));
    }

    public CPGQuery whereNot(CPGQuery subQuery) {
        List<CPGNode> collected = currentNodes.collect(Collectors.toList());
        return new CPGQuery(cpg, collected.stream().filter(node -> {
            CPGQuery nodeQuery = new CPGQuery(cpg, Stream.of(node));
            return !nodeQuery.exists();
        }));
    }

    public CPGQuery isMethodCall() {
        return filterType(CPGNodeType.CALL_SITE);
    }

    public CPGQuery isFieldAccess() {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isFieldAccess());
    }

    public CPGQuery isAllocation() {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isAllocation());
    }

    public CPGQuery isReturn() {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isReturn());
    }

    public CPGQuery isBranch() {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isBranch());
    }

    public CPGQuery nameMatches(String regex) {
        Pattern p = Pattern.compile(regex);
        return filter(n -> {
            Object name = n.getProperty("name");
            return name != null && p.matcher(name.toString()).matches();
        });
    }

    public CPGQuery ownerMatches(String regex) {
        Pattern p = Pattern.compile(regex);
        return filter(n -> {
            Object owner = n.getProperty("owner");
            if (owner != null && p.matcher(owner.toString()).matches()) return true;
            Object targetOwner = n.getProperty("targetOwner");
            return targetOwner != null && p.matcher(targetOwner.toString()).matches();
        });
    }

    public CPGQuery limit(int n) {
        return new CPGQuery(cpg, currentNodes.limit(n));
    }

    public CPGQuery skip(int n) {
        return new CPGQuery(cpg, currentNodes.skip(n));
    }

    public CPGQuery dedup() {
        return new CPGQuery(cpg, currentNodes.distinct());
    }

    public <T> Stream<T> map(Function<CPGNode, T> mapper) {
        return currentNodes.map(mapper);
    }

    public CPGQuery flatMap(Function<CPGNode, Stream<CPGNode>> mapper) {
        return new CPGQuery(cpg, currentNodes.flatMap(mapper));
    }

    public Stream<CPGNode> toStream() {
        return currentNodes;
    }

    public List<CPGNode> toList() {
        return currentNodes.collect(Collectors.toList());
    }

    public Set<CPGNode> toSet() {
        return currentNodes.collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Optional<CPGNode> first() {
        return currentNodes.findFirst();
    }

    public long count() {
        return currentNodes.count();
    }

    public boolean exists() {
        return currentNodes.findAny().isPresent();
    }

    public void forEach(Consumer<CPGNode> action) {
        currentNodes.forEach(action);
    }
}
