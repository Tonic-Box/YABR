package com.tonic.analysis.callgraph;

import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Represents a node in the call graph, corresponding to a single method.
 * Tracks both callers (methods that call this method) and callees (methods this method calls).
 */
public class CallGraphNode {

    private final MethodReference reference;
    private final MethodEntry methodEntry;
    private final Set<CallSite> incomingCalls = new LinkedHashSet<>();
    private final Set<CallSite> outgoingCalls = new LinkedHashSet<>();

    public CallGraphNode(MethodReference reference, MethodEntry methodEntry) {
        this.reference = reference;
        this.methodEntry = methodEntry;
    }

    public MethodReference getReference() {
        return reference;
    }

    /**
     * Gets the MethodEntry if this method is in the ClassPool.
     * Returns null for external methods.
     */
    public MethodEntry getMethodEntry() {
        return methodEntry;
    }

    /**
     * Returns true if this method is in the ClassPool (not external).
     */
    public boolean isInPool() {
        return methodEntry != null;
    }

    /**
     * Gets all call sites where this method is called.
     */
    public Set<CallSite> getIncomingCalls() {
        return Collections.unmodifiableSet(incomingCalls);
    }

    /**
     * Gets all call sites made from this method.
     */
    public Set<CallSite> getOutgoingCalls() {
        return Collections.unmodifiableSet(outgoingCalls);
    }

    /**
     * Gets all methods that call this method (callers).
     */
    public Set<MethodReference> getCallers() {
        Set<MethodReference> callers = new LinkedHashSet<>();
        for (CallSite site : incomingCalls) {
            callers.add(site.getCaller());
        }
        return callers;
    }

    /**
     * Gets all methods called by this method (callees).
     */
    public Set<MethodReference> getCallees() {
        Set<MethodReference> callees = new LinkedHashSet<>();
        for (CallSite site : outgoingCalls) {
            callees.add(site.getTarget());
        }
        return callees;
    }

    /**
     * Gets the number of times this method is called.
     */
    public int getCallCount() {
        return incomingCalls.size();
    }

    /**
     * Gets the number of methods this method calls.
     */
    public int getCalleeCount() {
        return outgoingCalls.size();
    }

    /**
     * Checks if this method has any callers.
     */
    public boolean hasCaller() {
        return !incomingCalls.isEmpty();
    }

    /**
     * Checks if this method calls any other methods.
     */
    public boolean hasCallees() {
        return !outgoingCalls.isEmpty();
    }

    void addIncomingCall(CallSite site) {
        incomingCalls.add(site);
    }

    void addOutgoingCall(CallSite site) {
        outgoingCalls.add(site);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallGraphNode)) return false;
        CallGraphNode that = (CallGraphNode) o;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }

    @Override
    public String toString() {
        return "CallGraphNode{" + reference +
               ", callers=" + incomingCalls.size() +
               ", callees=" + outgoingCalls.size() + "}";
    }
}
