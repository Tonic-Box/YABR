package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;

import java.util.List;

/**
 * Copy instruction (used for phi elimination and value copying).
 */
@Getter
public class CopyInstruction extends IRInstruction {

    private Value source;

    public CopyInstruction(SSAValue result, Value source) {
        super(result);
        this.source = source;
        if (source instanceof SSAValue) {
            SSAValue ssa = (SSAValue) source;
            ssa.addUse(this);
        }
    }

    @Override
    public List<Value> getOperands() {
        return List.of(source);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (source.equals(oldValue)) {
            if (source instanceof SSAValue) {
                SSAValue ssa = (SSAValue) source;
                ssa.removeUse(this);
            }
            source = newValue;
            if (newValue instanceof SSAValue) {
                SSAValue ssa = (SSAValue) newValue;
                ssa.addUse(this);
            }
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitCopy(this);
    }

    @Override
    public String toString() {
        return result + " = " + source;
    }
}
