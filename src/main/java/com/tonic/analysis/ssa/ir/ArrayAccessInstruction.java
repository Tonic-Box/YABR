package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

@Getter
public class ArrayAccessInstruction extends IRInstruction {

    private final AccessMode mode;
    private Value array;
    private Value index;
    private Value value;

    public static ArrayAccessInstruction createLoad(SSAValue result, Value array, Value index) {
        return new ArrayAccessInstruction(AccessMode.LOAD, result, array, index, null);
    }

    public static ArrayAccessInstruction createStore(Value array, Value index, Value value) {
        return new ArrayAccessInstruction(AccessMode.STORE, null, array, index, value);
    }

    private ArrayAccessInstruction(AccessMode mode, SSAValue result, Value array, Value index, Value value) {
        super(result);
        this.mode = mode;
        this.array = array;
        this.index = index;
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
        if (array instanceof SSAValue) {
            ((SSAValue) array).addUse(this);
        }
        if (index instanceof SSAValue) {
            ((SSAValue) index).addUse(this);
        }
        if (value instanceof SSAValue) {
            ((SSAValue) value).addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        if (mode == AccessMode.LOAD) {
            return List.of(array, index);
        }
        return List.of(array, index, value);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (array.equals(oldValue)) {
            if (array instanceof SSAValue) {
                ((SSAValue) array).removeUse(this);
            }
            array = newValue;
            if (newValue instanceof SSAValue) {
                ((SSAValue) newValue).addUse(this);
            }
        }
        if (index.equals(oldValue)) {
            if (index instanceof SSAValue) {
                ((SSAValue) index).removeUse(this);
            }
            index = newValue;
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
        return visitor.visitArrayAccess(this);
    }

    @Override
    public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
        if (mode == AccessMode.LOAD) {
            if (newOperands.size() < 2) {
                return null;
            }
            return createLoad(newResult, newOperands.get(0), newOperands.get(1));
        } else {
            if (newOperands.size() < 3) {
                return null;
            }
            return createStore(newOperands.get(0), newOperands.get(1), newOperands.get(2));
        }
    }

    @Override
    public String toString() {
        if (mode == AccessMode.LOAD) {
            return result + " = " + array + "[" + index + "]";
        }
        return array + "[" + index + "] = " + value;
    }
}
