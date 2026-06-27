package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.listener.CompositeListener;
import com.tonic.analysis.simulation.listener.SimulationListener;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.simulation.util.StateTransitions;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.MethodEntry;

import java.util.*;

/**
 * Main simulation engine for executing abstract interpretation.
 *
 * <p>The SimulationEngine executes a method's IR instructions while tracking
 * execution state and notifying registered listeners of events.
 *
 * <p>Example usage:
 * <pre>
 * SimulationContext ctx = SimulationContext.forMethod(method);
 * SimulationEngine engine = new SimulationEngine(ctx);
 *
 * StackOperationListener stackListener = new StackOperationListener();
 * engine.addListener(stackListener);
 *
 * SimulationResult result = engine.simulate(irMethod);
 * System.out.println("Max stack depth: " + result.getMaxStackDepth());
 * </pre>
 */
public class SimulationEngine {

    private final SimulationContext context;
    private final CompositeListener listeners;

    /**
     * Creates a new simulation engine with the given context.
     */
    public SimulationEngine(SimulationContext context) {
        this.context = context;
        this.listeners = new CompositeListener();
    }

    /**
     * Adds a listener to receive simulation events.
     */
    public SimulationEngine addListener(SimulationListener listener) {
        listeners.add(listener);
        return this;
    }

    /**
     * Adds multiple listeners.
     */
    public SimulationEngine addListeners(SimulationListener... listenerArray) {
        for (SimulationListener listener : listenerArray) {
            listeners.add(listener);
        }
        return this;
    }

    /**
     * Removes a listener.
     */
    public SimulationEngine removeListener(SimulationListener listener) {
        listeners.remove(listener);
        return this;
    }

    /**
     * Gets a listener of a specific type.
     */
    public <T extends SimulationListener> T getListener(Class<T> type) {
        return listeners.getListener(type);
    }

    /**
     * Gets the simulation context.
     */
    public SimulationContext getContext() {
        return context;
    }

    /**
     * Simulates execution of a method.
     *
     * @param method the IR method to simulate
     * @return the simulation result
     */
    public SimulationResult simulate(IRMethod method) {
        long startTime = System.nanoTime();
        SimulationResult.Builder resultBuilder = SimulationResult.builder().method(method);

        // Notify start
        listeners.onSimulationStart(method);

        // Initialize state with method parameters
        SimulationState state = createInitialState(method);

        // Get blocks in execution order
        List<IRBlock> blocks = method.getReversePostOrder();
        if (blocks.isEmpty() && method.getEntryBlock() != null) {
            blocks = List.of(method.getEntryBlock());
        }

        // Track visited blocks for loop detection
        Map<IRBlock, SimulationState> blockEntryStates = new HashMap<>();
        Set<IRBlock> completed = new HashSet<>();

        // Worklist algorithm for simulation
        Queue<IRBlock> worklist = new LinkedList<>();
        if (method.getEntryBlock() != null) {
            worklist.add(method.getEntryBlock());
            blockEntryStates.put(method.getEntryBlock(), state);
        }

        int instructionCount = 0;
        int maxIterations = method.getBlockCount() * 10; // Prevent infinite loops
        int iterations = 0;

        while (!worklist.isEmpty() && iterations < maxIterations) {
            iterations++;
            IRBlock block = worklist.poll();

            // Skip if already completed with same state
            SimulationState entryState = blockEntryStates.get(block);
            if (entryState == null) {
                continue;
            }

            // Simulate the block
            state = entryState.atBlock(block);
            listeners.onBlockEntry(block, state);

            // Record entry state
            if (context.isInstructionLevel()) {
                resultBuilder.addState(state.snapshot());
            }

            // Execute phi instructions first
            for (PhiInstruction phi : block.getPhiInstructions()) {
                SimulationState before = state;
                state = executeInstruction(phi, state, resultBuilder);
                instructionCount++;
            }

            // Execute regular instructions
            for (IRInstruction instr : block.getInstructions()) {
                SimulationState before = state;
                state = executeInstruction(instr, state, resultBuilder);
                instructionCount++;

                // Handle terminating instructions
                if (instr instanceof ReturnInstruction) {
                    listeners.onMethodReturn((ReturnInstruction) instr, state);
                } else if (instr instanceof SimpleInstruction) {
                    SimpleInstruction simple = (SimpleInstruction) instr;
                    if (simple.getOp() == SimpleOp.ATHROW) {
                        listeners.onException(simple, state);
                    }
                }
            }

            listeners.onBlockExit(block, state);
            completed.add(block);

            // Add successor blocks to worklist
            for (IRBlock successor : block.getSuccessors()) {
                SimulationState existingState = blockEntryStates.get(successor);
                if (existingState == null) {
                    blockEntryStates.put(successor, state);
                    worklist.add(successor);
                } else if (!completed.contains(successor)) {
                    // Merge states for loops
                    SimulationState merged = existingState.merge(state);
                    blockEntryStates.put(successor, merged);
                    if (!worklist.contains(successor)) {
                        worklist.add(successor);
                    }
                }
            }
        }

        // Build result
        resultBuilder.totalInstructions(instructionCount);
        resultBuilder.maxStackDepth(state.maxStackDepth());
        resultBuilder.simulationTime(System.nanoTime() - startTime);

        SimulationResult result = resultBuilder.build();
        listeners.onSimulationEnd(method, result);

        return result;
    }

    /**
     * Simulates a single method entry (creates IR method on the fly).
     */
    public SimulationResult simulate(MethodEntry method) {
        // This requires building the IR method first
        // For now, we just return an empty result
        // In a full implementation, this would use the IR lifter
        throw new UnsupportedOperationException(
            "Direct MethodEntry simulation requires IR lifting. Use simulate(IRMethod) instead.");
    }

    /**
     * Executes a single instruction and updates state.
     */
    public SimulationState step(SimulationState state, IRInstruction instr) {
        return executeInstruction(instr, state, null);
    }

    /**
     * Executes all instructions in a block.
     */
    public SimulationState stepBlock(SimulationState state, IRBlock block) {
        state = state.atBlock(block);
        listeners.onBlockEntry(block, state);

        for (PhiInstruction phi : block.getPhiInstructions()) {
            state = executeInstruction(phi, state, null);
        }

        for (IRInstruction instr : block.getInstructions()) {
            state = executeInstruction(instr, state, null);
        }

        listeners.onBlockExit(block, state);
        return state;
    }

    /**
     * Simulates a specific path through the method.
     */
    public SimulationResult simulatePath(IRMethod method, List<IRBlock> path) {
        long startTime = System.nanoTime();
        SimulationResult.Builder resultBuilder = SimulationResult.builder().method(method);

        listeners.onSimulationStart(method);

        SimulationState state = createInitialState(method);
        int instructionCount = 0;

        for (IRBlock block : path) {
            state = state.atBlock(block);
            listeners.onBlockEntry(block, state);

            if (context.isInstructionLevel()) {
                resultBuilder.addState(state.snapshot());
            }

            for (PhiInstruction phi : block.getPhiInstructions()) {
                state = executeInstruction(phi, state, resultBuilder);
                instructionCount++;
            }

            for (IRInstruction instr : block.getInstructions()) {
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

    // ========== Private Helpers ==========

    private SimulationState createInitialState(IRMethod method) {
        // Initialize locals with method parameters
        LocalState locals = LocalState.empty();

        int localIndex = 0;

        // For instance methods, slot 0 is 'this'
        if (!method.isStatic()) {
            SimValue thisValue = SimValue.ofType(null, null); // Unknown ref type
            locals = locals.set(localIndex++, thisValue);
        }

        // Add parameter values
        for (SSAValue param : method.getParameters()) {
            SimValue paramValue = SimValue.fromSSA(param, null);
            if (param.getType() != null && param.getType().isTwoSlot()) {
                locals = locals.setWide(localIndex, paramValue);
                localIndex += 2;
            } else {
                locals = locals.set(localIndex++, paramValue);
            }
        }

        return SimulationState.of(
            StackState.empty(),
            locals
        ).atBlock(method.getEntryBlock());
    }

    private SimulationState executeInstruction(IRInstruction instr, SimulationState state,
                                                SimulationResult.Builder resultBuilder) {
        // Notify before
        listeners.onBeforeInstruction(instr, state);

        // Track stack changes for listener notifications
        int stackBefore = state.stackDepth();

        // Apply state transition
        SimulationState newState = StateTransitions.apply(state, instr);

        // Notify specific instruction events
        notifyInstructionEvents(instr, state, newState);

        // Record state if instruction-level tracking
        if (context.isInstructionLevel() && resultBuilder != null) {
            resultBuilder.addState(newState.snapshot());
        }

        // Notify after
        listeners.onAfterInstruction(instr, state, newState);

        return newState.nextInstruction();
    }

    private void notifyInstructionEvents(IRInstruction instr, SimulationState before, SimulationState after) {
        // Stack push/pop events
        int pushCount = StateTransitions.getPushCount(instr);
        int popCount = StateTransitions.getPopCount(instr);

        // Notify pops (before the operation logically happens)
        for (int i = 0; i < popCount && i < before.stackDepth(); i++) {
            SimValue value = before.peek(i);
            if (value != null && !value.isWideSecondSlot()) {
                listeners.onStackPop(value, instr);
            }
        }

        // Notify pushes (after the operation)
        for (int i = 0; i < pushCount && i < after.stackDepth(); i++) {
            SimValue value = after.peek(pushCount - 1 - i);
            if (value != null && !value.isWideSecondSlot()) {
                listeners.onStackPush(value, instr);
            }
        }

        // Type-specific events
        if (instr instanceof NewInstruction) {
            listeners.onAllocation((NewInstruction) instr, before);
        } else if (instr instanceof NewArrayInstruction) {
            listeners.onArrayAllocation((NewArrayInstruction) instr, before);
        } else if (instr instanceof FieldAccessInstruction) {
            FieldAccessInstruction fieldAccess = (FieldAccessInstruction) instr;
            if (fieldAccess.isLoad()) {
                listeners.onFieldRead(fieldAccess, before);
            } else {
                listeners.onFieldWrite(fieldAccess, before);
            }
        } else if (instr instanceof ArrayAccessInstruction) {
            ArrayAccessInstruction arrayAccess = (ArrayAccessInstruction) instr;
            if (arrayAccess.isLoad()) {
                listeners.onArrayRead(arrayAccess, before);
            } else {
                listeners.onArrayWrite(arrayAccess, before);
            }
        } else if (instr instanceof InvokeInstruction) {
            listeners.onMethodCall((InvokeInstruction) instr, before);
        } else if (instr instanceof BranchInstruction) {
            listeners.onBranch((BranchInstruction) instr, true, before);
        } else if (instr instanceof SwitchInstruction) {
            listeners.onSwitch((SwitchInstruction) instr, -1, before);
        } else if (instr instanceof SimpleInstruction) {
            SimpleInstruction simple = (SimpleInstruction) instr;
            switch (simple.getOp()) {
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
