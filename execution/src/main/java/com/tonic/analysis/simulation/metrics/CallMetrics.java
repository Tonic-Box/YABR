package com.tonic.analysis.simulation.metrics;

import com.tonic.analysis.simulation.listener.MethodCallListener;

/**
 * An immutable tally of the method calls seen during a simulation.
 */
public class CallMetrics
{

    private final int totalCalls;
    private final int virtualCalls;
    private final int staticCalls;
    private final int interfaceCalls;
    private final int specialCalls;
    private final int dynamicCalls;
    private final int distinctMethods;

    private CallMetrics(int totalCalls, int virtualCalls, int staticCalls, int interfaceCalls, int specialCalls, int dynamicCalls, int distinctMethods)
    {
        this.totalCalls = totalCalls;
        this.virtualCalls = virtualCalls;
        this.staticCalls = staticCalls;
        this.interfaceCalls = interfaceCalls;
        this.specialCalls = specialCalls;
        this.dynamicCalls = dynamicCalls;
        this.distinctMethods = distinctMethods;
    }

    /**
     * Snapshots the counters a call listener accumulated.
     *
     * @param listener the listener to read
     * @return the metrics
     */
    public static CallMetrics from(MethodCallListener listener)
    {
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
     * @return metrics with every counter at zero
     */
    public static CallMetrics empty()
    {
        return new CallMetrics(0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * @return the total number of calls
     */
    public int getTotalCalls()
    {
        return totalCalls;
    }

    /**
     * @return the number of invokevirtual calls
     */
    public int getVirtualCalls()
    {
        return virtualCalls;
    }

    /**
     * @return the number of invokestatic calls
     */
    public int getStaticCalls()
    {
        return staticCalls;
    }

    /**
     * @return the number of invokeinterface calls
     */
    public int getInterfaceCalls()
    {
        return interfaceCalls;
    }

    /**
     * @return the number of invokespecial calls
     */
    public int getSpecialCalls()
    {
        return specialCalls;
    }

    /**
     * @return the number of invokedynamic calls
     */
    public int getDynamicCalls()
    {
        return dynamicCalls;
    }

    /**
     * @return the number of distinct methods called
     */
    public int getDistinctMethods()
    {
        return distinctMethods;
    }

    /**
     * @return virtual plus interface calls
     */
    public int getPolymorphicCalls()
    {
        return virtualCalls + interfaceCalls;
    }

    /**
     * @return virtual calls as a percentage of total calls, or 0 if there were none
     */
    public double getVirtualCallPercentage()
    {
        if (totalCalls == 0) return 0;
        return (double) virtualCalls / totalCalls * 100;
    }

    /**
     * @return static calls as a percentage of total calls, or 0 if there were none
     */
    public double getStaticCallPercentage()
    {
        if (totalCalls == 0) return 0;
        return (double) staticCalls / totalCalls * 100;
    }

    /**
     * @return true if at least one call was recorded
     */
    public boolean hasCalls()
    {
        return totalCalls > 0;
    }

    /**
     * @return total calls divided by distinct methods, or 0 if no method was called
     */
    public double getAverageCallsPerMethod()
    {
        if (distinctMethods == 0) return 0;
        return (double) totalCalls / distinctMethods;
    }

    /**
     * Sums every counter with another set of metrics.
     *
     * @param other the metrics to add
     * @return the combined metrics
     */
    public CallMetrics combine(CallMetrics other)
    {
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
    public String toString()
    {
        return "CallMetrics[total=" + totalCalls +
            ", virtual=" + virtualCalls +
            ", static=" + staticCalls +
            ", interface=" + interfaceCalls +
            ", special=" + specialCalls +
            ", dynamic=" + dynamicCalls +
            ", distinct=" + distinctMethods + "]";
    }
}
