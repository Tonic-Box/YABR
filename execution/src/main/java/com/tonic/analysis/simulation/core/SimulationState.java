package com.tonic.analysis.simulation.core;

import com.tonic.analysis.simulation.heap.HeapMode;
import com.tonic.analysis.simulation.heap.SimHeap;
import com.tonic.analysis.simulation.state.LocalState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.simulation.state.StackState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.Objects;

/**
 * Immutable snapshot of execution state at a point during simulation.
 *
 * <p>SimulationState captures:
 * <ul>
 *   <li>Operand stack state</li>
 *   <li>Local variable state</li>
 *   <li>Current block and instruction position</li>
 *   <li>Call stack depth (for inter-procedural simulation)</li>
 * </ul>
 *
 * <p>All operations return new SimulationState instances, making state
 * safe to store in collections and compare across different execution points.
 */
public final class SimulationState {

    private final StackState stack;
    private final LocalState locals;
    private final IRBlock currentBlock;
    private final int instructionIndex;
    private final int callDepth;
    private final SimHeap heap;

    private SimulationState(StackState stack, LocalState locals,
                            IRBlock currentBlock, int instructionIndex, int callDepth) {
        this(stack, locals, currentBlock, instructionIndex, callDepth, new SimHeap(HeapMode.COPY_ON_MERGE));
    }

    private SimulationState(StackState stack, LocalState locals,
                            IRBlock currentBlock, int instructionIndex, int callDepth, SimHeap heap) {
        this.stack = stack;
        this.locals = locals;
        this.currentBlock = currentBlock;
        this.instructionIndex = instructionIndex;
        this.callDepth = callDepth;
        this.heap = heap;
    }

    // ========== Factory Methods ==========

    /**
     * Create an initial empty state.
     */
    public static SimulationState empty() {
        return new SimulationState(StackState.empty(), LocalState.empty(), null, 0, 0);
    }

    /**
     * Create an initial state for a method entry.
     */
    public static SimulationState forMethodEntry(IRBlock entryBlock, LocalState initialLocals) {
        return new SimulationState(StackState.empty(), initialLocals, entryBlock, 0, 0);
    }

    /**
     * Create state with specific stack and locals.
     */
    public static SimulationState of(StackState stack, LocalState locals) {
        return new SimulationState(stack, locals, null, 0, 0);
    }

    // ========== Stack Operations ==========

    /**
     * Push a value onto the stack.
     */
    public SimulationState push(SimValue value) {
        return new SimulationState(stack.push(value), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Push a wide value (long/double) onto the stack.
     */
    public SimulationState pushWide(SimValue value) {
        return new SimulationState(stack.pushWide(value), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Pop the top value from the stack.
     */
    public SimulationState pop() {
        return new SimulationState(stack.pop(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Pop multiple values from the stack.
     */
    public SimulationState pop(int count) {
        return new SimulationState(stack.pop(count), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Pop a wide value (2 slots) from the stack.
     */
    public SimulationState popWide() {
        return new SimulationState(stack.popWide(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Peek at the top value without removing it.
     */
    public SimValue peek() {
        return stack.peek();
    }

    /**
     * Peek at a value at the given depth (0 = top).
     */
    public SimValue peek(int depth) {
        return stack.peek(depth);
    }

    /**
     * Get the top value, accounting for wide types.
     * If top is a wide second slot, returns the value below it.
     */
    public SimValue peekValue() {
        return stack.peekValue();
    }

    /**
     * Get value at depth, accounting for wide types.
     */
    public SimValue peekValue(int depth) {
        return stack.peekValue(depth);
    }

    /**
     * Get the current stack depth.
     */
    public int stackDepth() {
        return stack.depth();
    }

    /**
     * Get the maximum stack depth seen during simulation.
     */
    public int maxStackDepth() {
        return stack.maxDepth();
    }

    /**
     * Duplicate top stack value (dup).
     */
    public SimulationState dup() {
        return new SimulationState(stack.dup(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate with insertion (dup_x1).
     */
    public SimulationState dupX1() {
        return new SimulationState(stack.dupX1(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate with insertion (dup_x2).
     */
    public SimulationState dupX2() {
        return new SimulationState(stack.dupX2(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate top two values (dup2).
     */
    public SimulationState dup2() {
        return new SimulationState(stack.dup2(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate two with insertion (dup2_x1).
     */
    public SimulationState dup2X1() {
        return new SimulationState(stack.dup2X1(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate two with insertion (dup2_x2).
     */
    public SimulationState dup2X2() {
        return new SimulationState(stack.dup2X2(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Swap top two values.
     */
    public SimulationState swap() {
        return new SimulationState(stack.swap(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Clear the stack (for exception handlers).
     */
    public SimulationState clearStack() {
        return new SimulationState(stack.clear(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    // ========== Local Variable Operations ==========

    /**
     * Set a local variable.
     */
    public SimulationState setLocal(int index, SimValue value) {
        return new SimulationState(stack, locals.set(index, value), currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Set a wide local variable (long/double).
     */
    public SimulationState setLocalWide(int index, SimValue value) {
        return new SimulationState(stack, locals.setWide(index, value), currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Get a local variable.
     */
    public SimValue getLocal(int index) {
        return locals.get(index);
    }

    /**
     * Check if a local variable is defined.
     */
    public boolean hasLocal(int index) {
        return locals.isDefined(index);
    }

    // ========== Position Operations ==========

    /**
     * Move to a new block.
     */
    public SimulationState atBlock(IRBlock block) {
        return new SimulationState(stack, locals, block, 0, callDepth, heap);
    }

    /**
     * Move to a specific instruction index.
     */
    public SimulationState atInstruction(int index) {
        return new SimulationState(stack, locals, currentBlock, index, callDepth, heap);
    }

    /**
     * Advance to the next instruction.
     */
    public SimulationState nextInstruction() {
        return new SimulationState(stack, locals, currentBlock, instructionIndex + 1, callDepth, heap);
    }

    /**
     * Enter a method call (increment call depth).
     */
    public SimulationState enterCall() {
        return new SimulationState(StackState.empty(), LocalState.empty(), null, 0, callDepth + 1, heap);
    }

    /**
     * Return from a method call (decrement call depth).
     */
    public SimulationState exitCall(SimulationState callerState) {
        return new SimulationState(callerState.stack, callerState.locals,
            callerState.currentBlock, callerState.instructionIndex, callDepth - 1, heap);
    }

    // ========== State Queries ==========

    /**
     * Get the operand stack state.
     */
    public StackState getStack() {
        return stack;
    }

    /**
     * Get the local variable state.
     */
    public LocalState getLocals() {
        return locals;
    }

    /**
     * Get the current block.
     */
    public IRBlock getCurrentBlock() {
        return currentBlock;
    }

    /**
     * Get the current instruction index within the block.
     */
    public int getInstructionIndex() {
        return instructionIndex;
    }

    /**
     * Get the call depth (0 = top-level).
     */
    public int getCallDepth() {
        return callDepth;
    }

    /**
     * Get the simulation heap.
     */
    public SimHeap getHeap() {
        return heap;
    }

    /**
     * Get the current instruction.
     */
    public IRInstruction getCurrentInstruction() {
        if (currentBlock == null) return null;
        var instructions = currentBlock.getInstructions();
        if (instructionIndex >= 0 && instructionIndex < instructions.size()) {
            return instructions.get(instructionIndex);
        }
        return null;
    }

    /**
     * Check if at the start of a block.
     */
    public boolean isAtBlockStart() {
        return instructionIndex == 0;
    }

    /**
     * Check if at the end of a block.
     */
    public boolean isAtBlockEnd() {
        if (currentBlock == null) return true;
        return instructionIndex >= currentBlock.getInstructions().size();
    }

    // ========== Merging ==========

    /**
     * Merge this state with another for control flow convergence.
     * Uses set-based union for heap merging.
     */
    public SimulationState merge(SimulationState other) {
        if (other == null) return this;
        StackState mergedStack = stack.merge(other.stack);
        LocalState mergedLocals = locals.merge(other.locals);
        SimHeap mergedHeap = heap.merge(other.heap);
        return new SimulationState(mergedStack, mergedLocals, currentBlock, instructionIndex, callDepth, mergedHeap);
    }

    /**
     * Create a snapshot of this state for storage.
     */
    public StateSnapshot snapshot() {
        return new StateSnapshot(this);
    }

    // ========== With Methods ==========

    /**
     * Create a new state with a different stack.
     */
    public SimulationState withStack(StackState newStack) {
        return new SimulationState(newStack, locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Create a new state with different locals.
     */
    public SimulationState withLocals(LocalState newLocals) {
        return new SimulationState(stack, newLocals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Create a new state with a different heap.
     */
    public SimulationState withHeap(SimHeap newHeap) {
        return new SimulationState(stack, locals, currentBlock, instructionIndex, callDepth, newHeap);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimulationState)) return false;
        SimulationState that = (SimulationState) o;
        return instructionIndex == that.instructionIndex &&
               callDepth == that.callDepth &&
               Objects.equals(stack, that.stack) &&
               Objects.equals(locals, that.locals) &&
               Objects.equals(currentBlock, that.currentBlock) &&
               Objects.equals(heap, that.heap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack, locals, currentBlock, instructionIndex, callDepth);
    }

    @Override
    public String toString() {
        return "SimulationState[" +
            "block=" + (currentBlock != null ? currentBlock.getId() : "null") +
            ", instr=" + instructionIndex +
            ", stack=" + stack.depth() +
            ", locals=" + locals.size() +
            ", heap=" + (heap.getObjectCount() + heap.getArrayCount()) + " objects" +
            ", depth=" + callDepth +
            "]";
    }
}
