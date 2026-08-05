package com.tonic.analysis.cpg.query;

import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fluent, stream-backed traversal over a CPG; each step returns a new query and the
 * underlying stream is single-use, so a query chain terminates exactly once.
 */
public class CPGQuery
{

    private final CodePropertyGraph cpg;
    private final Stream<CPGNode> currentNodes;

    /**
     * Creates an empty query over a graph; select a starting set with methods like
     * {@link #methods()} or {@link #all()}.
     * @param cpg the graph to query
     */
    public CPGQuery(CodePropertyGraph cpg)
    {
        this.cpg = cpg;
        this.currentNodes = Stream.empty();
    }

    private CPGQuery(CodePropertyGraph cpg, Stream<CPGNode> nodes)
    {
        this.cpg = cpg;
        this.currentNodes = nodes;
    }

    /**
     * @return the cpg
     */
    public CodePropertyGraph getCpg()
    {
        return cpg;
    }

    /**
     * @return the current nodes
     */
    public Stream<CPGNode> getCurrentNodes()
    {
        return currentNodes;
    }

    /**
     * Selects all method nodes.
     * @return a query positioned on every method
     */
    public CPGQuery methods()
    {
        return new CPGQuery(cpg, cpg.nodes(MethodNode.class).map(n -> n));
    }

    /**
     * Selects method nodes whose name matches a regex.
     * @param namePattern the regex the method name must match in full
     * @return a query positioned on the matching methods
     */
    public CPGQuery methods(String namePattern)
    {
        Pattern p = Pattern.compile(namePattern);
        return new CPGQuery(cpg, cpg.nodes(MethodNode.class)
            .filter(m -> p.matcher(m.getName()).matches())
            .map(n -> n));
    }

    /**
     * Selects a single method by exact signature.
     * @param owner the declaring class internal name
     * @param name the method name
     * @param descriptor the method descriptor
     * @return a query positioned on the method, or an empty query
     */
    public CPGQuery method(String owner, String name, String descriptor)
    {
        return cpg.getMethod(owner, name, descriptor)
            .map(m -> new CPGQuery(cpg, Stream.of(m)))
            .orElse(new CPGQuery(cpg, Stream.empty()));
    }

    /**
     * Selects all instruction nodes.
     * @return a query positioned on every instruction
     */
    public CPGQuery instructions()
    {
        return new CPGQuery(cpg, cpg.nodes(InstructionNode.class).map(n -> n));
    }

    /**
     * Selects instruction nodes wrapping a specific IR instruction class.
     * @param type the IR instruction class
     * @return a query positioned on the matching instructions
     */
    public CPGQuery instructions(Class<? extends IRInstruction> type)
    {
        return new CPGQuery(cpg, cpg.getInstructionsOfType(type).map(n -> n));
    }

    /**
     * Selects all call site nodes.
     * @return a query positioned on every call site
     */
    public CPGQuery callSites()
    {
        return new CPGQuery(cpg, cpg.nodes(CallSiteNode.class).map(n -> n));
    }

    /**
     * Selects call sites targeting a method name regardless of descriptor.
     * @param owner the target class internal name
     * @param method the target method name
     * @return a query positioned on the matching call sites
     */
    public CPGQuery callsTo(String owner, String method)
    {
        return new CPGQuery(cpg, cpg.getCallsTo(owner, method).map(n -> n));
    }

    /**
     * Selects call sites targeting an exact method signature.
     * @param owner the target class internal name
     * @param method the target method name
     * @param descriptor the target method descriptor
     * @return a query positioned on the matching call sites
     */
    public CPGQuery callsTo(String owner, String method, String descriptor)
    {
        return new CPGQuery(cpg, cpg.getCallsTo(owner, method, descriptor).map(n -> n));
    }

    /**
     * Selects all basic block nodes.
     * @return a query positioned on every block
     */
    public CPGQuery blocks()
    {
        return new CPGQuery(cpg, cpg.nodes(BlockNode.class).map(n -> n));
    }

    /**
     * Selects every node in the graph.
     * @return a query positioned on all nodes
     */
    public CPGQuery all()
    {
        return new CPGQuery(cpg, cpg.getAllNodes().stream());
    }

    /**
     * Steps along outgoing edges to their targets.
     * @param edgeTypes the edge types to follow; empty follows every edge
     * @return a query positioned on the reached nodes
     */
    public CPGQuery out(CPGEdgeType... edgeTypes)
    {
        Set<CPGEdgeType> types = Set.of(edgeTypes);
        return new CPGQuery(cpg, currentNodes.flatMap(node ->
            node.getOutgoingEdges().stream()
                .filter(e -> types.isEmpty() || types.contains(e.getType()))
                .map(CPGEdge::getTarget)));
    }

    /**
     * Steps along incoming edges to their sources.
     * @param edgeTypes the edge types to follow; empty follows every edge
     * @return a query positioned on the reached nodes
     */
    public CPGQuery in(CPGEdgeType... edgeTypes)
    {
        Set<CPGEdgeType> types = Set.of(edgeTypes);
        return new CPGQuery(cpg, currentNodes.flatMap(node ->
            node.getIncomingEdges().stream()
                .filter(e -> types.isEmpty() || types.contains(e.getType()))
                .map(CPGEdge::getSource)));
    }

    /**
     * Steps along edges in both directions.
     * @param edgeTypes the edge types to follow; empty follows every edge
     * @return a query positioned on the reached nodes
     */
    public CPGQuery both(CPGEdgeType... edgeTypes)
    {
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

    /**
     * Steps to direct control-flow successors.
     * @return a query positioned on the successor nodes
     */
    public CPGQuery cfgNext()
    {
        return out(CPGEdgeType.CFG_NEXT, CPGEdgeType.CFG_TRUE, CPGEdgeType.CFG_FALSE);
    }

    /**
     * Steps to direct control-flow predecessors.
     * @return a query positioned on the predecessor nodes
     */
    public CPGQuery cfgPrev()
    {
        return in(CPGEdgeType.CFG_NEXT, CPGEdgeType.CFG_TRUE, CPGEdgeType.CFG_FALSE);
    }

    /**
     * Expands to every node transitively reachable along control-flow edges,
     * including the starting nodes.
     * @return a query positioned on the reachable nodes
     */
    public CPGQuery cfgReachable()
    {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> reachable = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty())
            {
                CPGNode current = worklist.poll();
                if (!reachable.add(current)) continue;

                for (CPGEdge edge : current.getOutgoingEdges())
                {
                    if (edge.getType().isCFGEdge())
                    {
                        worklist.add(edge.getTarget());
                    }
                }
            }
            return reachable.stream();
        }));
    }

    /**
     * Steps to AST parents.
     * @return a query positioned on the parent nodes
     */
    public CPGQuery astParent()
    {
        return in(CPGEdgeType.AST_CHILD);
    }

    /**
     * Steps to direct AST children.
     * @return a query positioned on the child nodes
     */
    public CPGQuery astChildren()
    {
        return out(CPGEdgeType.AST_CHILD);
    }

    /**
     * Expands to every transitive AST descendant, excluding the starting nodes.
     * @return a query positioned on the descendant nodes
     */
    public CPGQuery astDescendants()
    {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> descendants = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty())
            {
                CPGNode current = worklist.poll();
                for (CPGEdge edge : current.getOutgoingEdges())
                {
                    if (edge.getType() == CPGEdgeType.AST_CHILD)
                    {
                        if (descendants.add(edge.getTarget()))
                        {
                            worklist.add(edge.getTarget());
                        }
                    }
                }
            }
            return descendants.stream();
        }));
    }

    /**
     * Steps backwards along data-flow edges to definition sources.
     * @return a query positioned on the source nodes
     */
    public CPGQuery dataFlowIn()
    {
        return in(CPGEdgeType.DATA_DEF, CPGEdgeType.DATA_USE, CPGEdgeType.REACHING_DEF);
    }

    /**
     * Steps forwards along data-flow edges to dependent uses.
     * @return a query positioned on the dependent nodes
     */
    public CPGQuery dataFlowOut()
    {
        return out(CPGEdgeType.DATA_DEF, CPGEdgeType.DATA_USE, CPGEdgeType.REACHING_DEF);
    }

    /**
     * Steps to nodes control-dependent on the current ones.
     * @return a query positioned on the dependent nodes
     */
    public CPGQuery controlDependents()
    {
        return out(CPGEdgeType.CONTROL_DEP, CPGEdgeType.CONTROL_DEP_TRUE, CPGEdgeType.CONTROL_DEP_FALSE);
    }

    /**
     * Steps to direct callers along call edges.
     * @return a query positioned on the calling nodes
     */
    public CPGQuery callers()
    {
        return in(CPGEdgeType.CALL);
    }

    /**
     * Steps to direct callees along call edges.
     * @return a query positioned on the called nodes
     */
    public CPGQuery callees()
    {
        return out(CPGEdgeType.CALL);
    }

    /**
     * Expands to every transitive caller, excluding the starting nodes.
     * @return a query positioned on the calling nodes
     */
    public CPGQuery callersTransitive()
    {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> callers = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty())
            {
                CPGNode current = worklist.poll();
                for (CPGEdge edge : current.getIncomingEdges())
                {
                    if (edge.getType() == CPGEdgeType.CALL)
                    {
                        if (callers.add(edge.getSource()))
                        {
                            worklist.add(edge.getSource());
                        }
                    }
                }
            }
            return callers.stream();
        }));
    }

    /**
     * Expands to every transitive callee, excluding the starting nodes.
     * @return a query positioned on the called nodes
     */
    public CPGQuery calleesTransitive()
    {
        return new CPGQuery(cpg, currentNodes.flatMap(start -> {
            Set<CPGNode> callees = new LinkedHashSet<>();
            Deque<CPGNode> worklist = new ArrayDeque<>();
            worklist.add(start);

            while (!worklist.isEmpty())
            {
                CPGNode current = worklist.poll();
                for (CPGEdge edge : current.getOutgoingEdges())
                {
                    if (edge.getType() == CPGEdgeType.CALL)
                    {
                        if (callees.add(edge.getTarget()))
                        {
                            worklist.add(edge.getTarget());
                        }
                    }
                }
            }
            return callees.stream();
        }));
    }

    /**
     * Keeps only nodes matching a predicate.
     * @param predicate the node test
     * @return a query positioned on the matching nodes
     */
    public CPGQuery filter(Predicate<CPGNode> predicate)
    {
        return new CPGQuery(cpg, currentNodes.filter(predicate));
    }

    /**
     * Keeps only nodes of the given node types.
     * @param types the accepted node types
     * @return a query positioned on the matching nodes
     */
    public CPGQuery filterType(CPGNodeType... types)
    {
        Set<CPGNodeType> typeSet = Set.of(types);
        return new CPGQuery(cpg, currentNodes.filter(n -> typeSet.contains(n.getNodeType())));
    }

    /**
     * Keeps only nodes carrying a property.
     * @param key the property key
     * @return a query positioned on the matching nodes
     */
    public CPGQuery hasProperty(String key)
    {
        return new CPGQuery(cpg, currentNodes.filter(n -> n.hasProperty(key)));
    }

    /**
     * Keeps only nodes carrying a property with the given value.
     * @param key the property key
     * @param value the required value
     * @return a query positioned on the matching nodes
     */
    public CPGQuery hasProperty(String key, Object value)
    {
        return new CPGQuery(cpg, currentNodes.filter(n ->
            n.hasProperty(key) && Objects.equals(n.getProperty(key), value)));
    }

    /**
     * Keeps only nodes for which the sub-query yields a result.
     * @param subQuery the existence condition
     * @return a query positioned on the matching nodes
     */
    public CPGQuery where(CPGQuery subQuery)
    {
        List<CPGNode> collected = currentNodes.collect(Collectors.toList());
        return new CPGQuery(cpg, collected.stream().filter(node -> {
            CPGQuery nodeQuery = new CPGQuery(cpg, Stream.of(node));
            return nodeQuery.exists();
        }));
    }

    /**
     * Keeps only nodes for which the sub-query yields no result.
     * @param subQuery the absence condition
     * @return a query positioned on the matching nodes
     */
    public CPGQuery whereNot(CPGQuery subQuery)
    {
        List<CPGNode> collected = currentNodes.collect(Collectors.toList());
        return new CPGQuery(cpg, collected.stream().filter(node -> {
            CPGQuery nodeQuery = new CPGQuery(cpg, Stream.of(node));
            return !nodeQuery.exists();
        }));
    }

    /**
     * Keeps only call site nodes.
     * @return a query positioned on the matching nodes
     */
    public CPGQuery isMethodCall()
    {
        return filterType(CPGNodeType.CALL_SITE);
    }

    /**
     * Keeps only field access instructions.
     * @return a query positioned on the matching nodes
     */
    public CPGQuery isFieldAccess()
    {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isFieldAccess());
    }

    /**
     * Keeps only object or array allocation instructions.
     * @return a query positioned on the matching nodes
     */
    public CPGQuery isAllocation()
    {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isAllocation());
    }

    /**
     * Keeps only return instructions.
     * @return a query positioned on the matching nodes
     */
    public CPGQuery isReturn()
    {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isReturn());
    }

    /**
     * Keeps only conditional branch instructions.
     * @return a query positioned on the matching nodes
     */
    public CPGQuery isBranch()
    {
        return filter(n -> n instanceof InstructionNode && ((InstructionNode) n).isBranch());
    }

    /**
     * Keeps only nodes whose name property matches a regex.
     * @param regex the pattern the name must match in full
     * @return a query positioned on the matching nodes
     */
    public CPGQuery nameMatches(String regex)
    {
        Pattern p = Pattern.compile(regex);
        return filter(n -> {
            Object name = n.getProperty("name");
            return name != null && p.matcher(name.toString()).matches();
        });
    }

    /**
     * Keeps only nodes whose owner or target owner property matches a regex.
     * @param regex the pattern the owner must match in full
     * @return a query positioned on the matching nodes
     */
    public CPGQuery ownerMatches(String regex)
    {
        Pattern p = Pattern.compile(regex);
        return filter(n -> {
            Object owner = n.getProperty("owner");
            if (owner != null && p.matcher(owner.toString()).matches()) return true;
            Object targetOwner = n.getProperty("targetOwner");
            return targetOwner != null && p.matcher(targetOwner.toString()).matches();
        });
    }

    /**
     * Truncates the result to at most n nodes.
     * @param n the maximum number of nodes to keep
     * @return a query positioned on the truncated set
     */
    public CPGQuery limit(int n)
    {
        return new CPGQuery(cpg, currentNodes.limit(n));
    }

    /**
     * Drops the first n nodes.
     * @param n the number of nodes to skip
     * @return a query positioned on the remaining nodes
     */
    public CPGQuery skip(int n)
    {
        return new CPGQuery(cpg, currentNodes.skip(n));
    }

    /**
     * Removes duplicate nodes from the result.
     * @return a query positioned on the distinct nodes
     */
    public CPGQuery dedup()
    {
        return new CPGQuery(cpg, currentNodes.distinct());
    }

    /**
     * Terminates the query by mapping each node to a value.
     * @param mapper the node transform
     * @param <T> the result element type
     * @return a stream of mapped values
     */
    public <T> Stream<T> map(Function<CPGNode, T> mapper)
    {
        return currentNodes.map(mapper);
    }

    /**
     * Replaces each node with the nodes produced by the mapper.
     * @param mapper the node expansion
     * @return a query positioned on the produced nodes
     */
    public CPGQuery flatMap(Function<CPGNode, Stream<CPGNode>> mapper)
    {
        return new CPGQuery(cpg, currentNodes.flatMap(mapper));
    }

    /**
     * @return the current node stream
     */
    public Stream<CPGNode> toStream()
    {
        return currentNodes;
    }

    /**
     * Terminates the query into a list.
     * @return the result nodes in encounter order
     */
    public List<CPGNode> toList()
    {
        return currentNodes.collect(Collectors.toList());
    }

    /**
     * Terminates the query into an insertion-ordered set.
     * @return the distinct result nodes
     */
    public Set<CPGNode> toSet()
    {
        return currentNodes.collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Terminates the query with its first node.
     * @return the first result node, if any
     */
    public Optional<CPGNode> first()
    {
        return currentNodes.findFirst();
    }

    /**
     * Terminates the query by counting its nodes.
     * @return the number of result nodes
     */
    public long count()
    {
        return currentNodes.count();
    }

    /**
     * Terminates the query by testing for any result.
     * @return whether the query matched at least one node
     */
    public boolean exists()
    {
        return currentNodes.findAny().isPresent();
    }

    /**
     * Terminates the query by applying an action to each node.
     * @param action the action to run per node
     */
    public void forEach(Consumer<CPGNode> action)
    {
        currentNodes.forEach(action);
    }
}
