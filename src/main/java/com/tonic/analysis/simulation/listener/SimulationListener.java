package com.tonic.analysis.simulation.listener;

import com.tonic.analysis.simulation.core.SimulationResult;
import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.heap.AllocationSite;
import com.tonic.analysis.simulation.heap.EscapeAnalyzer;
import com.tonic.analysis.simulation.heap.FieldKey;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;

import java.util.Set;

/**
 * Listener interface for simulation events.
 *
 * <p>Implement this interface to receive callbacks during simulation execution.
 * All methods have default no-op implementations, so listeners can override
 * only the methods they care about.
 *
 * <p>Example usage:
 * <pre>
 * SimulationListener listener = new SimulationListener() {
 *     {@literal @}Override
 *     public void onStackPush(SimValue value, IRInstruction source) {
 *         System.out.println("Push: " + value);
 *     }
 * };
 * </pre>
 */
public interface SimulationListener {

    // ========== Lifecycle Events ==========

    /**
     * Called when simulation starts.
     *
     * @param method the IR method being simulated
     */
    default void onSimulationStart(IRMethod method) {}

    /**
     * Called when simulation ends.
     *
     * @param method the IR method that was simulated
     * @param result the simulation result
     */
    default void onSimulationEnd(IRMethod method, SimulationResult result) {}

    // ========== Block Events ==========

    /**
     * Called when entering a block.
     *
     * @param block the block being entered
     * @param state the simulation state at entry
     */
    default void onBlockEntry(IRBlock block, SimulationState state) {}

    /**
     * Called when exiting a block.
     *
     * @param block the block being exited
     * @param state the simulation state at exit
     */
    default void onBlockExit(IRBlock block, SimulationState state) {}

    // ========== Instruction Events ==========

    /**
     * Called before executing an instruction.
     *
     * @param instr the instruction about to execute
     * @param state the state before execution
     */
    default void onBeforeInstruction(IRInstruction instr, SimulationState state) {}

    /**
     * Called after executing an instruction.
     *
     * @param instr the instruction that executed
     * @param before the state before execution
     * @param after the state after execution
     */
    default void onAfterInstruction(IRInstruction instr, SimulationState before, SimulationState after) {}

    // ========== Stack Events ==========

    /**
     * Called when a value is pushed onto the stack.
     *
     * @param value the pushed value
     * @param source the instruction that produced the value
     */
    default void onStackPush(SimValue value, IRInstruction source) {}

    /**
     * Called when a value is popped from the stack.
     *
     * @param value the popped value
     * @param consumer the instruction consuming the value
     */
    default void onStackPop(SimValue value, IRInstruction consumer) {}

    // ========== Allocation Events ==========

    /**
     * Called when a new object is allocated.
     *
     * @param instr the NEW instruction
     * @param state the current state
     */
    default void onAllocation(NewInstruction instr, SimulationState state) {}

    /**
     * Called when a new array is allocated.
     *
     * @param instr the NEWARRAY instruction
     * @param state the current state
     */
    default void onArrayAllocation(NewArrayInstruction instr, SimulationState state) {}

    // ========== Field Access Events ==========

    /**
     * Called when a field is read.
     *
     * @param instr the GETFIELD/GETSTATIC instruction
     * @param state the current state
     */
    default void onFieldRead(GetFieldInstruction instr, SimulationState state) {}

    /**
     * Called when a field is written.
     *
     * @param instr the PUTFIELD/PUTSTATIC instruction
     * @param state the current state
     */
    default void onFieldWrite(PutFieldInstruction instr, SimulationState state) {}

    // ========== Array Access Events ==========

    /**
     * Called when an array element is read.
     *
     * @param instr the AALOAD/IALOAD/etc instruction
     * @param state the current state
     */
    default void onArrayRead(ArrayLoadInstruction instr, SimulationState state) {}

    /**
     * Called when an array element is written.
     *
     * @param instr the AASTORE/IASTORE/etc instruction
     * @param state the current state
     */
    default void onArrayWrite(ArrayStoreInstruction instr, SimulationState state) {}

    // ========== Control Flow Events ==========

    /**
     * Called when a branch instruction is encountered.
     *
     * @param instr the branch instruction
     * @param taken true if the branch is taken (for conditional branches)
     * @param state the current state
     */
    default void onBranch(BranchInstruction instr, boolean taken, SimulationState state) {}

    /**
     * Called when a switch instruction is encountered.
     *
     * @param instr the switch instruction
     * @param targetIndex the selected case index (-1 for default)
     * @param state the current state
     */
    default void onSwitch(SwitchInstruction instr, int targetIndex, SimulationState state) {}

    // ========== Method Call Events ==========

    /**
     * Called when a method is invoked.
     *
     * @param instr the invoke instruction
     * @param state the current state
     */
    default void onMethodCall(InvokeInstruction instr, SimulationState state) {}

    /**
     * Called when a method returns.
     *
     * @param instr the return instruction
     * @param state the current state
     */
    default void onMethodReturn(ReturnInstruction instr, SimulationState state) {}

    // ========== Exception Events ==========

    /**
     * Called when an exception is thrown.
     *
     * @param instr the throw instruction
     * @param state the current state
     */
    default void onException(ThrowInstruction instr, SimulationState state) {}

    // ========== Monitor Events ==========

    /**
     * Called when a monitor is entered.
     *
     * @param instr the monitor enter instruction
     * @param state the current state
     */
    default void onMonitorEnter(MonitorEnterInstruction instr, SimulationState state) {}

    /**
     * Called when a monitor is exited.
     *
     * @param instr the monitor exit instruction
     * @param state the current state
     */
    default void onMonitorExit(MonitorExitInstruction instr, SimulationState state) {}

    // ========== Heap Tracking Events ==========

    /**
     * Called when an object is allocated on the simulation heap.
     *
     * @param site the allocation site
     * @param ref the reference to the allocated object
     */
    default void onObjectAllocated(AllocationSite site, SimValue ref) {}

    /**
     * Called when an array is allocated on the simulation heap.
     *
     * @param site the allocation site
     * @param ref the reference to the allocated array
     * @param length the array length
     */
    default void onArrayAllocated(AllocationSite site, SimValue ref, SimValue length) {}

    /**
     * Called when a field is written on the simulation heap.
     *
     * @param objectRef the object reference
     * @param field the field being written
     * @param value the value being stored
     */
    default void onHeapFieldWrite(SimValue objectRef, FieldKey field, SimValue value) {}

    /**
     * Called when a field is read from the simulation heap.
     *
     * @param objectRef the object reference
     * @param field the field being read
     * @param values the possible values (may be multiple due to imprecision)
     */
    default void onHeapFieldRead(SimValue objectRef, FieldKey field, Set<SimValue> values) {}

    /**
     * Called when an array element is stored on the simulation heap.
     *
     * @param arrayRef the array reference
     * @param index the array index
     * @param value the value being stored
     */
    default void onHeapArrayStore(SimValue arrayRef, SimValue index, SimValue value) {}

    /**
     * Called when an array element is loaded from the simulation heap.
     *
     * @param arrayRef the array reference
     * @param index the array index
     * @param values the possible values
     */
    default void onHeapArrayLoad(SimValue arrayRef, SimValue index, Set<SimValue> values) {}

    /**
     * Called when an object escapes its allocation scope.
     *
     * @param site the allocation site
     * @param escapeState the escape state
     */
    default void onObjectEscaped(AllocationSite site, EscapeAnalyzer.EscapeState escapeState) {}

    /**
     * Called when an aliasing relationship is detected.
     *
     * @param ref1 first reference
     * @param ref2 second reference
     * @param mustAlias true if definitely aliased, false if may-alias
     */
    default void onAlias(SimValue ref1, SimValue ref2, boolean mustAlias) {}
}
