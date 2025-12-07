package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Unary operation (negation, type conversion).
 */
@Getter
public class UnaryOpInstruction extends IRInstruction {

    private final UnaryOp op;
    private Value operand;

    public UnaryOpInstruction(SSAValue result, UnaryOp op, Value operand) {
        super(result);
        this.op = op;
        this.operand = operand;
        if (operand instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        return List.of(operand);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (operand.equals(oldValue)) {
            if (operand instanceof SSAValue ssa) ssa.removeUse(this);
            operand = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitUnaryOp(this);
    }

    @Override
    public String toString() {
        return result + " = " + op.name().toLowerCase() + " " + operand;
    }
}
