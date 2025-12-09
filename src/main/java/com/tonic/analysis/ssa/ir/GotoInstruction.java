package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Unconditional jump.
 */
@Getter
public class GotoInstruction extends IRInstruction {

    @Setter
    private IRBlock target;

    public GotoInstruction(IRBlock target) {
        super();
        this.target = target;
    }

    @Override
    public List<Value> getOperands() {
        return List.of();
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitGoto(this);
    }

    @Override
    public boolean isTerminator() {
        return true;
    }

    @Override
    public void replaceTarget(IRBlock oldTarget, IRBlock newTarget) {
        if (target == oldTarget) {
            target = newTarget;
        }
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        return new GotoInstruction(target);
    }

    @Override
    public String toString() {
        return "goto " + target.getName();
    }
}
