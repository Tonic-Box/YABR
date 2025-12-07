package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Base class for all IR instructions.
 */
@Getter
public abstract class IRInstruction {

    private static int nextId = 0;

    protected final int id;
    @Setter
    protected IRBlock block;
    @Setter
    protected SSAValue result;

    protected IRInstruction() {
        this.id = nextId++;
    }

    protected IRInstruction(SSAValue result) {
        this.id = nextId++;
        this.result = result;
        if (result != null) {
            result.setDefinition(this);
        }
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
        nextId = 0;
    }
}
