package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Monitor enter instruction (MONITORENTER).
 */
@Getter
public class MonitorEnterInstruction extends IRInstruction {

    private Value objectRef;

    public MonitorEnterInstruction(Value objectRef) {
        super();
        this.objectRef = objectRef;
        if (objectRef instanceof SSAValue) {
            SSAValue ssa = (SSAValue) objectRef;
            ssa.addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        return List.of(objectRef);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (objectRef.equals(oldValue)) {
            if (objectRef instanceof SSAValue) {
                SSAValue ssa = (SSAValue) objectRef;
                ssa.removeUse(this);
            }
            objectRef = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitMonitorEnter(this);
    }

    @Override
    public String toString() {
        return "monitorenter " + objectRef;
    }
}
