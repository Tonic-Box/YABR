package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Type cast instruction (CHECKCAST).
 */
@Getter
public class CastInstruction extends IRInstruction {

    private Value objectRef;
    private final IRType targetType;

    public CastInstruction(SSAValue result, Value objectRef, IRType targetType) {
        super(result);
        this.objectRef = objectRef;
        this.targetType = targetType;
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
        return visitor.visitCast(this);
    }

    @Override
    public String toString() {
        return result + " = (" + targetType + ") " + objectRef;
    }
}
