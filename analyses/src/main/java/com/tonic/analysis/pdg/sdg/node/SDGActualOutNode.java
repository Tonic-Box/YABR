package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.List;

/**
 * SDG node for the value a call site receives back from the callee.
 */
public class SDGActualOutNode extends PDGNode
{

    private SDGCallNode callNode;
    private final SSAValue returnValue;

    /**
     * Creates an actual-out node; the owning call node is set separately.
     * @param id the node id
     * @param returnValue the value the call defines, or null for a void call
     * @param block the block holding the call
     */
    public SDGActualOutNode(int id, SSAValue returnValue, IRBlock block)
    {
        super(id, PDGNodeType.ACTUAL_OUT, block);
        this.returnValue = returnValue;
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
     * @return the return value
     */
    public SSAValue getReturnValue()
    {
        return returnValue;
    }

    @Override
    public String getLabel()
    {
        if (returnValue != null)
        {
            return "ACTUAL_OUT:" + returnValue.getName();
        }
        return "ACTUAL_OUT";
    }

    @Override
    public List<Value> getUsedValues()
    {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return returnValue;
    }

    /**
     * @return true if the call produces a value rather than returning void
     */
    public boolean hasReturnValue()
    {
        return returnValue != null;
    }

    /**
     * Names the returned value for display, falling back to "result" for a void call.
     * @return the display name
     */
    public String getParameterName()
    {
        if (returnValue != null)
        {
            return returnValue.getName();
        }
        return "result";
    }

    @Override
    public String toString()
    {
        String valueName = returnValue != null ? returnValue.getName() : "void";
        return String.format("SDGActualOut[%d: %s]", getId(), valueName);
    }
}
