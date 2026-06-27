package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class SDGEntryNode extends PDGNode {

    private final MethodReference methodRef;
    @Setter
    private PDG procedurePDG;
    private final List<SDGFormalInNode> formalIns = new ArrayList<>();
    @Setter
    private SDGFormalOutNode formalOut;

    public SDGEntryNode(int id, MethodReference methodRef, IRBlock entryBlock) {
        super(id, PDGNodeType.ENTRY, entryBlock);
        this.methodRef = methodRef;
    }

    public void addFormalIn(SDGFormalInNode formalIn) {
        formalIns.add(formalIn);
    }

    public SDGFormalInNode getFormalIn(int parameterIndex) {
        for (SDGFormalInNode formalIn : formalIns) {
            if (formalIn.getParameterIndex() == parameterIndex) {
                return formalIn;
            }
        }
        return null;
    }

    public int getFormalInCount() {
        return formalIns.size();
    }

    public List<SDGFormalInNode> getFormalIns() {
        return Collections.unmodifiableList(formalIns);
    }

    public boolean hasFormalOut() {
        return formalOut != null;
    }

    @Override
    public String getLabel() {
        return "ENTRY:" + methodRef.getName();
    }

    @Override
    public List<Value> getUsedValues() {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue() {
        return null;
    }

    public String getFullSignature() {
        return methodRef.getOwner() + "." + methodRef.getName() + methodRef.getDescriptor();
    }

    public String getMethodName() {
        return methodRef.getOwner() + "." + methodRef.getName();
    }

    @Override
    public String toString() {
        return String.format("SDGEntry[%d: %s, %d params]",
            getId(), methodRef.getName(), formalIns.size());
    }
}
