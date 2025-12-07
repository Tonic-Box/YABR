package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Instance check instruction (INSTANCEOF).
 */
@Getter
public class InstanceOfInstruction extends IRInstruction {

    private Value objectRef;
    private final IRType checkType;

    public InstanceOfInstruction(SSAValue result, Value objectRef, IRType checkType) {
        super(result);
        this.objectRef = objectRef;
        this.checkType = checkType;
        if (objectRef instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        return List.of(objectRef);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (objectRef.equals(oldValue)) {
            if (objectRef instanceof SSAValue ssa) ssa.removeUse(this);
            objectRef = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitInstanceOf(this);
    }

    @Override
    public String toString() {
        return result + " = " + objectRef + " instanceof " + checkType;
    }
}
