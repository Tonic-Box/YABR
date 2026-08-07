package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collections;
import java.util.List;

/**
 * System dependence graph node for a method's returned value at its exit; it uses the returned
 * SSA value and defines nothing.
 */
public class SDGFormalOutNode extends PDGNode
{

    private SDGEntryNode entryNode;
    private final SSAValue returnValue;
    private final String returnType;

    /**
     * Creates a formal-out node that is not yet linked to its entry node.
     * @param id node id
     * @param returnValue SSA value returned, or null for a void method
     * @param returnType return descriptor, may be null
     * @param exitBlock the method's exit block
     */
    public SDGFormalOutNode(int id, SSAValue returnValue, String returnType, IRBlock exitBlock)
    {
        super(id, PDGNodeType.FORMAL_OUT, exitBlock);
        this.returnValue = returnValue;
        this.returnType = returnType;
    }

    /**
     * @return the entry node
     */
    public SDGEntryNode getEntryNode()
    {
        return entryNode;
    }

    /**
     * Links this return back to the entry node of its owning method.
     * @param entryNode owning method's entry node
     */
    public void setEntryNode(SDGEntryNode entryNode)
    {
        this.entryNode = entryNode;
    }

    /**
     * @return the return value
     */
    public SSAValue getReturnValue()
    {
        return returnValue;
    }

    /**
     * @return the return type
     */
    public String getReturnType()
    {
        return returnType;
    }

    @Override
    public String getLabel()
    {
        return "FORMAL_OUT:return";
    }

    @Override
    public List<Value> getUsedValues()
    {
        if (returnValue != null)
        {
            return Collections.singletonList(returnValue);
        }
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return null;
    }

    /**
     * @return true if an SSA value was bound as the returned value
     */
    public boolean hasReturnValue()
    {
        return returnValue != null;
    }

    /**
     * @return true if the return descriptor is "V" or was never recorded
     */
    public boolean isVoidReturn()
    {
        return "V".equals(returnType) || returnType == null;
    }

    /**
     * @return the returned SSA value's name, or "return" when no value is bound
     */
    public String getParameterName()
    {
        if (returnValue != null)
        {
            return returnValue.getName();
        }
        return "return";
    }

    @Override
    public String toString()
    {
        return String.format("SDGFormalOut[%d: %s]", getId(), returnType != null ? returnType : "void");
    }
}
