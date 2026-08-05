package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * A call-graph node for a single method, tracking its incoming (caller) and outgoing (callee) call sites.
 */
public class CallGraphNode
{

    private final MethodReference reference;
    private final MethodEntry methodEntry;
    private final Set<CallSite> incomingCalls = new LinkedHashSet<>();
    private final Set<CallSite> outgoingCalls = new LinkedHashSet<>();

    /**
     * Creates a node for the given method.
     * @param reference the method this node represents
     * @param methodEntry the parsed method, or null if the method is external to the pool
     */
    public CallGraphNode(MethodReference reference, MethodEntry methodEntry)
    {
        this.reference = reference;
        this.methodEntry = methodEntry;
    }

    /**
     * @return the reference
     */
    public MethodReference getReference()
    {
        return reference;
    }

    /**
     * @return the method entry, or null for methods outside the ClassPool
     */
    public MethodEntry getMethodEntry()
    {
        return methodEntry;
    }

    /**
     * @return true if this method is in the ClassPool (not external)
     */
    public boolean isInPool()
    {
        return methodEntry != null;
    }

    /**
     * @return an unmodifiable view of the call sites where this method is called
     */
    public Set<CallSite> getIncomingCalls()
    {
        return Collections.unmodifiableSet(incomingCalls);
    }

    /**
     * @return an unmodifiable view of the call sites made from this method
     */
    public Set<CallSite> getOutgoingCalls()
    {
        return Collections.unmodifiableSet(outgoingCalls);
    }

    /**
     * Collects the distinct methods that call this method.
     * @return the caller method references
     */
    public Set<MethodReference> getCallers()
    {
        Set<MethodReference> callers = new LinkedHashSet<>();
        for (CallSite site : incomingCalls)
        {
            callers.add(site.getCaller());
        }
        return callers;
    }

    /**
     * Collects the distinct methods called by this method.
     * @return the callee method references
     */
    public Set<MethodReference> getCallees()
    {
        Set<MethodReference> callees = new LinkedHashSet<>();
        for (CallSite site : outgoingCalls)
        {
            callees.add(site.getTarget());
        }
        return callees;
    }

    /**
     * @return the number of incoming call sites
     */
    public int getCallCount()
    {
        return incomingCalls.size();
    }

    /**
     * @return the number of outgoing call sites
     */
    public int getCalleeCount()
    {
        return outgoingCalls.size();
    }

    /**
     * @return true if this method has at least one caller
     */
    public boolean hasCaller()
    {
        return !incomingCalls.isEmpty();
    }

    /**
     * @return true if this method calls at least one other method
     */
    public boolean hasCallees()
    {
        return !outgoingCalls.isEmpty();
    }

    void addIncomingCall(CallSite site)
    {
        incomingCalls.add(site);
    }

    void addOutgoingCall(CallSite site)
    {
        outgoingCalls.add(site);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof CallGraphNode)) return false;
        CallGraphNode that = (CallGraphNode) o;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(reference);
    }

    @Override
    public String toString()
    {
        return "CallGraphNode{" + reference +
               ", callers=" + incomingCalls.size() +
               ", callees=" + outgoingCalls.size() + "}";
    }
}
