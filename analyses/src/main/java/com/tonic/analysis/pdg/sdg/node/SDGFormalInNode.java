package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.List;

public class SDGFormalInNode extends PDGNode {

    private SDGEntryNode entryNode;
    private final int parameterIndex;
    private final SSAValue formalParameter;
    private final String parameterType;

    public SDGFormalInNode(int id, int parameterIndex, SSAValue formalParameter,
                           String parameterType, IRBlock entryBlock) {
        super(id, PDGNodeType.FORMAL_IN, entryBlock);
        this.parameterIndex = parameterIndex;
        this.formalParameter = formalParameter;
        this.parameterType = parameterType;
    }

    public SDGEntryNode getEntryNode() {
        return entryNode;
    }

    public void setEntryNode(SDGEntryNode entryNode) {
        this.entryNode = entryNode;
    }

    public int getParameterIndex() {
        return parameterIndex;
    }

    public SSAValue getFormalParameter() {
        return formalParameter;
    }

    public String getParameterType() {
        return parameterType;
    }

    @Override
    public String getLabel() {
        String name = formalParameter != null ? formalParameter.getName() : "param" + parameterIndex;
        return "FORMAL_IN:" + name;
    }

    @Override
    public List<Value> getUsedValues() {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue() {
        return formalParameter;
    }

    public boolean hasParameterType() {
        return parameterType != null;
    }

    public String getParameterName() {
        if (formalParameter != null) {
            return formalParameter.getName();
        }
        return "param" + parameterIndex;
    }

    @Override
    public String toString() {
        return String.format("SDGFormalIn[%d: param%d (%s)]",
            getId(), parameterIndex, parameterType != null ? parameterType : "?");
    }
}
