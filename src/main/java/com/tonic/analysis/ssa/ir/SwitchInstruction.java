package com.tonic.analysis.ssa.ir;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.analysis.ssa.visitor.IRVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Switch instruction (tableswitch or lookupswitch).
 */
@Getter
public class SwitchInstruction extends IRInstruction {

    private Value key;
    @Setter
    private IRBlock defaultTarget;
    private final Map<Integer, IRBlock> cases;

    public SwitchInstruction(Value key, IRBlock defaultTarget) {
        super();
        this.key = key;
        this.defaultTarget = defaultTarget;
        this.cases = new LinkedHashMap<>();
        if (key instanceof SSAValue ssa) ssa.addUse(this);
    }

    /**
     * Adds a case to the switch instruction.
     *
     * @param value the case value
     * @param target the target block for this case
     */
    public void addCase(int value, IRBlock target) {
        cases.put(value, target);
    }

    /**
     * Gets the target block for a specific case value.
     *
     * @param value the case value
     * @return the target block, or default target if not found
     */
    public IRBlock getCase(int value) {
        return cases.getOrDefault(value, defaultTarget);
    }

    @Override
    public List<Value> getOperands() {
        return List.of(key);
    }

    @Override
    public void replaceOperand(Value oldValue, Value newValue) {
        if (key.equals(oldValue)) {
            if (key instanceof SSAValue ssa) ssa.removeUse(this);
            key = newValue;
            if (newValue instanceof SSAValue ssa) ssa.addUse(this);
        }
    }

    @Override
    public <T> T accept(IRVisitor<T> visitor) {
        return visitor.visitSwitch(this);
    }

    @Override
    public boolean isTerminator() {
        return true;
    }

    @Override
    public void replaceTarget(IRBlock oldTarget, IRBlock newTarget) {
        if (defaultTarget == oldTarget) {
            defaultTarget = newTarget;
        }
        for (Map.Entry<Integer, IRBlock> entry : cases.entrySet()) {
            if (entry.getValue() == oldTarget) {
                entry.setValue(newTarget);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("switch ").append(key).append(" {\n");
        for (Map.Entry<Integer, IRBlock> entry : cases.entrySet()) {
            sb.append("  case ").append(entry.getKey()).append(": goto ").append(entry.getValue().getName()).append("\n");
        }
        sb.append("  default: goto ").append(defaultTarget.getName()).append("\n}");
        return sb.toString();
    }
}
