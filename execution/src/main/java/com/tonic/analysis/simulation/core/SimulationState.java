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
 * Immutable snapshot of stack, locals, heap and position at one point during simulation.
 */
public final class SimulationState
{

    private final StackState stack;
    private final LocalState locals;
    private final IRBlock currentBlock;
    private final int instructionIndex;
    private final int callDepth;
    private final SimHeap heap;

    private SimulationState(StackState stack, LocalState locals, IRBlock currentBlock, int instructionIndex, int callDepth)
    {
        this(stack, locals, currentBlock, instructionIndex, callDepth, new SimHeap(HeapMode.COPY_ON_MERGE));
    }

    private SimulationState(StackState stack, LocalState locals, IRBlock currentBlock, int instructionIndex, int callDepth, SimHeap heap)
    {
        this.stack = stack;
        this.locals = locals;
        this.currentBlock = currentBlock;
        this.instructionIndex = instructionIndex;
        this.callDepth = callDepth;
        this.heap = heap;
    }

    // Factory Methods

    /**
     * Create an initial empty state.
     * @return a state with an empty stack, empty locals and no position
     */
    public static SimulationState empty()
    {
        return new SimulationState(StackState.empty(), LocalState.empty(), null, 0, 0);
    }

    /**
     * Create an initial state for a method entry.
     * @param entryBlock the block execution starts in
     * @param initialLocals the locals holding the incoming parameters
     * @return a state positioned at the entry block's first instruction with an empty stack
     */
    public static SimulationState forMethodEntry(IRBlock entryBlock, LocalState initialLocals)
    {
        return new SimulationState(StackState.empty(), initialLocals, entryBlock, 0, 0);
    }

    /**
     * Create state with specific stack and locals.
     * @param stack the operand stack
     * @param locals the local variables
     * @return a state holding them, with no position
     */
    public static SimulationState of(StackState stack, LocalState locals)
    {
        return new SimulationState(stack, locals, null, 0, 0);
    }

    // Stack Operations

    /**
     * Push a value onto the stack.
     * @param value the value to push
     * @return a new state with the value on top
     */
    public SimulationState push(SimValue value)
    {
        return new SimulationState(stack.push(value), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Push a wide value (long/double) onto the stack.
     * @param value the value to push
     * @return a new state with the value occupying two slots
     */
    public SimulationState pushWide(SimValue value)
    {
        return new SimulationState(stack.pushWide(value), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Pop the top value from the stack.
     * @return a new state with the top slot removed
     */
    public SimulationState pop()
    {
        return new SimulationState(stack.pop(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Pop multiple values from the stack.
     * @param count how many slots to remove
     * @return a new state with that many slots removed
     */
    public SimulationState pop(int count)
    {
        return new SimulationState(stack.pop(count), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Pop a wide value (2 slots) from the stack.
     * @return a new state with both slots removed
     */
    public SimulationState popWide()
    {
        return new SimulationState(stack.popWide(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Peek at the top value without removing it.
     * @return the top stack value
     */
    public SimValue peek()
    {
        return stack.peek();
    }

    /**
     * Peek at a value at the given depth (0 = top).
     * @param depth slots below the top
     * @return the value in that slot
     */
    public SimValue peek(int depth)
    {
        return stack.peek(depth);
    }

    /**
     * Get the top value, accounting for wide types.
     * @return the topmost whole value
     */
    public SimValue peekValue()
    {
        return stack.peekValue();
    }

    /**
     * Get value at depth, accounting for wide types.
     * @param depth slots below the top
     * @return the whole value at that depth
     */
    public SimValue peekValue(int depth)
    {
        return stack.peekValue(depth);
    }

    /**
     * Get the current stack depth.
     * @return the number of occupied stack slots
     */
    public int stackDepth()
    {
        return stack.depth();
    }

    /**
     * Get the maximum stack depth seen during simulation.
     * @return the high-water mark of occupied stack slots
     */
    public int maxStackDepth()
    {
        return stack.maxDepth();
    }

    /**
     * Duplicate top stack value (dup).
     * @return a new state with the top slot copied
     */
    public SimulationState dup()
    {
        return new SimulationState(stack.dup(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate with insertion (dup_x1).
     * @return a new state with the top slot copied two slots down
     */
    public SimulationState dupX1()
    {
        return new SimulationState(stack.dupX1(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate with insertion (dup_x2).
     * @return a new state with the top slot copied three slots down
     */
    public SimulationState dupX2()
    {
        return new SimulationState(stack.dupX2(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate top two values (dup2).
     * @return a new state with the top two slots copied
     */
    public SimulationState dup2()
    {
        return new SimulationState(stack.dup2(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate two with insertion (dup2_x1).
     * @return a new state with the top two slots copied three slots down
     */
    public SimulationState dup2X1()
    {
        return new SimulationState(stack.dup2X1(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Duplicate two with insertion (dup2_x2).
     * @return a new state with the top two slots copied four slots down
     */
    public SimulationState dup2X2()
    {
        return new SimulationState(stack.dup2X2(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Swap top two values.
     * @return a new state with the top two slots exchanged
     */
    public SimulationState swap()
    {
        return new SimulationState(stack.swap(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Clear the stack (for exception handlers).
     * @return a new state with an empty stack and the locals kept
     */
    public SimulationState clearStack()
    {
        return new SimulationState(stack.clear(), locals, currentBlock, instructionIndex, callDepth, heap);
    }

    // Local Variable Operations

    /**
     * Set a local variable.
     * @param index the local slot
     * @param value the value to store
     * @return a new state with that slot bound
     */
    public SimulationState setLocal(int index, SimValue value)
    {
        return new SimulationState(stack, locals.set(index, value), currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Set a wide local variable (long/double).
     * @param index the first of the two local slots
     * @param value the value to store
     * @return a new state with both slots bound
     */
    public SimulationState setLocalWide(int index, SimValue value)
    {
        return new SimulationState(stack, locals.setWide(index, value), currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Get a local variable.
     * @param index the local slot
     * @return the value in that slot
     */
    public SimValue getLocal(int index)
    {
        return locals.get(index);
    }

    /**
     * Check if a local variable is defined.
     * @param index the local slot
     * @return true when the slot holds a value
     */
    public boolean hasLocal(int index)
    {
        return locals.isDefined(index);
    }

    // Position Operations

    /**
     * Move to a new block.
     * @param block the block to enter
     * @return a new state positioned at that block's first instruction
     */
    public SimulationState atBlock(IRBlock block)
    {
        return new SimulationState(stack, locals, block, 0, callDepth, heap);
    }

    /**
     * Move to a specific instruction index.
     * @param index the instruction index within the current block
     * @return a new state positioned there
     */
    public SimulationState atInstruction(int index)
    {
        return new SimulationState(stack, locals, currentBlock, index, callDepth, heap);
    }

    /**
     * Advance to the next instruction.
     * @return a new state with the instruction index incremented
     */
    public SimulationState nextInstruction()
    {
        return new SimulationState(stack, locals, currentBlock, instructionIndex + 1, callDepth, heap);
    }

    /**
     * Enter a method call (increment call depth).
     * @return a new state with a fresh stack and locals at one deeper call level, sharing the heap
     */
    public SimulationState enterCall()
    {
        return new SimulationState(StackState.empty(), LocalState.empty(), null, 0, callDepth + 1, heap);
    }

    /**
     * Return from a method call (decrement call depth).
     * @param callerState the state captured before the call was entered
     * @return a new state restoring the caller's stack, locals and position, keeping this heap
     */
    public SimulationState exitCall(SimulationState callerState)
    {
        return new SimulationState(callerState.stack, callerState.locals,
            callerState.currentBlock, callerState.instructionIndex, callDepth - 1, heap);
    }

    // State Queries

    /**
     * @return the operand stack state
     */
    public StackState getStack()
    {
        return stack;
    }

    /**
     * @return the local variable state
     */
    public LocalState getLocals()
    {
        return locals;
    }

    /**
     * @return the block being executed, or null when the state has no position
     */
    public IRBlock getCurrentBlock()
    {
        return currentBlock;
    }

    /**
     * @return the instruction index within the current block
     */
    public int getInstructionIndex()
    {
        return instructionIndex;
    }

    /**
     * @return the call depth (0 = top-level)
     */
    public int getCallDepth()
    {
        return callDepth;
    }

    /**
     * @return the simulation heap
     */
    public SimHeap getHeap()
    {
        return heap;
    }

    /**
     * Get the instruction at the current position.
     * @return the instruction, or null when there is no block or the index is out of range
     */
    public IRInstruction getCurrentInstruction()
    {
        if (currentBlock == null) return null;
        var instructions = currentBlock.getInstructions();
        if (instructionIndex >= 0 && instructionIndex < instructions.size())
        {
            return instructions.get(instructionIndex);
        }
        return null;
    }

    /**
     * Check if at the start of a block.
     * @return true when the instruction index is 0
     */
    public boolean isAtBlockStart()
    {
        return instructionIndex == 0;
    }

    /**
     * Check if at the end of a block.
     * @return true when there is no block, or the index is past its last instruction
     */
    public boolean isAtBlockEnd()
    {
        if (currentBlock == null) return true;
        return instructionIndex >= currentBlock.getInstructions().size();
    }

    // Merging

    /**
     * Merge this state with another for control flow convergence.
     * @param other the incoming state, may be null
     * @return the merged state, or this one when other is null; this state's position is kept
     */
    public SimulationState merge(SimulationState other)
    {
        if (other == null) return this;
        StackState mergedStack = stack.merge(other.stack);
        LocalState mergedLocals = locals.merge(other.locals);
        SimHeap mergedHeap = heap.merge(other.heap);
        return new SimulationState(mergedStack, mergedLocals, currentBlock, instructionIndex, callDepth, mergedHeap);
    }

    /**
     * Create a snapshot of this state for storage.
     * @return a snapshot wrapping this state
     */
    public StateSnapshot snapshot()
    {
        return new StateSnapshot(this);
    }

    // With Methods

    /**
     * Create a new state with a different stack.
     * @param newStack the replacement operand stack
     * @return a new state with everything else unchanged
     */
    public SimulationState withStack(StackState newStack)
    {
        return new SimulationState(newStack, locals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Create a new state with different locals.
     * @param newLocals the replacement local variables
     * @return a new state with everything else unchanged
     */
    public SimulationState withLocals(LocalState newLocals)
    {
        return new SimulationState(stack, newLocals, currentBlock, instructionIndex, callDepth, heap);
    }

    /**
     * Create a new state with a different heap.
     * @param newHeap the replacement heap
     * @return a new state with everything else unchanged
     */
    public SimulationState withHeap(SimHeap newHeap)
    {
        return new SimulationState(stack, locals, currentBlock, instructionIndex, callDepth, newHeap);
    }

    @Override
    public boolean equals(Object o)
    {
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
    public int hashCode()
    {
        return Objects.hash(stack, locals, currentBlock, instructionIndex, callDepth);
    }

    @Override
    public String toString()
    {
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
