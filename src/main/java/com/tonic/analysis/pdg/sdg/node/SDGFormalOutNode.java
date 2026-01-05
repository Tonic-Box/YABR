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
public class SDGFormalOutNode extends PDGNode {

    @Setter
    private SDGEntryNode entryNode;
    private final SSAValue returnValue;
    private final String returnType;

    public SDGFormalOutNode(int id, SSAValue returnValue, String returnType, IRBlock exitBlock) {
        super(id, PDGNodeType.FORMAL_OUT, exitBlock);
        this.returnValue = returnValue;
        this.returnType = returnType;
    }

    @Override
    public String getLabel() {
        return "FORMAL_OUT:return";
    }

    @Override
    public List<Value> getUsedValues() {
        if (returnValue != null) {
            return Collections.singletonList(returnValue);
        }
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue() {
        return null;
    }

    public boolean hasReturnValue() {
        return returnValue != null;
    }

    public boolean isVoidReturn() {
        return "V".equals(returnType) || returnType == null;
    }

    public String getParameterName() {
        if (returnValue != null) {
            return returnValue.getName();
        }
        return "return";
    }

    @Override
    public String toString() {
        return String.format("SDGFormalOut[%d: %s]",
            getId(), returnType != null ? returnType : "void");
    }
}
