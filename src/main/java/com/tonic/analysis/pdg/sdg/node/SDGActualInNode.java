package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
public class SDGActualInNode extends PDGNode {

    @Setter
    private SDGCallNode callNode;
    private final int parameterIndex;
    private final Value actualValue;

    public SDGActualInNode(int id, int parameterIndex, Value actualValue, IRBlock block) {
        super(id, PDGNodeType.ACTUAL_IN, block);
        this.parameterIndex = parameterIndex;
        this.actualValue = actualValue;
    }

    @Override
    public String getLabel() {
        String name = actualValue instanceof SSAValue
            ? ((SSAValue) actualValue).getName()
            : actualValue.toString();
        return "ACTUAL_IN:" + name;
    }

    @Override
    public List<Value> getUsedValues() {
        if (actualValue != null) {
            return Collections.singletonList(actualValue);
        }
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue() {
        return null;
    }

    public boolean isSSAValue() {
        return actualValue instanceof SSAValue;
    }

    public SSAValue getActualSSAValue() {
        if (actualValue instanceof SSAValue) {
            return (SSAValue) actualValue;
        }
        return null;
    }

    public String getParameterName() {
        if (actualValue instanceof SSAValue) {
            return ((SSAValue) actualValue).getName();
        }
        if (actualValue != null) {
            return actualValue.toString();
        }
        return "arg" + parameterIndex;
    }

    @Override
    public String toString() {
        String valueName = actualValue != null ? actualValue.toString() : "null";
        return String.format("SDGActualIn[%d: arg%d = %s]",
            getId(), parameterIndex, valueName);
    }
}
