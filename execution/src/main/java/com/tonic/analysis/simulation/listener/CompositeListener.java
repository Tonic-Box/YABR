package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simulation listener that forwards every callback to its children in order, so several listeners
 * can be registered as one.
 */
public class CompositeListener implements SimulationListener
{

    private final List<SimulationListener> listeners;

    /**
     * Creates a composite with no children; add them with add().
     */
    public CompositeListener()
    {
        this.listeners = new ArrayList<>();
    }

    /**
     * Creates a composite over the given children.
     *
     * @param listeners children to delegate to, in order
     */
    public CompositeListener(SimulationListener... listeners)
    {
        this.listeners = new ArrayList<>(Arrays.asList(listeners));
    }

    /**
     * Creates a composite over a copy of the given list.
     *
     * @param listeners children to delegate to, in order
     */
    public CompositeListener(List<SimulationListener> listeners)
    {
        this.listeners = new ArrayList<>(listeners);
    }

    /**
     * Appends a child at the end of the delegation order.
     *
     * @param listener child to add
     * @return this composite
     */
    public CompositeListener add(SimulationListener listener)
    {
        listeners.add(listener);
        return this;
    }

    /**
     * Drops the first occurrence of a child, if present.
     *
     * @param listener child to remove
     * @return this composite
     */
    public CompositeListener remove(SimulationListener listener)
    {
        listeners.remove(listener);
        return this;
    }

    /**
     * @return an unmodifiable view of the children in delegation order
     */
    public List<SimulationListener> getListeners()
    {
        return Collections.unmodifiableList(listeners);
    }

    /**
     * Finds the first child assignable to a type.
     *
     * @param type class to match against
     * @param <T> listener type sought
     * @return the first matching child, or null if none matches
     */
    @SuppressWarnings("unchecked")
    public <T extends SimulationListener> T getListener(Class<T> type)
    {
        for (SimulationListener listener : listeners)
        {
            if (type.isInstance(listener))
            {
                return (T) listener;
            }
        }
        return null;
    }

    // Delegating Methods

    @Override
    public void onSimulationStart(IRMethod method)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onSimulationStart(method);
        }
    }

    @Override
    public void onSimulationEnd(IRMethod method, SimulationResult result)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onSimulationEnd(method, result);
        }
    }

    @Override
    public void onBlockEntry(IRBlock block, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onBlockEntry(block, state);
        }
    }

    @Override
    public void onBlockExit(IRBlock block, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onBlockExit(block, state);
        }
    }

    @Override
    public void onBeforeInstruction(IRInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onBeforeInstruction(instr, state);
        }
    }

    @Override
    public void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onAfterInstruction(instr, before, after);
        }
    }

    @Override
    public void onStackPush(SimValue value, IRInstruction source)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onStackPush(value, source);
        }
    }

    @Override
    public void onStackPop(SimValue value, IRInstruction consumer)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onStackPop(value, consumer);
        }
    }

    @Override
    public void onAllocation(NewInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onAllocation(instr, state);
        }
    }

    @Override
    public void onArrayAllocation(NewArrayInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onArrayAllocation(instr, state);
        }
    }

    @Override
    public void onFieldRead(FieldAccessInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onFieldRead(instr, state);
        }
    }

    @Override
    public void onFieldWrite(FieldAccessInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onFieldWrite(instr, state);
        }
    }

    @Override
    public void onArrayRead(ArrayAccessInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onArrayRead(instr, state);
        }
    }

    @Override
    public void onArrayWrite(ArrayAccessInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onArrayWrite(instr, state);
        }
    }

    @Override
    public void onBranch(BranchInstruction instr, boolean taken, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onBranch(instr, taken, state);
        }
    }

    @Override
    public void onSwitch(SwitchInstruction instr, int targetIndex, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onSwitch(instr, targetIndex, state);
        }
    }

    @Override
    public void onMethodCall(InvokeInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onMethodCall(instr, state);
        }
    }

    @Override
    public void onMethodReturn(ReturnInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onMethodReturn(instr, state);
        }
    }

    @Override
    public void onException(SimpleInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onException(instr, state);
        }
    }

    @Override
    public void onMonitorEnter(SimpleInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onMonitorEnter(instr, state);
        }
    }

    @Override
    public void onMonitorExit(SimpleInstruction instr, SimulationState state)
    {
        for (SimulationListener listener : listeners)
        {
            listener.onMonitorExit(instr, state);
        }
    }
}
