package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Load from local variable slot (used during lifting before SSA conversion).
 */
@Getter
public class LoadLocalInstruction extends IRInstruction {

    private final int localIndex;

    public LoadLocalInstruction(SSAValue result, int localIndex) {
        super(result);
        this.localIndex = localIndex;
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
        return visitor.visitLoadLocal(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        return new LoadLocalInstruction(newResult, localIndex);
    }

    @Override
    public String toString() {
        return result + " = load_local " + localIndex;
    }
}
