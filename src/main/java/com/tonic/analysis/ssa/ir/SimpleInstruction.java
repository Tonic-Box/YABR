package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class SimpleInstruction extends IRInstruction {

    private final SimpleOp op;
    private Value operand;
    @Setter
    private IRBlock target;

    public static SimpleInstruction createArrayLength(SSAValue result, Value array) {
        return new SimpleInstruction(SimpleOp.ARRAYLENGTH, result, array, null);
    }

    public static SimpleInstruction createMonitorEnter(Value objectRef) {
        return new SimpleInstruction(SimpleOp.MONITORENTER, null, objectRef, null);
    }

    public static SimpleInstruction createMonitorExit(Value objectRef) {
        return new SimpleInstruction(SimpleOp.MONITOREXIT, null, objectRef, null);
    }

    public static SimpleInstruction createThrow(Value exception) {
        return new SimpleInstruction(SimpleOp.ATHROW, null, exception, null);
    }

    public static SimpleInstruction createGoto(IRBlock target) {
        return new SimpleInstruction(SimpleOp.GOTO, null, null, target);
    }

    private SimpleInstruction(SimpleOp op, SSAValue result, Value operand, IRBlock target) {
        super(result);
        this.op = op;
        this.operand = operand;
        this.target = target;
        if (operand instanceof SSAValue) {
            ((SSAValue) operand).addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        if (operand != null) {
            return List.of(operand);
        }
        return List.of();
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (operand != null && operand.equals(oldValue)) {
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
        return visitor.visitSimple(this);
    }

    @Override
    public boolean isTerminator() {
        return op == SimpleOp.ATHROW || op == SimpleOp.GOTO;
    }

    @Override
    public void replaceTarget(IRBlock oldTarget, IRBlock newTarget) {
        if (op == SimpleOp.GOTO && target == oldTarget) {
            target = newTarget;
        }
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        switch (op) {
            case ARRAYLENGTH:
                if (newOperands.isEmpty()) {
                    return null;
                }
                return createArrayLength(newResult, newOperands.get(0));
            case MONITORENTER:
                if (newOperands.isEmpty()) {
                    return null;
                }
                return createMonitorEnter(newOperands.get(0));
            case MONITOREXIT:
                if (newOperands.isEmpty()) {
                    return null;
                }
                return createMonitorExit(newOperands.get(0));
            case ATHROW:
                if (newOperands.isEmpty()) {
                    return null;
                }
                return createThrow(newOperands.get(0));
            case GOTO:
                return createGoto(target);
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        switch (op) {
            case ARRAYLENGTH:
                return result + " = arraylength " + operand;
            case MONITORENTER:
                return "monitorenter " + operand;
            case MONITOREXIT:
                return "monitorexit " + operand;
            case ATHROW:
                return "throw " + operand;
            case GOTO:
                return "goto " + (target != null ? target.getName() : "null");
            default:
                return "simple " + op;
        }
    }
}
