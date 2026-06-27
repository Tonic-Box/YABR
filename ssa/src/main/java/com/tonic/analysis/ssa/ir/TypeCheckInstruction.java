package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

@Getter
public class TypeCheckInstruction extends IRInstruction {

    private final TypeCheckOp op;
    private Value operand;
    private final IRType targetType;

    public static TypeCheckInstruction createCast(SSAValue result, Value operand, IRType targetType) {
        return new TypeCheckInstruction(TypeCheckOp.CAST, result, operand, targetType);
    }

    public static TypeCheckInstruction createInstanceOf(SSAValue result, Value operand, IRType checkType) {
        return new TypeCheckInstruction(TypeCheckOp.INSTANCEOF, result, operand, checkType);
    }

    private TypeCheckInstruction(TypeCheckOp op, SSAValue result, Value operand, IRType targetType) {
        super(result);
        this.op = op;
        this.operand = operand;
        this.targetType = targetType;
        if (operand instanceof SSAValue) {
            ((SSAValue) operand).addUse(this);
        }
    }

    public boolean isCast() {
        return op == TypeCheckOp.CAST;
    }

    public boolean isInstanceOf() {
        return op == TypeCheckOp.INSTANCEOF;
    }

    @Override
    public List<Value> getOperands() {
        return List.of(operand);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (operand.equals(oldValue)) {
            if (operand instanceof SSAValue) {
                ((SSAValue) operand).removeUse(this);
            }
            operand = newValue;
            if (newValue instanceof SSAValue) {
                ((SSAValue) newValue).addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitTypeCheck(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (newOperands.isEmpty()) {
            return null;
        }
        if (op == TypeCheckOp.CAST) {
            return createCast(newResult, newOperands.get(0), targetType);
        }
        return createInstanceOf(newResult, newOperands.get(0), targetType);
    }

    @Override
    public String toString() {
        if (op == TypeCheckOp.CAST) {
            return result + " = (" + targetType + ") " + operand;
        }
        return result + " = " + operand + " instanceof " + targetType;
    }
}
