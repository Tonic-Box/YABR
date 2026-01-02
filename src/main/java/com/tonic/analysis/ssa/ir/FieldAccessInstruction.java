package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class FieldAccessInstruction extends IRInstruction {

    private final AccessMode mode;
    private final String owner;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;
    private Value objectRef;
    private Value value;

    public static FieldAccessInstruction createLoad(SSAValue result, String owner, String name,
                                                    String descriptor, Value objectRef) {
        return new FieldAccessInstruction(AccessMode.LOAD, result, owner, name, descriptor, false, objectRef, null);
    }

    public static FieldAccessInstruction createStaticLoad(SSAValue result, String owner, String name,
                                                          String descriptor) {
        return new FieldAccessInstruction(AccessMode.LOAD, result, owner, name, descriptor, true, null, null);
    }

    public static FieldAccessInstruction createStore(String owner, String name, String descriptor,
                                                     Value objectRef, Value value) {
        return new FieldAccessInstruction(AccessMode.STORE, null, owner, name, descriptor, false, objectRef, value);
    }

    public static FieldAccessInstruction createStaticStore(String owner, String name, String descriptor,
                                                           Value value) {
        return new FieldAccessInstruction(AccessMode.STORE, null, owner, name, descriptor, true, null, value);
    }

    private FieldAccessInstruction(AccessMode mode, SSAValue result, String owner, String name,
                                   String descriptor, boolean isStatic, Value objectRef, Value value) {
        super(result);
        this.mode = mode;
        this.owner = owner;
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.objectRef = objectRef;
        this.value = value;
        registerUses();
    }

    public boolean isLoad() {
        return mode == AccessMode.LOAD;
    }

    public boolean isStore() {
        return mode == AccessMode.STORE;
    }

    private void registerUses() {
        if (objectRef instanceof SSAValue) {
            ((SSAValue) objectRef).addUse(this);
        }
        if (value instanceof SSAValue) {
            ((SSAValue) value).addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        List<Value> ops = new ArrayList<>();
        if (objectRef != null) {
            ops.add(objectRef);
        }
        if (value != null) {
            ops.add(value);
        }
        return ops;
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (objectRef != null && objectRef.equals(oldValue)) {
            if (objectRef instanceof SSAValue) {
                ((SSAValue) objectRef).removeUse(this);
            }
            objectRef = newValue;
            if (newValue instanceof SSAValue) {
                ((SSAValue) newValue).addUse(this);
            }
        }
        if (value != null && value.equals(oldValue)) {
            if (value instanceof SSAValue) {
                ((SSAValue) value).removeUse(this);
            }
            value = newValue;
            if (newValue instanceof SSAValue) {
                ((SSAValue) newValue).addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitFieldAccess(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (mode == AccessMode.LOAD) {
            if (isStatic) {
                return createStaticLoad(newResult, owner, name, descriptor);
            }
            if (newOperands.isEmpty()) {
                return null;
            }
            return createLoad(newResult, owner, name, descriptor, newOperands.get(0));
        } else {
            if (isStatic) {
                if (newOperands.isEmpty()) {
                    return null;
                }
                return createStaticStore(owner, name, descriptor, newOperands.get(0));
            }
            if (newOperands.size() < 2) {
                return null;
            }
            return createStore(owner, name, descriptor, newOperands.get(0), newOperands.get(1));
        }
    }

    @Override
    public String toString() {
        if (mode == AccessMode.LOAD) {
            if (isStatic) {
                return result + " = getstatic " + owner + "." + name + " : " + descriptor;
            }
            return result + " = getfield " + objectRef + "." + name + " : " + descriptor;
        } else {
            if (isStatic) {
                return "putstatic " + owner + "." + name + " : " + descriptor + " = " + value;
            }
            return "putfield " + objectRef + "." + name + " : " + descriptor + " = " + value;
        }
    }
}
