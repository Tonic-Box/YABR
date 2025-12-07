package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Conditional branch instruction.
 */
@Getter
public class BranchInstruction extends IRInstruction {

    private final CompareOp condition;
    private Value left;
    private Value right;
    @Setter
    private IRBlock trueTarget;
    @Setter
    private IRBlock falseTarget;

    public BranchInstruction(CompareOp condition, Value left, Value right, IRBlock trueTarget, IRBlock falseTarget) {
        super();
        this.condition = condition;
        this.left = left;
        this.right = right;
        this.trueTarget = trueTarget;
        this.falseTarget = falseTarget;
        registerUses();
    }

    public BranchInstruction(CompareOp condition, Value operand, IRBlock trueTarget, IRBlock falseTarget) {
        this(condition, operand, null, trueTarget, falseTarget);
    }

    private void registerUses() {
        if (left instanceof SSAValue ssa) ssa.addUse(this);
        if (right instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        List<Value> ops = new ArrayList<>();
        ops.add(left);
        if (right != null) ops.add(right);
        return ops;
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (left != null && left.equals(oldValue)) {
            if (left instanceof SSAValue ssa) ssa.removeUse(this);
            left = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
        if (right != null && right.equals(oldValue)) {
            if (right instanceof SSAValue ssa) ssa.removeUse(this);
            right = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitBranch(this);
    }

    @Override
    public boolean isTerminator() {
        return true;
    }

    @Override
    public String toString() {
        if (right == null) {
            return "if " + condition.name().toLowerCase() + " " + left + " goto " + trueTarget.getName() + " else " + falseTarget.getName();
        }
        return "if " + left + " " + condition.name().toLowerCase() + " " + right + " goto " + trueTarget.getName() + " else " + falseTarget.getName();
    }
}
