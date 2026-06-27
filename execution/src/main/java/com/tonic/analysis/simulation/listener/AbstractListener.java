package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;

/**
 * Abstract base class for simulation listeners.
 *
 * <p>Provides no-op implementations of all listener methods.
 * Subclasses can override only the methods they need.
 *
 * <p>This class also provides some utility methods for common
 * listener operations.
 */
public abstract class AbstractListener implements SimulationListener {

    protected IRMethod currentMethod;
    protected boolean simulationActive = false;

    @Override
    public void onSimulationStart(IRMethod method) {
        this.currentMethod = method;
        this.simulationActive = true;
    }

    @Override
    public void onSimulationEnd(IRMethod method, SimulationResult result) {
        this.simulationActive = false;
    }

    /**
     * Returns true if simulation is currently active.
     */
    protected boolean isActive() {
        return simulationActive;
    }

    /**
     * Gets the method currently being simulated.
     */
    protected IRMethod getCurrentMethod() {
        return currentMethod;
    }
}
