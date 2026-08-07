package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.ssa.cfg.IRMethod;

/**
 * No-op base implementation of every simulation listener callback.
 */
public abstract class AbstractListener implements SimulationListener
{

    protected IRMethod currentMethod;
    protected boolean simulationActive = false;

    @Override
    public void onSimulationStart(IRMethod method)
    {
        this.currentMethod = method;
        this.simulationActive = true;
    }

    @Override
    public void onSimulationEnd(IRMethod method, SimulationResult result)
    {
        this.simulationActive = false;
    }

    /**
     * @return true between simulation start and end
     */
    protected boolean isActive()
    {
        return simulationActive;
    }

    /**
     * @return the method currently being simulated
     */
    protected IRMethod getCurrentMethod()
    {
        return currentMethod;
    }
}
