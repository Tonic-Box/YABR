package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;

import java.util.List;

/**
 * Binary arithmetic/logical operation.
 */
public class BinaryOpInstruction extends IRInstruction {

    private final BinaryOp op;
    private Value left;
    private Value right;

    public BinaryOpInstruction(SSAValue result, BinaryOp op, Value left, Value right) {
        super(result);
        this.op = op;
        this.left = left;
        this.right = right;
        registerUses();
    }

    private void registerUses() {
        if (left instanceof SSAValue) {
            SSAValue ssa = (SSAValue) left;
            ssa.addUse(this);
        }
        if (right instanceof SSAValue) {
            SSAValue ssa = (SSAValue) right;
            ssa.addUse(this);
        }
    }

    public BinaryOp getOp() {
        return op;
    }

    public Value getLeft() {
        return left;
    }

    public Value getRight() {
        return right;
    }

    @Override
    public List<Value> getOperands() {
        return List.of(left, right);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (left.equals(oldValue)) {
            if (left instanceof SSAValue) {
                SSAValue ssa = (SSAValue) left;
                ssa.removeUse(this);
            }
            left = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
        if (right.equals(oldValue)) {
            if (right instanceof SSAValue) {
                SSAValue ssa = (SSAValue) right;
                ssa.removeUse(this);
            }
            right = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitBinaryOp(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, java.util.List<Value> newOperands) {
        if (newOperands.size() < 2) return null;
        return new BinaryOpInstruction(newResult, op, newOperands.get(0), newOperands.get(1));
    }

    /**
     * Swaps the two operands in place. Valid only for commutative ops; the operand set is unchanged so the
     * use-lists need no update. Lets a canonical-ordering pass reorder {@code a op b} to {@code b op a}.
     */
    public void swapOperands() {
        Value tmp = left;
        left = right;
        right = tmp;
    }

    @Override
    public String toString() {
        return result + " = " + op.name().toLowerCase() + " " + left + ", " + right;
    }
}
