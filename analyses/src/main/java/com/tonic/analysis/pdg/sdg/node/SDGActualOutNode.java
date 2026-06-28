package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.List;

public class SDGActualOutNode extends PDGNode {

    private SDGCallNode callNode;
    private final SSAValue returnValue;

    public SDGActualOutNode(int id, SSAValue returnValue, IRBlock block) {
        super(id, PDGNodeType.ACTUAL_OUT, block);
        this.returnValue = returnValue;
    }

    public SDGCallNode getCallNode() {
        return callNode;
    }

    public void setCallNode(SDGCallNode callNode) {
        this.callNode = callNode;
    }

    public SSAValue getReturnValue() {
        return returnValue;
    }

    @Override
    public String getLabel() {
        if (returnValue != null) {
            return "ACTUAL_OUT:" + returnValue.getName();
        }
        return "ACTUAL_OUT";
    }

    @Override
    public List<Value> getUsedValues() {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue() {
        return returnValue;
    }

    public boolean hasReturnValue() {
        return returnValue != null;
    }

    public String getParameterName() {
        if (returnValue != null) {
            return returnValue.getName();
        }
        return "result";
    }

    @Override
    public String toString() {
        String valueName = returnValue != null ? returnValue.getName() : "void";
        return String.format("SDGActualOut[%d: %s]", getId(), valueName);
    }
}
