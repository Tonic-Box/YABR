package com.tonic.analysis.callgraph;

import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a call graph for a set of classes.
 * Provides queries for caller/callee relationships, reachability, and dead code detection.
 */
public class CallGraph {

    private final Map<MethodReference, CallGraphNode> nodes = new LinkedHashMap<>();
    private final ClassHierarchy hierarchy;
    private final ClassPool classPool;

    CallGraph(ClassPool classPool, ClassHierarchy hierarchy) {
        this.classPool = classPool;
        this.hierarchy = hierarchy;
    }

    /**
     * Builds a call graph from the given ClassPool.
     */
    public static CallGraph build(ClassPool classPool) {
        return CallGraphBuilder.build(classPool);
    }

    /**
     * Gets or creates a node for the given method reference.
     */
    CallGraphNode getOrCreateNode(MethodReference ref, MethodEntry entry) {
        return nodes.computeIfAbsent(ref, r -> new CallGraphNode(r, entry));
    }

    /**
     * Gets the node for a method reference, or null if not found.
     */
    public CallGraphNode getNode(MethodReference ref) {
        return nodes.get(ref);
    }

    /**
     * Gets the node for a method by owner, name, and descriptor.
     */
    public CallGraphNode getNode(String owner, String name, String descriptor) {
        return nodes.get(new MethodReference(owner, name, descriptor));
    }

    /**
     * Gets the node for a MethodEntry.
     */
    public CallGraphNode getNode(MethodEntry method) {
        return getNode(method.getOwnerName(), method.getName(), method.getDesc());
    }

    /**
     * Returns all nodes in the call graph.
     */
    public Collection<CallGraphNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Returns only nodes that are in the ClassPool (not external references).
     */
    public Collection<CallGraphNode> getPoolNodes() {
        return nodes.values().stream()
                .filter(CallGraphNode::isInPool)
                .collect(Collectors.toList());
    }

    /**
     * Gets all methods that call the specified method.
     */
    public Set<MethodReference> getCallers(MethodReference method) {
        CallGraphNode node = nodes.get(method);
        if (node == null) return Collections.emptySet();
        return node.getCallers();
    }

    /**
     * Gets all methods that call the specified method.
     */
    public Set<MethodReference> getCallers(String owner, String name, String descriptor) {
        return getCallers(new MethodReference(owner, name, descriptor));
    }

    /**
     * Gets all methods that call the specified method entry.
     */
    public Set<MethodReference> getCallers(MethodEntry method) {
        return getCallers(method.getOwnerName(), method.getName(), method.getDesc());
    }

    /**
     * Gets all methods called by the specified method.
     */
    public Set<MethodReference> getCallees(MethodReference method) {
        CallGraphNode node = nodes.get(method);
        if (node == null) return Collections.emptySet();
        return node.getCallees();
    }

    /**
     * Gets all methods called by the specified method.
     */
    public Set<MethodReference> getCallees(String owner, String name, String descriptor) {
        return getCallees(new MethodReference(owner, name, descriptor));
    }

    /**
     * Gets all methods called by the specified method entry.
     */
    public Set<MethodReference> getCallees(MethodEntry method) {
        return getCallees(method.getOwnerName(), method.getName(), method.getDesc());
    }

    /**
     * Gets all call sites where the specified method is called.
     */
    public Set<CallSite> getCallSitesFor(MethodReference method) {
        CallGraphNode node = nodes.get(method);
        if (node == null) return Collections.emptySet();
        return node.getIncomingCalls();
    }

    /**
     * Gets all call sites made from the specified method.
     */
    public Set<CallSite> getCallSitesFrom(MethodReference method) {
        CallGraphNode node = nodes.get(method);
        if (node == null) return Collections.emptySet();
        return node.getOutgoingCalls();
    }

    /**
     * Computes all methods reachable from the given entry points.
     * This is useful for dead code detection.
     *
     * @param entryPoints the methods to start from
     * @return all methods reachable via the call graph
     */
    public Set<MethodReference> getReachableFrom(Collection<MethodReference> entryPoints) {
        Set<MethodReference> reachable = new LinkedHashSet<>();
        Deque<MethodReference> worklist = new ArrayDeque<>(entryPoints);

        while (!worklist.isEmpty()) {
            MethodReference current = worklist.poll();
            if (!reachable.add(current)) continue;

            CallGraphNode node = nodes.get(current);
            if (node != null) {
                for (MethodReference callee : node.getCallees()) {
                    if (!reachable.contains(callee)) {
                        worklist.add(callee);
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * Computes all methods reachable from main methods and static initializers.
     */
    public Set<MethodReference> getReachableFromMainEntryPoints() {
        List<MethodReference> entryPoints = new ArrayList<>();

        for (CallGraphNode node : nodes.values()) {
            if (!node.isInPool()) continue;
            MethodReference ref = node.getReference();

            // Main methods
            if ("main".equals(ref.getName()) && "([Ljava/lang/String;)V".equals(ref.getDescriptor())) {
                entryPoints.add(ref);
            }
            // Static initializers
            if ("<clinit>".equals(ref.getName())) {
                entryPoints.add(ref);
            }
        }

        return getReachableFrom(entryPoints);
    }

    /**
     * Finds all methods that are not reachable from the given entry points.
     * These are potentially dead methods.
     *
     * @param entryPoints the methods to start from
     * @return methods that are not reachable
     */
    public Set<MethodReference> getUnreachableFrom(Collection<MethodReference> entryPoints) {
        Set<MethodReference> reachable = getReachableFrom(entryPoints);
        Set<MethodReference> unreachable = new LinkedHashSet<>();

        for (CallGraphNode node : nodes.values()) {
            if (node.isInPool() && !reachable.contains(node.getReference())) {
                unreachable.add(node.getReference());
            }
        }

        return unreachable;
    }

    /**
     * Finds methods with no callers (potential dead code).
     * Excludes constructors, static initializers, and main methods.
     */
    public Set<MethodReference> findMethodsWithNoCallers() {
        Set<MethodReference> noCaller = new LinkedHashSet<>();

        for (CallGraphNode node : nodes.values()) {
            if (!node.isInPool()) continue;
            if (node.hasCaller()) continue;

            MethodReference ref = node.getReference();

            // Skip constructors and static initializers
            if (ref.isConstructor() || ref.isStaticInitializer()) continue;

            // Skip main methods
            if ("main".equals(ref.getName()) && "([Ljava/lang/String;)V".equals(ref.getDescriptor())) {
                continue;
            }

            noCaller.add(ref);
        }

        return noCaller;
    }

    /**
     * Finds all methods matching a predicate.
     */
    public Set<MethodReference> findMethods(Predicate<CallGraphNode> predicate) {
        return nodes.values().stream()
                .filter(predicate)
                .map(CallGraphNode::getReference)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Checks if method A calls method B (directly).
     */
    public boolean calls(MethodReference caller, MethodReference callee) {
        CallGraphNode node = nodes.get(caller);
        if (node == null) return false;
        return node.getCallees().contains(callee);
    }

    /**
     * Checks if method A can reach method B (transitively).
     */
    public boolean canReach(MethodReference from, MethodReference to) {
        return getReachableFrom(Collections.singleton(from)).contains(to);
    }

    /**
     * Gets all possible targets for a virtual/interface call.
     * Uses the class hierarchy to resolve polymorphic dispatch.
     *
     * @param owner      the declared owner class
     * @param name       the method name
     * @param descriptor the method descriptor
     * @return all possible implementation methods
     */
    public Set<MethodReference> resolveVirtualTargets(String owner, String name, String descriptor) {
        Set<MethodReference> targets = new LinkedHashSet<>();

        if (hierarchy == null) {
            // Without hierarchy, just return the declared target
            targets.add(new MethodReference(owner, name, descriptor));
            return targets;
        }

        // Find all classes in the hierarchy that might implement this method
        Set<ClassNode> methodClasses = hierarchy.findMethodHierarchy(owner, name, descriptor);
        for (ClassNode classNode : methodClasses) {
            if (classNode.isInPool()) {
                MethodEntry method = hierarchy.getMethod(classNode.getName(), name, descriptor);
                if (method != null) {
                    targets.add(new MethodReference(classNode.getName(), name, descriptor));
                }
            }
        }

        // If no implementations found, add the declared target
        if (targets.isEmpty()) {
            targets.add(new MethodReference(owner, name, descriptor));
        }

        return targets;
    }

    /**
     * Gets the class hierarchy used by this call graph.
     */
    public ClassHierarchy getHierarchy() {
        return hierarchy;
    }

    /**
     * Gets the ClassPool this call graph was built from.
     */
    public ClassPool getClassPool() {
        return classPool;
    }

    /**
     * Returns the number of methods in the call graph.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Returns the number of call edges in the graph.
     */
    public int edgeCount() {
        int count = 0;
        for (CallGraphNode node : nodes.values()) {
            count += node.getOutgoingCalls().size();
        }
        return count;
    }

    @Override
    public String toString() {
        long poolCount = nodes.values().stream().filter(CallGraphNode::isInPool).count();
        return "CallGraph{methods=" + nodes.size() + ", inPool=" + poolCount + ", edges=" + edgeCount() + "}";
    }
}
