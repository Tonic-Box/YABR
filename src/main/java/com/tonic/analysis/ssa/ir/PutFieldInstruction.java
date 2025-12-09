package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Put field instruction (PUTFIELD or PUTSTATIC).
 */
@Getter
public class PutFieldInstruction extends IRInstruction {

    private final String owner;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;
    private Value objectRef;
    private Value value;

    public PutFieldInstruction(String owner, String name, String descriptor, Value objectRef, Value value) {
        super();
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = false;
        this.objectRef = objectRef;
        this.value = value;
        registerUses();
    }

    public PutFieldInstruction(String owner, String name, String descriptor, Value value) {
        super();
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = true;
        this.objectRef = null;
        this.value = value;
        if (value instanceof SSAValue ssa) ssa.addUse(this);
    }

    private void registerUses() {
        if (objectRef instanceof SSAValue ssa) ssa.addUse(this);
        if (value instanceof SSAValue ssa) ssa.addUse(this);
    }

    @Override
    public List<Value> getOperands() {
        List<Value> ops = new ArrayList<>();
        if (objectRef != null) ops.add(objectRef);
        ops.add(value);
        return ops;
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (objectRef != null && objectRef.equals(oldValue)) {
            if (objectRef instanceof SSAValue ssa) ssa.removeUse(this);
            objectRef = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
        if (value.equals(oldValue)) {
            if (value instanceof SSAValue ssa) ssa.removeUse(this);
            value = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitPutField(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (isStatic) {
            if (newOperands.isEmpty()) return null;
            return new PutFieldInstruction(owner, name, descriptor, newOperands.get(0));
        }
        if (newOperands.size() < 2) return null;
        return new PutFieldInstruction(owner, name, descriptor, newOperands.get(0), newOperands.get(1));
    }

    @Override
    public String toString() {
        if (isStatic) {
            return "putstatic " + owner + "." + name + " : " + descriptor + " = " + value;
        }
        return "putfield " + objectRef + "." + name + " : " + descriptor + " = " + value;
    }
}
