package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.listener.CompositeListener;
import com.tonic.analysis.simulation.listener.SimulationListener;
import com.tonic.analysis.simulation.state.CallStackState;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.simulation.util.StateTransitions;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassPool;
import java.util.*;

/**
 * Simulation engine with inter-procedural analysis support.
 *
 * <p>Extends the basic simulation engine to follow method calls up to a
 * configurable depth. Callees are resolved from the ClassPool and their bodies
 * lifted to IR on demand.
 *
 * <p>Example usage:
 * <pre>
 * SimulationContext ctx = SimulationContext.forPool(classPool)
 *     .withMaxCallDepth(3);
 *
 * InterProceduralEngine engine = new InterProceduralEngine(ctx);
 * SimulationResult result = engine.simulate(entryMethod);
 * </pre>
 */
public class InterProceduralEngine {

    private final SimulationContext context;
    private final CompositeListener listeners;
    private final Map<String, IRMethod> methodCache;
    private CallStackState callStack;
    private int methodsSimulated;
    private int maxDepthReached;

    public InterProceduralEngine(SimulationContext context) {
        this.context = context;
        this.listeners = new CompositeListener();
        this.methodCache = new HashMap<>();
        this.callStack = CallStackState.empty();
    }

    /**
     * Adds a listener.
     */
    public InterProceduralEngine addListener(SimulationListener listener) {
        listeners.add(listener);
        return this;
    }

    /**
     * Adds multiple listeners.
     */
    public InterProceduralEngine addListeners(SimulationListener... listenerArray) {
        for (SimulationListener listener : listenerArray) {
            listeners.add(listener);
        }
        return this;
    }

    /**
     * Gets the simulation context.
     */
    public SimulationContext getContext() {
        return context;
    }

    /**
     * Gets the current call stack.
     */
    public CallStackState getCallStack() {
        return callStack;
    }

    /**
     * Gets the number of methods simulated.
     */
    public int getMethodsSimulated() {
        return methodsSimulated;
    }

    /**
     * Gets the maximum call depth reached.
     */
    public int getMaxDepthReached() {
        return maxDepthReached;
    }

    /**
     * Simulates a method with inter-procedural analysis.
     */
    public SimulationResult simulate(IRMethod method) {
        long startTime = System.nanoTime();
        SimulationResult.Builder resultBuilder = SimulationResult.builder().method(method);

        callStack = CallStackState.empty();
        methodsSimulated = 0;
        maxDepthReached = 0;

        listeners.onSimulationStart(method);

        SimulationState state = createInitialState(method);

        int instructionCount = simulateMethod(method, state, resultBuilder, 0);

        resultBuilder.totalInstructions(instructionCount);
        resultBuilder.maxStackDepth(state.maxStackDepth());
        resultBuilder.simulationTime(System.nanoTime() - startTime);

        SimulationResult result = resultBuilder.build();
        listeners.onSimulationEnd(method, result);

        return result;
    }

    private int simulateMethod(IRMethod method, SimulationState initialState,
                               SimulationResult.Builder resultBuilder, int currentDepth) {
        methodsSimulated++;
        if (currentDepth > maxDepthReached) {
            maxDepthReached = currentDepth;
        }

        SimulationState state = initialState.atBlock(method.getEntryBlock());

        // Worklist for blocks
        Map<IRBlock, SimulationState> blockEntryStates = new HashMap<>();
        Set<IRBlock> completed = new HashSet<>();
        Queue<IRBlock> worklist = new LinkedList<>();

        if (method.getEntryBlock() != null) {
            worklist.add(method.getEntryBlock());
            blockEntryStates.put(method.getEntryBlock(), state);
        }

        int instructionCount = 0;
        int maxIterations = method.getBlockCount() * 10;
        int iterations = 0;

        while (!worklist.isEmpty() && iterations < maxIterations) {
            iterations++;
            IRBlock block = worklist.poll();

            SimulationState entryState = blockEntryStates.get(block);
            if (entryState == null) continue;

            state = entryState.atBlock(block);
            listeners.onBlockEntry(block, state);

            if (context.isInstructionLevel()) {
                resultBuilder.addState(state.snapshot());
            }

            for (PhiInstruction phi : block.getPhiInstructions()) {
                state = executeInstruction(phi, state, resultBuilder);
                instructionCount++;
            }

            for (IRInstruction instr : block.getInstructions()) {
                // Handle method calls specially for inter-procedural analysis
                if (instr instanceof InvokeInstruction && shouldFollowCall(currentDepth)) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    state = handleMethodCall(invoke, state, currentDepth, resultBuilder);
                    instructionCount++; // Count the invoke itself
                } else {
                    state = executeInstruction(instr, state, resultBuilder);
                    instructionCount++;
                }

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

            for (IRBlock successor : block.getSuccessors()) {
                SimulationState existingState = blockEntryStates.get(successor);
                if (existingState == null) {
                    blockEntryStates.put(successor, state);
                    worklist.add(successor);
                } else if (!completed.contains(successor)) {
                    SimulationState merged = existingState.merge(state);
                    blockEntryStates.put(successor, merged);
                    if (!worklist.contains(successor)) {
                        worklist.add(successor);
                    }
                }
            }
        }

        return instructionCount;
    }

    private boolean shouldFollowCall(int currentDepth) {
        return context.isInterProcedural() && currentDepth < context.getMaxCallDepth();
    }

    private SimulationState handleMethodCall(InvokeInstruction invoke, SimulationState state,
                                              int currentDepth, SimulationResult.Builder resultBuilder) {
        listeners.onMethodCall(invoke, state);

        IRMethod calledMethod = resolveMethod(invoke);

        if (calledMethod != null && !callStack.contains(calledMethod)) {
            CallStackState.CallFrame frame = new CallStackState.CallFrame(
                calledMethod, invoke, state,
                state.getCurrentBlock(), state.getInstructionIndex()
            );
            callStack = callStack.push(frame);

            SimulationState calleeState = createCalleeState(invoke, state, calledMethod);

            simulateMethod(calledMethod, calleeState, resultBuilder, currentDepth + 1);

            callStack = callStack.pop();

            state = applyReturnEffect(invoke, state);
        } else {
            // Can't follow call - just apply state transition
            state = StateTransitions.apply(state, invoke);
        }

        return state.nextInstruction();
    }

    private IRMethod resolveMethod(InvokeInstruction invoke) {
        String key = invoke.getOwner() + "." + invoke.getName() + invoke.getDescriptor();
        if (methodCache.containsKey(key)) {
            return methodCache.get(key);
        }

        IRMethod resolved = liftCallee(invoke);
        methodCache.put(key, resolved);
        return resolved;
    }

    /**
     * Resolves the invoked method from the ClassPool and lifts its body to IR, or null when the
     * class/method cannot be resolved or has no body (abstract/native).
     */
    private IRMethod liftCallee(InvokeInstruction invoke) {
        ClassPool classPool = context.getClassPool();
        if (classPool == null) {
            return null;
        }
        try {
            var classFile = classPool.get(invoke.getOwner());
            if (classFile == null) {
                return null;
            }
            for (var method : classFile.getMethods()) {
                if (method.getName().equals(invoke.getName())
                        && method.getDesc().equals(invoke.getDescriptor())) {
                    return method.getCodeAttribute() != null
                            ? new SSA(classFile.getConstPool()).lift(method)
                            : null;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private SimulationState createCalleeState(InvokeInstruction invoke, SimulationState callerState,
                                               IRMethod calledMethod) {
        LocalState locals = LocalState.empty();
        int localIndex = 0;
        int argCount = invoke.getArguments().size();

        // For instance methods, slot 0 is 'this' (receiver from stack)
        if (!calledMethod.isStatic()) {
            SimValue receiver = callerState.peek(argCount);
            locals = locals.set(localIndex++, receiver);
        }

        for (int i = 0; i < argCount; i++) {
            SimValue arg = callerState.peek(argCount - 1 - i);
            if (arg != null && arg.isWide()) {
                locals = locals.setWide(localIndex, arg);
                localIndex += 2;
            } else {
                locals = locals.set(localIndex++, arg);
            }
        }

        return SimulationState.of(StackState.empty(), locals)
            .atBlock(calledMethod.getEntryBlock());
    }

    private SimulationState applyReturnEffect(InvokeInstruction invoke, SimulationState state) {
        return StateTransitions.apply(state, invoke);
    }

    private SimulationState createInitialState(IRMethod method) {
        LocalState locals = LocalState.empty();
        int localIndex = 0;

        if (!method.isStatic()) {
            SimValue thisValue = SimValue.ofType(null, null);
            locals = locals.set(localIndex++, thisValue);
        }

        for (SSAValue param : method.getParameters()) {
            SimValue paramValue = SimValue.fromSSA(param, null);
            if (param.getType() != null && param.getType().isTwoSlot()) {
                locals = locals.setWide(localIndex, paramValue);
                localIndex += 2;
            } else {
                locals = locals.set(localIndex++, paramValue);
            }
        }

        return SimulationState.of(StackState.empty(), locals)
            .atBlock(method.getEntryBlock());
    }

    private SimulationState executeInstruction(IRInstruction instr, SimulationState state,
                                                SimulationResult.Builder resultBuilder) {
        listeners.onBeforeInstruction(instr, state);

        SimulationState newState = StateTransitions.apply(state, instr);

        notifyInstructionEvents(instr, state, newState);

        if (context.isInstructionLevel() && resultBuilder != null) {
            resultBuilder.addState(newState.snapshot());
        }

        listeners.onAfterInstruction(instr, state, newState);

        return newState.nextInstruction();
    }

    private void notifyInstructionEvents(IRInstruction instr, SimulationState before, SimulationState after) {
        int pushCount = StateTransitions.getPushCount(instr);
        int popCount = StateTransitions.getPopCount(instr);

        for (int i = 0; i < popCount && i < before.stackDepth(); i++) {
            SimValue value = before.peek(i);
            if (value != null && !value.isWideSecondSlot()) {
                listeners.onStackPop(value, instr);
            }
        }

        for (int i = 0; i < pushCount && i < after.stackDepth(); i++) {
            SimValue value = after.peek(pushCount - 1 - i);
            if (value != null && !value.isWideSecondSlot()) {
                listeners.onStackPush(value, instr);
            }
        }

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
