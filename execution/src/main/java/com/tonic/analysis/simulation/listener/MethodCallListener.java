package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;

import java.util.*;

/**
 * Simulation listener that counts method calls by invoke kind and by target, optionally recording the call
 * sequence.
 */
public class MethodCallListener extends AbstractListener
{

    private int totalCalls;
    private int virtualCalls;
    private int staticCalls;
    private int interfaceCalls;
    private int specialCalls;
    private int dynamicCalls;

    private final Map<MethodReference, Integer> callCounts;
    private final List<CallSite> callSequence;
    private final boolean trackSequence;

    /**
     * Creates a listener that also records the call sequence.
     */
    public MethodCallListener()
    {
        this(true);
    }

    /**
     * Creates a listener with all counters at zero.
     *
     * @param trackSequence whether to record every call site in order as well as counting
     */
    public MethodCallListener(boolean trackSequence)
    {
        this.trackSequence = trackSequence;
        this.callCounts = new HashMap<>();
        this.callSequence = new ArrayList<>();
    }

    @Override
    public void onSimulationStart(IRMethod method)
    {
        super.onSimulationStart(method);
        totalCalls = 0;
        virtualCalls = 0;
        staticCalls = 0;
        interfaceCalls = 0;
        specialCalls = 0;
        dynamicCalls = 0;
        callCounts.clear();
        callSequence.clear();
    }

    @Override
    public void onMethodCall(InvokeInstruction instr, SimulationState state)
    {
        totalCalls++;

        InvokeType type = instr.getInvokeType();
        switch (type)
        {
            case VIRTUAL:
                virtualCalls++;
                break;
            case STATIC:
                staticCalls++;
                break;
            case INTERFACE:
                interfaceCalls++;
                break;
            case SPECIAL:
                specialCalls++;
                break;
            case DYNAMIC:
                dynamicCalls++;
                break;
        }

        MethodReference ref = new MethodReference(instr.getOwner(), instr.getName(), instr.getDescriptor());
        callCounts.merge(ref, 1, Integer::sum);

        if (trackSequence)
        {
            callSequence.add(new CallSite(instr, ref, type, state.stackDepth()));
        }
    }

    /**
     * @return the total number of method calls seen
     */
    public int getTotalCalls()
    {
        return totalCalls;
    }

    /**
     * @return the number of invokevirtual calls seen
     */
    public int getVirtualCalls()
    {
        return virtualCalls;
    }

    /**
     * @return the number of invokestatic calls seen
     */
    public int getStaticCalls()
    {
        return staticCalls;
    }

    /**
     * @return the number of invokeinterface calls seen
     */
    public int getInterfaceCalls()
    {
        return interfaceCalls;
    }

    /**
     * @return the number of invokespecial calls seen (constructors, super calls)
     */
    public int getSpecialCalls()
    {
        return specialCalls;
    }

    /**
     * @return the number of invokedynamic calls seen
     */
    public int getDynamicCalls()
    {
        return dynamicCalls;
    }

    /**
     * Looks up how many times one exact method was called.
     *
     * @param owner internal owner name
     * @param name method name
     * @param descriptor method descriptor
     * @return the call count, or 0 if that method was never called
     */
    public int getCallCount(String owner, String name, String descriptor)
    {
        MethodReference ref = new MethodReference(owner, name, descriptor);
        return callCounts.getOrDefault(ref, 0);
    }

    /**
     * @return an unmodifiable view of the per-method call counts
     */
    public Map<MethodReference, Integer> getCallCounts()
    {
        return Collections.unmodifiableMap(callCounts);
    }

    /**
     * @return an unmodifiable view of the calls in execution order, empty unless sequence tracking is on
     */
    public List<CallSite> getCallSequence()
    {
        return Collections.unmodifiableList(callSequence);
    }

    /**
     * @return how many distinct methods were called
     */
    public int getDistinctMethodCount()
    {
        return callCounts.size();
    }

    /**
     * Ranks the called methods by descending call count.
     *
     * @param n maximum number of entries to return
     * @return the top entries, fewer than n if not that many methods were called
     */
    public List<Map.Entry<MethodReference, Integer>> getMostCalledMethods(int n)
    {
        List<Map.Entry<MethodReference, Integer>> sorted = new ArrayList<>(callCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    /**
     * An owner, name and descriptor triple used as the call count map key.
     */
    public static class MethodReference
    {
        private final String owner;
        private final String name;
        private final String descriptor;

        public MethodReference(String owner, String name, String descriptor)
        {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        /**
         * @return the owner
         */
        public String getOwner()
        {
            return owner;
        }

        /**
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the descriptor
         */
        public String getDescriptor()
        {
            return descriptor;
        }

        /**
         * @return true if the referenced method is an instance constructor
         */
        public boolean isConstructor()
        {
            return "<init>".equals(name);
        }

        /**
         * @return true if the referenced method is a static initializer
         */
        public boolean isClassInitializer()
        {
            return "<clinit>".equals(name);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof MethodReference)) return false;
            MethodReference that = (MethodReference) o;
            return Objects.equals(owner, that.owner) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(owner, name, descriptor);
        }

        @Override
        public String toString()
        {
            return owner + "." + name + descriptor;
        }
    }

    /**
     * One recorded call: the invoke, its resolved target, kind and the operand stack depth at entry.
     */
    public static class CallSite
    {
        private final InvokeInstruction instruction;
        private final MethodReference target;
        private final InvokeType invokeType;
        private final int stackDepthAtCall;

        public CallSite(InvokeInstruction instruction, MethodReference target, InvokeType invokeType, int stackDepth)
        {
            this.instruction = instruction;
            this.target = target;
            this.invokeType = invokeType;
            this.stackDepthAtCall = stackDepth;
        }

        /**
         * @return the instruction
         */
        public InvokeInstruction getInstruction()
        {
            return instruction;
        }

        /**
         * @return the target
         */
        public MethodReference getTarget()
        {
            return target;
        }

        /**
         * @return the invoke type
         */
        public InvokeType getInvokeType()
        {
            return invokeType;
        }

        /**
         * @return the stack depth at call
         */
        public int getStackDepthAtCall()
        {
            return stackDepthAtCall;
        }

        @Override
        public String toString()
        {
            return invokeType + " " + target;
        }
    }

    @Override
    public String toString()
    {
        return "MethodCallListener[total=" + totalCalls +
            ", virtual=" + virtualCalls +
            ", static=" + staticCalls +
            ", interface=" + interfaceCalls +
            ", special=" + specialCalls +
            ", dynamic=" + dynamicCalls + "]";
    }
}
