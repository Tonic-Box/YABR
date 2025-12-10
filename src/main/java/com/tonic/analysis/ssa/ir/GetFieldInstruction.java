package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Get field instruction (GETFIELD or GETSTATIC).
 */
@Getter
public class GetFieldInstruction extends IRInstruction {

    private final String owner;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;
    private Value objectRef;

    public GetFieldInstruction(SSAValue result, String owner, String name, String descriptor, Value objectRef) {
        super(result);
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = false;
        this.objectRef = objectRef;
        if (objectRef instanceof SSAValue) {
            SSAValue ssa = (SSAValue) objectRef;
            ssa.addUse(this);
        }
    }

    public GetFieldInstruction(SSAValue result, String owner, String name, String descriptor) {
        super(result);
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = true;
        this.objectRef = null;
    }

    @Override
    public List<Value> getOperands() {
        return objectRef != null ? List.of(objectRef) : List.of();
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (objectRef != null && objectRef.equals(oldValue)) {
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
        return visitor.visitGetField(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (isStatic) {
            return new GetFieldInstruction(newResult, owner, name, descriptor);
        }
        if (newOperands.isEmpty()) return null;
        return new GetFieldInstruction(newResult, owner, name, descriptor, newOperands.get(0));
    }

    @Override
    public String toString() {
        if (isStatic) {
            return result + " = getstatic " + owner + "." + name + " : " + descriptor;
        }
        return result + " = getfield " + objectRef + "." + name + " : " + descriptor;
    }
}
