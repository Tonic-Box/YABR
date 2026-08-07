package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.List;

/**
 * SDG node for one actual argument passed at a call site.
 */
public class SDGActualInNode extends PDGNode
{

    private SDGCallNode callNode;
    private final int parameterIndex;
    private final Value actualValue;

    /**
     * Creates an actual-in node; the owning call node is set separately.
     * @param id the node id
     * @param parameterIndex the zero-based argument position
     * @param actualValue the value passed at the call site
     * @param block the block holding the call
     */
    public SDGActualInNode(int id, int parameterIndex, Value actualValue, IRBlock block)
    {
        super(id, PDGNodeType.ACTUAL_IN, block);
        this.parameterIndex = parameterIndex;
        this.actualValue = actualValue;
    }

    /**
     * @return the call node
     */
    public SDGCallNode getCallNode()
    {
        return callNode;
    }

    /**
     * Links this node back to the call site that owns it.
     * @param callNode the owning call node
     */
    public void setCallNode(SDGCallNode callNode)
    {
        this.callNode = callNode;
    }

    /**
     * @return the parameter index
     */
    public int getParameterIndex()
    {
        return parameterIndex;
    }

    /**
     * @return the actual value
     */
    public Value getActualValue()
    {
        return actualValue;
    }

    @Override
    public String getLabel()
    {
        String name = actualValue instanceof SSAValue
            ? ((SSAValue) actualValue).getName()
            : actualValue.toString();
        return "ACTUAL_IN:" + name;
    }

    @Override
    public List<Value> getUsedValues()
    {
        if (actualValue != null)
        {
            return Collections.singletonList(actualValue);
        }
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return null;
    }

    /**
     * @return true if the actual argument is an SSA value rather than a constant
     */
    public boolean isSSAValue()
    {
        return actualValue instanceof SSAValue;
    }

    /**
     * Narrows the actual argument to an SSA value.
     * @return the SSA value, or null if the argument is not one
     */
    public SSAValue getActualSSAValue()
    {
        if (actualValue instanceof SSAValue)
        {
            return (SSAValue) actualValue;
        }
        return null;
    }

    /**
     * Names the argument for display, falling back to argN when the value has no name.
     * @return the display name
     */
    public String getParameterName()
    {
        if (actualValue instanceof SSAValue)
        {
            return ((SSAValue) actualValue).getName();
        }
        if (actualValue != null)
        {
            return actualValue.toString();
        }
        return "arg" + parameterIndex;
    }

    @Override
    public String toString()
    {
        String valueName = actualValue != null ? actualValue.toString() : "null";
        return String.format("SDGActualIn[%d: arg%d = %s]", getId(), parameterIndex, valueName);
    }
}
