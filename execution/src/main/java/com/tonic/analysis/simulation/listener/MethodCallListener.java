package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import com.tonic.analysis.ssa.ir.ReturnInstruction;

import java.util.*;

/**
 * Listener that tracks method calls during simulation.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Total method call count</li>
 *   <li>Call counts by type (virtual, static, interface, special)</li>
 *   <li>Call counts per method</li>
 *   <li>Call sequence</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * MethodCallListener listener = new MethodCallListener();
 * engine.addListener(listener);
 * engine.simulate(method);
 *
 * System.out.println("Total calls: " + listener.getTotalCalls());
 * for (var entry : listener.getCallCounts().entrySet()) {
 *     System.out.println(entry.getKey() + ": " + entry.getValue());
 * }
 * </pre>
 */
public class MethodCallListener extends AbstractListener {

    private int totalCalls;
    private int virtualCalls;
    private int staticCalls;
    private int interfaceCalls;
    private int specialCalls;
    private int dynamicCalls;

    private final Map<MethodReference, Integer> callCounts;
    private final List<CallSite> callSequence;
    private boolean trackSequence;

    public MethodCallListener() {
        this(true);
    }

    public MethodCallListener(boolean trackSequence) {
        this.trackSequence = trackSequence;
        this.callCounts = new HashMap<>();
        this.callSequence = new ArrayList<>();
    }

    @Override
    public void onSimulationStart(IRMethod method) {
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
    public void onMethodCall(InvokeInstruction instr, SimulationState state) {
        totalCalls++;

        // Count by invoke type
        InvokeType type = instr.getInvokeType();
        switch (type) {
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

        // Track per-method counts
        MethodReference ref = new MethodReference(
            instr.getOwner(),
            instr.getName(),
            instr.getDescriptor()
        );
        callCounts.merge(ref, 1, Integer::sum);

        // Track sequence
        if (trackSequence) {
            callSequence.add(new CallSite(instr, ref, type, state.stackDepth()));
        }
    }

    /**
     * Gets the total number of method calls.
     */
    public int getTotalCalls() {
        return totalCalls;
    }

    /**
     * Gets the number of virtual method calls.
     */
    public int getVirtualCalls() {
        return virtualCalls;
    }

    /**
     * Gets the number of static method calls.
     */
    public int getStaticCalls() {
        return staticCalls;
    }

    /**
     * Gets the number of interface method calls.
     */
    public int getInterfaceCalls() {
        return interfaceCalls;
    }

    /**
     * Gets the number of special method calls (constructors, super calls).
     */
    public int getSpecialCalls() {
        return specialCalls;
    }

    /**
     * Gets the number of dynamic (invokedynamic) calls.
     */
    public int getDynamicCalls() {
        return dynamicCalls;
    }

    /**
     * Gets the call count for a specific method.
     */
    public int getCallCount(String owner, String name, String descriptor) {
        MethodReference ref = new MethodReference(owner, name, descriptor);
        return callCounts.getOrDefault(ref, 0);
    }

    /**
     * Gets all call counts by method.
     */
    public Map<MethodReference, Integer> getCallCounts() {
        return Collections.unmodifiableMap(callCounts);
    }

    /**
     * Gets the call sequence.
     */
    public List<CallSite> getCallSequence() {
        return Collections.unmodifiableList(callSequence);
    }

    /**
     * Gets the number of distinct methods called.
     */
    public int getDistinctMethodCount() {
        return callCounts.size();
    }

    /**
     * Gets the most called methods (top N).
     */
    public List<Map.Entry<MethodReference, Integer>> getMostCalledMethods(int n) {
        List<Map.Entry<MethodReference, Integer>> sorted = new ArrayList<>(callCounts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    /**
     * Represents a method reference.
     */
    public static class MethodReference {
        private final String owner;
        private final String name;
        private final String descriptor;

        public MethodReference(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public boolean isConstructor() {
            return "<init>".equals(name);
        }

        public boolean isClassInitializer() {
            return "<clinit>".equals(name);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodReference)) return false;
            MethodReference that = (MethodReference) o;
            return Objects.equals(owner, that.owner) &&
                   Objects.equals(name, that.name) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, descriptor);
        }

        @Override
        public String toString() {
            return owner + "." + name + descriptor;
        }
    }

    /**
     * Represents a call site.
     */
    public static class CallSite {
        private final InvokeInstruction instruction;
        private final MethodReference target;
        private final InvokeType invokeType;
        private final int stackDepthAtCall;

        public CallSite(InvokeInstruction instruction, MethodReference target,
                       InvokeType invokeType, int stackDepth) {
            this.instruction = instruction;
            this.target = target;
            this.invokeType = invokeType;
            this.stackDepthAtCall = stackDepth;
        }

        public InvokeInstruction getInstruction() {
            return instruction;
        }

        public MethodReference getTarget() {
            return target;
        }

        public InvokeType getInvokeType() {
            return invokeType;
        }

        public int getStackDepthAtCall() {
            return stackDepthAtCall;
        }

        @Override
        public String toString() {
            return invokeType + " " + target;
        }
    }

    @Override
    public String toString() {
        return "MethodCallListener[total=" + totalCalls +
            ", virtual=" + virtualCalls +
            ", static=" + staticCalls +
            ", interface=" + interfaceCalls +
            ", special=" + specialCalls +
            ", dynamic=" + dynamicCalls + "]";
    }
}
