package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.listener.CompositeListener;
import com.tonic.analysis.simulation.listener.SimulationListener;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.simulation.util.StateTransitions;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Main simulation engine for executing abstract interpretation.
 */
public class SimulationEngine
{

    private final SimulationContext context;
    private final CompositeListener listeners;

    /**
     * Creates a new simulation engine with the given context.
     *
     * @param context the configuration and state the simulation runs under
     */
    public SimulationEngine(SimulationContext context)
    {
        this.context = context;
        this.listeners = new CompositeListener();
    }

    /**
     * Registers a listener to receive simulation events.
     *
     * @param listener the listener to register
     * @return this engine
     */
    public SimulationEngine addListener(SimulationListener listener)
    {
        listeners.add(listener);
        return this;
    }

    /**
     * Registers several listeners in one call.
     *
     * @param listenerArray the listeners to register
     * @return this engine
     */
    public SimulationEngine addListeners(SimulationListener... listenerArray)
    {
        for (SimulationListener listener : listenerArray)
        {
            listeners.add(listener);
        }
        return this;
    }

    /**
     * Unregisters a listener.
     *
     * @param listener the listener to drop
     * @return this engine
     */
    public SimulationEngine removeListener(SimulationListener listener)
    {
        listeners.remove(listener);
        return this;
    }

    /**
     * Looks up a registered listener by its class.
     *
     * @param <T>  the listener type
     * @param type the listener class to match
     * @return the first registered listener of that type, or null if none is registered
     */
    public <T extends SimulationListener> T getListener(Class<T> type)
    {
        return listeners.getListener(type);
    }

    /**
     * @return the context this engine simulates under
     */
    public SimulationContext getContext()
    {
        return context;
    }

    /**
     * Simulates execution of a method.
     * @param method the IR method to simulate
     * @return the simulation result
     */
    public SimulationResult simulate(IRMethod method)
    {
        long startTime = System.nanoTime();
        SimulationResult.Builder resultBuilder = SimulationResult.builder().method(method);

        listeners.onSimulationStart(method);

        SimulationState state = createInitialState(method);

        // Track visited blocks for loop detection
        Map<IRBlock, SimulationState> blockEntryStates = new HashMap<>();
        Set<IRBlock> completed = new HashSet<>();

        // Worklist algorithm for simulation
        Queue<IRBlock> worklist = new LinkedList<>();
        if (method.getEntryBlock() != null)
        {
            worklist.add(method.getEntryBlock());
            blockEntryStates.put(method.getEntryBlock(), state);
        }

        int instructionCount = 0;
        int maxIterations = method.getBlockCount() * 10; // Prevent infinite loops
        int iterations = 0;

        while (!worklist.isEmpty() && iterations < maxIterations)
        {
            iterations++;
            IRBlock block = worklist.poll();

            // Skip if already completed with same state
            SimulationState entryState = blockEntryStates.get(block);
            if (entryState == null)
            {
                continue;
            }

            state = entryState.atBlock(block);
            listeners.onBlockEntry(block, state);

            if (context.isInstructionLevel())
            {
                resultBuilder.addState(state.snapshot());
            }

            for (PhiInstruction phi : block.getPhiInstructions())
            {
                state = executeInstruction(phi, state, resultBuilder);
                instructionCount++;
            }

            for (IRInstruction instr : block.getInstructions())
            {
                state = executeInstruction(instr, state, resultBuilder);
                instructionCount++;

                // Handle terminating instructions
                if (instr instanceof ReturnInstruction)
                {
                    listeners.onMethodReturn((ReturnInstruction) instr, state);
                }
                else if (instr instanceof SimpleInstruction)
                {
                    SimpleInstruction simple = (SimpleInstruction) instr;
                    if (simple.getOp() == SimpleOp.ATHROW)
                    {
                        listeners.onException(simple, state);
                    }
                }
            }

            listeners.onBlockExit(block, state);
            completed.add(block);

            for (IRBlock successor : block.getSuccessors())
            {
                SimulationState existingState = blockEntryStates.get(successor);
                if (existingState == null)
                {
                    blockEntryStates.put(successor, state);
                    worklist.add(successor);
                }
                else if (!completed.contains(successor))
                {
                    // Merge states for loops
                    SimulationState merged = existingState.merge(state);
                    blockEntryStates.put(successor, merged);
                    if (!worklist.contains(successor))
                    {
                        worklist.add(successor);
                    }
                }
            }
        }

        resultBuilder.totalInstructions(instructionCount);
        resultBuilder.maxStackDepth(state.maxStackDepth());
        resultBuilder.simulationTime(System.nanoTime() - startTime);

        SimulationResult result = resultBuilder.build();
        listeners.onSimulationEnd(method, result);

        return result;
    }

    /**
     * Lifts a class file method to IR and simulates it.
     *
     * @param method the method to lift and simulate
     * @return the simulation result
     * @throws IllegalArgumentException if the method has no code attribute
     */
    public SimulationResult simulate(MethodEntry method)
    {
        if (method.getCodeAttribute() == null)
        {
            throw new IllegalArgumentException("Cannot simulate a method with no body: " + method.getName());
        }
        IRMethod irMethod = new SSA(method.getClassFile().getConstPool()).lift(method);
        return simulate(irMethod);
    }

    /**
     * Executes one instruction without recording it in a result.
     *
     * @param state the state to execute against
     * @param instr the instruction to execute
     * @return the state after the instruction
     */
    public SimulationState step(SimulationState state, IRInstruction instr)
    {
        return executeInstruction(instr, state, null);
    }

    /**
     * Executes a block's phis and instructions, firing the block entry and exit events.
     *
     * @param state the state to enter the block with
     * @param block the block to execute
     * @return the state after the last instruction
     */
    public SimulationState stepBlock(SimulationState state, IRBlock block)
    {
        state = state.atBlock(block);
        listeners.onBlockEntry(block, state);

        for (PhiInstruction phi : block.getPhiInstructions())
        {
            state = executeInstruction(phi, state, null);
        }

        for (IRInstruction instr : block.getInstructions())
        {
            state = executeInstruction(instr, state, null);
        }

        listeners.onBlockExit(block, state);
        return state;
    }

    /**
     * Simulates one fixed block sequence instead of the whole method.
     *
     * @param method the method the path belongs to
     * @param path the blocks to execute in order
     * @return the simulation result for that path
     */
    public SimulationResult simulatePath(IRMethod method, List<IRBlock> path)
    {
        long startTime = System.nanoTime();
        SimulationResult.Builder resultBuilder = SimulationResult.builder().method(method);

        listeners.onSimulationStart(method);

        SimulationState state = createInitialState(method);
        int instructionCount = 0;

        for (IRBlock block : path)
        {
            state = state.atBlock(block);
            listeners.onBlockEntry(block, state);

            if (context.isInstructionLevel())
            {
                resultBuilder.addState(state.snapshot());
            }

            for (PhiInstruction phi : block.getPhiInstructions())
            {
                state = executeInstruction(phi, state, resultBuilder);
                instructionCount++;
            }

            for (IRInstruction instr : block.getInstructions())
            {
                state = executeInstruction(instr, state, resultBuilder);
                instructionCount++;
            }

            listeners.onBlockExit(block, state);
        }

        resultBuilder.totalInstructions(instructionCount);
        resultBuilder.maxStackDepth(state.maxStackDepth());
        resultBuilder.simulationTime(System.nanoTime() - startTime);

        SimulationResult result = resultBuilder.build();
        listeners.onSimulationEnd(method, result);

        return result;
    }

    // Private Helpers

    private SimulationState createInitialState(IRMethod method)
    {
        LocalState locals = LocalState.empty();

        int localIndex = 0;

        // For instance methods, slot 0 is 'this'
        if (!method.isStatic())
        {
            SimValue thisValue = SimValue.ofType(null, null); // Unknown ref type
            locals = locals.set(localIndex++, thisValue);
        }

        for (SSAValue param : method.getParameters())
        {
            SimValue paramValue = SimValue.fromSSA(param, null);
            if (param.getType() != null && param.getType().isTwoSlot())
            {
                locals = locals.setWide(localIndex, paramValue);
                localIndex += 2;
            }
            else
            {
                locals = locals.set(localIndex++, paramValue);
            }
        }

        return SimulationState.of(StackState.empty(), locals).atBlock(method.getEntryBlock());
    }

    private SimulationState executeInstruction(IRInstruction instr, SimulationState state, SimulationResult.Builder resultBuilder)
    {
        listeners.onBeforeInstruction(instr, state);

        SimulationState newState = StateTransitions.apply(state, instr);

        notifyInstructionEvents(instr, state, newState);

        if (context.isInstructionLevel() && resultBuilder != null)
        {
            resultBuilder.addState(newState.snapshot());
        }

        listeners.onAfterInstruction(instr, state, newState);

        return newState.nextInstruction();
    }

    private void notifyInstructionEvents(IRInstruction instr, SimulationState before, SimulationState after)
    {
        // Stack push/pop events
        int pushCount = StateTransitions.getPushCount(instr);
        int popCount = StateTransitions.getPopCount(instr);

        // Notify pops (before the operation logically happens)
        for (int i = 0; i < popCount && i < before.stackDepth(); i++)
        {
            SimValue value = before.peek(i);
            if (value != null && !value.isWideSecondSlot())
            {
                listeners.onStackPop(value, instr);
            }
        }

        // Notify pushes (after the operation)
        for (int i = 0; i < pushCount && i < after.stackDepth(); i++)
        {
            SimValue value = after.peek(pushCount - 1 - i);
            if (value != null && !value.isWideSecondSlot())
            {
                listeners.onStackPush(value, instr);
            }
        }

        // Type-specific events
        if (instr instanceof NewInstruction)
        {
            listeners.onAllocation((NewInstruction) instr, before);
        }
        else if (instr instanceof NewArrayInstruction)
        {
            listeners.onArrayAllocation((NewArrayInstruction) instr, before);
        }
        else if (instr instanceof FieldAccessInstruction)
        {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isLoad())
            {
                listeners.onFieldRead(fieldAccess, before);
            }
            else
            {
                listeners.onFieldWrite(fieldAccess, before);
            }
        }
        else if (instr instanceof ArrayAccessInstruction)
        {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isLoad())
            {
                listeners.onArrayRead(arrayAccess, before);
            }
            else
            {
                listeners.onArrayWrite(arrayAccess, before);
            }
        }
        else if (instr instanceof InvokeInstruction)
        {
            listeners.onMethodCall((InvokeInstruction) instr, before);
        }
        else if (instr instanceof BranchInstruction)
        {
            listeners.onBranch((BranchInstruction) instr, true, before);
        }
        else if (instr instanceof SwitchInstruction)
        {
            listeners.onSwitch((SwitchInstruction) instr, -1, before);
        }
        else if (instr instanceof SimpleInstruction)
        {
            SimpleInstruction simple = (SimpleInstruction) instr;
            switch (simple.getOp())
            {
                case MONITORENTER:
                    listeners.onMonitorEnter(simple, before);
                    break;
                case MONITOREXIT:
                    listeners.onMonitorExit(simple, before);
                    break;
                case ATHROW:
                    listeners.onException(simple, before);
                    break;
            }
        }
    }
}
