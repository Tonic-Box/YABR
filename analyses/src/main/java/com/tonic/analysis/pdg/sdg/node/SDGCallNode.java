package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class SDGCallNode extends PDGNode {

    private final InvokeInstruction invokeInstruction;
    private final CallSite callSite;
    private final List<SDGActualInNode> actualIns = new ArrayList<>();
    @Setter
    private SDGActualOutNode actualOut;
    @Setter
    private SDGEntryNode targetEntry;

    public SDGCallNode(int id, InvokeInstruction invokeInstruction, CallSite callSite, IRBlock block) {
        super(id, PDGNodeType.CALL_SITE, block);
        this.invokeInstruction = invokeInstruction;
        this.callSite = callSite;
    }

    public void addActualIn(SDGActualInNode actualIn) {
        actualIns.add(actualIn);
    }

    public SDGActualInNode getActualIn(int parameterIndex) {
        for (SDGActualInNode actualIn : actualIns) {
            if (actualIn.getParameterIndex() == parameterIndex) {
                return actualIn;
            }
        }
        return null;
    }

    public int getActualInCount() {
        return actualIns.size();
    }

    public List<SDGActualInNode> getActualIns() {
        return Collections.unmodifiableList(actualIns);
    }

    public boolean hasActualOut() {
        return actualOut != null;
    }

    public boolean hasTargetEntry() {
        return targetEntry != null;
    }

    public String getTargetOwner() {
        return invokeInstruction.getOwner();
    }

    public String getTargetName() {
        return invokeInstruction.getName();
    }

    public String getTargetDescriptor() {
        return invokeInstruction.getDescriptor();
    }

    @Override
    public String getLabel() {
        return "CALL:" + invokeInstruction.getName();
    }

    @Override
    public List<Value> getUsedValues() {
        return invokeInstruction.getOperands();
    }

    @Override
    public SSAValue getDefinedValue() {
        return invokeInstruction.getResult();
    }

    @Override
    public String toString() {
        return String.format("SDGCall[%d: %s.%s, %d args]",
            getId(), getTargetOwner(), getTargetName(), actualIns.size());
    }
}
