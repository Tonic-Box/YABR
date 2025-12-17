package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.MethodCallListener;

/**
 * Metrics container for method call operations.
 *
 * <p>This class provides a clean interface to call statistics
 * collected during simulation.
 */
public class CallMetrics {

    private final int totalCalls;
    private final int virtualCalls;
    private final int staticCalls;
    private final int interfaceCalls;
    private final int specialCalls;
    private final int dynamicCalls;
    private final int distinctMethods;

    private CallMetrics(int totalCalls, int virtualCalls, int staticCalls,
                        int interfaceCalls, int specialCalls, int dynamicCalls, int distinctMethods) {
        this.totalCalls = totalCalls;
        this.virtualCalls = virtualCalls;
        this.staticCalls = staticCalls;
        this.interfaceCalls = interfaceCalls;
        this.specialCalls = specialCalls;
        this.dynamicCalls = dynamicCalls;
        this.distinctMethods = distinctMethods;
    }

    /**
     * Creates metrics from a MethodCallListener.
     */
    public static CallMetrics from(MethodCallListener listener) {
        return new CallMetrics(
            listener.getTotalCalls(),
            listener.getVirtualCalls(),
            listener.getStaticCalls(),
            listener.getInterfaceCalls(),
            listener.getSpecialCalls(),
            listener.getDynamicCalls(),
            listener.getDistinctMethodCount()
        );
    }

    /**
     * Creates empty metrics.
     */
    public static CallMetrics empty() {
        return new CallMetrics(0, 0, 0, 0, 0, 0, 0);
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
     * Gets the number of special method calls.
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
     * Gets the number of distinct methods called.
     */
    public int getDistinctMethods() {
        return distinctMethods;
    }

    /**
     * Gets the number of polymorphic calls (virtual + interface).
     */
    public int getPolymorphicCalls() {
        return virtualCalls + interfaceCalls;
    }

    /**
     * Gets the percentage of virtual calls.
     */
    public double getVirtualCallPercentage() {
        if (totalCalls == 0) return 0;
        return (double) virtualCalls / totalCalls * 100;
    }

    /**
     * Gets the percentage of static calls.
     */
    public double getStaticCallPercentage() {
        if (totalCalls == 0) return 0;
        return (double) staticCalls / totalCalls * 100;
    }

    /**
     * Returns true if any calls occurred.
     */
    public boolean hasCalls() {
        return totalCalls > 0;
    }

    /**
     * Gets the average calls per distinct method.
     */
    public double getAverageCallsPerMethod() {
        if (distinctMethods == 0) return 0;
        return (double) totalCalls / distinctMethods;
    }

    /**
     * Combines this metrics with another.
     */
    public CallMetrics combine(CallMetrics other) {
        return new CallMetrics(
            this.totalCalls + other.totalCalls,
            this.virtualCalls + other.virtualCalls,
            this.staticCalls + other.staticCalls,
            this.interfaceCalls + other.interfaceCalls,
            this.specialCalls + other.specialCalls,
            this.dynamicCalls + other.dynamicCalls,
            this.distinctMethods + other.distinctMethods
        );
    }

    @Override
    public String toString() {
        return "CallMetrics[total=" + totalCalls +
            ", virtual=" + virtualCalls +
            ", static=" + staticCalls +
            ", interface=" + interfaceCalls +
            ", special=" + specialCalls +
            ", dynamic=" + dynamicCalls +
            ", distinct=" + distinctMethods + "]";
    }
}
