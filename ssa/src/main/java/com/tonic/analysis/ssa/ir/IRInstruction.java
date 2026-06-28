package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * Base class for all IR instructions.
 */
public abstract class IRInstruction {

    private static final ThreadLocal<int[]> NEXT_ID = ThreadLocal.withInitial(() -> new int[1]);

    protected final int id;
    protected IRBlock block;
    protected SSAValue result;
    protected int bytecodeOffset = -1;

    protected IRInstruction() {
        this.id = NEXT_ID.get()[0]++;
    }

    protected IRInstruction(SSAValue result) {
        this.id = NEXT_ID.get()[0]++;
        this.result = result;
        if (result != null) {
            result.setDefinition(this);
        }
    }

    public int getId() {
        return id;
    }

    public IRBlock getBlock() {
        return block;
    }

    public void setBlock(IRBlock block) {
        this.block = block;
    }

    public SSAValue getResult() {
        return result;
    }

    public void setResult(SSAValue result) {
        this.result = result;
    }

    /**
     * Returns the originating bytecode offset, or {@code -1} when unknown. Stamped by the lifter so SSA
     * instructions can be correlated back to their source bytecode (e.g. data-flow provenance).
     */
    public int getBytecodeOffset() {
        return bytecodeOffset;
    }

    public void setBytecodeOffset(int bytecodeOffset) {
        this.bytecodeOffset = bytecodeOffset;
    }

    /**
     * Gets the operands used by this instruction.
     *
     * @return list of operand values
     */
    public abstract List<Value> getOperands();

    /**
     * Replaces an operand value with a new value.
     *
     * @param oldValue the value to replace
     * @param newValue the replacement value
     */
    public abstract void replaceOperand(Value oldValue, Value newValue);

    /**
     * Accepts a visitor for this instruction.
     *
     * @param visitor the visitor to accept
     * @param <T> the return type
     * @return the visitor result
     */
    public abstract <T> T accept(IRVisitor<T> visitor);

    /**
     * Checks if this instruction produces a result value.
     *
     * @return true if instruction has a result
     */
    public boolean hasResult() {
        return result != null;
    }

    /**
     * Gets the type of the result value.
     *
     * @return the result type, or null if no result
     */
    public IRType getResultType() {
        return result != null ? result.getType() : null;
    }

    /**
     * Checks if this instruction terminates a basic block.
     *
     * @return true if this is a terminator instruction
     */
    public boolean isTerminator() {
        return false;
    }

    /**
     * Checks if this is a phi instruction.
     *
     * @return true if this is a phi instruction
     */
    public boolean isPhi() {
        return false;
    }

    /**
     * Resets the instruction ID counter.
     */
    public static void resetIdCounter() {
        NEXT_ID.get()[0] = 0;
    }

    /**
     * Identity by {@code id}. Each instruction gets a unique id from the counter
     * (reset per lift in {@link com.tonic.analysis.ssa.lift.BytecodeLifter}; copies
     * receive fresh ids), so this matches object identity in every collection scope
     * while giving deterministic hashing/iteration order across runs.
     */
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof IRInstruction && ((IRInstruction) o).id == id);
    }

    @Override
    public int hashCode() {
        return id;
    }

    /**
     * Creates a copy of this instruction with new result and operands.
     * Subclasses should override for proper deep copying.
     *
     * @param newResult the new result value (may be null)
     * @param newOperands the new operand values
     * @return a copy of this instruction, or null if copying not supported
     */
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        return null;
    }

    /**
     * Replaces a target block in terminator instructions.
     * Only meaningful for branch/jump instructions.
     *
     * @param oldTarget the block to replace
     * @param newTarget the replacement block
     */
    public void replaceTarget(IRBlock oldTarget, IRBlock newTarget) {
    }
}
