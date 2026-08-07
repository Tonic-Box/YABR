package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.List;

/**
 * System dependence graph node for one formal parameter at a method entry; it defines the
 * parameter's SSA value and uses nothing.
 */
public class SDGFormalInNode extends PDGNode
{

    private SDGEntryNode entryNode;
    private final int parameterIndex;
    private final SSAValue formalParameter;
    private final String parameterType;

    /**
     * Creates a formal-in node that is not yet linked to its entry node.
     * @param id node id
     * @param parameterIndex zero-based parameter position
     * @param formalParameter SSA value defined for the parameter, may be null
     * @param parameterType parameter descriptor, may be null
     * @param entryBlock the method's entry block
     */
    public SDGFormalInNode(int id, int parameterIndex, SSAValue formalParameter, String parameterType, IRBlock entryBlock)
    {
        super(id, PDGNodeType.FORMAL_IN, entryBlock);
        this.parameterIndex = parameterIndex;
        this.formalParameter = formalParameter;
        this.parameterType = parameterType;
    }

    /**
     * @return the entry node
     */
    public SDGEntryNode getEntryNode()
    {
        return entryNode;
    }

    /**
     * Links this parameter back to the entry node of its owning method.
     * @param entryNode owning method's entry node
     */
    public void setEntryNode(SDGEntryNode entryNode)
    {
        this.entryNode = entryNode;
    }

    /**
     * @return the parameter index
     */
    public int getParameterIndex()
    {
        return parameterIndex;
    }

    /**
     * @return the formal parameter
     */
    public SSAValue getFormalParameter()
    {
        return formalParameter;
    }

    /**
     * @return the parameter type
     */
    public String getParameterType()
    {
        return parameterType;
    }

    @Override
    public String getLabel()
    {
        String name = formalParameter != null ? formalParameter.getName() : "param" + parameterIndex;
        return "FORMAL_IN:" + name;
    }

    @Override
    public List<Value> getUsedValues()
    {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return formalParameter;
    }

    /**
     * @return true if a descriptor was recorded for this parameter
     */
    public boolean hasParameterType()
    {
        return parameterType != null;
    }

    /**
     * @return the SSA value's name, or "paramN" when no formal value is bound
     */
    public String getParameterName()
    {
        if (formalParameter != null)
        {
            return formalParameter.getName();
        }
        return "param" + parameterIndex;
    }

    @Override
    public String toString()
    {
        return String.format("SDGFormalIn[%d: param%d (%s)]",
            getId(), parameterIndex, parameterType != null ? parameterType : "?");
    }
}
