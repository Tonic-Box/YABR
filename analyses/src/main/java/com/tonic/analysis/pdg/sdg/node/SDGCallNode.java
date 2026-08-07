package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.callgraph.CallSite;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * System dependence graph node for a call site, holding the actual-in nodes for its arguments,
 * an optional actual-out node, and a link to the callee entry once resolved.
 */
public class SDGCallNode extends PDGNode
{

    private final InvokeInstruction invokeInstruction;
    private final CallSite callSite;
    private final List<SDGActualInNode> actualIns = new ArrayList<>();
    private SDGActualOutNode actualOut;
    private SDGEntryNode targetEntry;

    /**
     * Creates a call-site node with no actual-in, actual-out or callee link yet.
     * @param id node id
     * @param invokeInstruction the invoke this node stands for
     * @param callSite call graph entry for the same invoke
     * @param block block containing the invoke
     */
    public SDGCallNode(int id, InvokeInstruction invokeInstruction, CallSite callSite, IRBlock block)
    {
        super(id, PDGNodeType.CALL_SITE, block);
        this.invokeInstruction = invokeInstruction;
        this.callSite = callSite;
    }

    /**
     * @return the invoke instruction
     */
    public InvokeInstruction getInvokeInstruction()
    {
        return invokeInstruction;
    }

    /**
     * @return the call site
     */
    public CallSite getCallSite()
    {
        return callSite;
    }

    /**
     * @return the actual out
     */
    public SDGActualOutNode getActualOut()
    {
        return actualOut;
    }

    /**
     * Attaches the node standing for the value this call returns.
     * @param actualOut actual-out node, or null for a void call
     */
    public void setActualOut(SDGActualOutNode actualOut)
    {
        this.actualOut = actualOut;
    }

    /**
     * @return the target entry
     */
    public SDGEntryNode getTargetEntry()
    {
        return targetEntry;
    }

    /**
     * Links this call to the entry node of the resolved callee.
     * @param targetEntry callee entry node
     */
    public void setTargetEntry(SDGEntryNode targetEntry)
    {
        this.targetEntry = targetEntry;
    }

    /**
     * Appends an argument node; no check is made for a duplicate parameter index.
     * @param actualIn node for one argument of this call
     */
    public void addActualIn(SDGActualInNode actualIn)
    {
        actualIns.add(actualIn);
    }

    /**
     * Looks up an attached actual-in node by the parameter position it feeds.
     * @param parameterIndex parameter position to find
     * @return the matching node, or null if none was added for that position
     */
    public SDGActualInNode getActualIn(int parameterIndex)
    {
        for (SDGActualInNode actualIn : actualIns)
        {
            if (actualIn.getParameterIndex() == parameterIndex)
            {
                return actualIn;
            }
        }
        return null;
    }

    /**
     * @return the number of actual-in nodes attached
     */
    public int getActualInCount()
    {
        return actualIns.size();
    }

    /**
     * @return an unmodifiable view of the actual-in nodes in the order they were added
     */
    public List<SDGActualInNode> getActualIns()
    {
        return Collections.unmodifiableList(actualIns);
    }

    /**
     * @return true if the call has a node for its returned value
     */
    public boolean hasActualOut()
    {
        return actualOut != null;
    }

    /**
     * @return true once the call has been linked to a callee entry node
     */
    public boolean hasTargetEntry()
    {
        return targetEntry != null;
    }

    /**
     * @return the owner of the invoked method
     */
    public String getTargetOwner()
    {
        return invokeInstruction.getOwner();
    }

    /**
     * @return the name of the invoked method
     */
    public String getTargetName()
    {
        return invokeInstruction.getName();
    }

    /**
     * @return the descriptor of the invoked method
     */
    public String getTargetDescriptor()
    {
        return invokeInstruction.getDescriptor();
    }

    @Override
    public String getLabel()
    {
        return "CALL:" + invokeInstruction.getName();
    }

    @Override
    public List<Value> getUsedValues()
    {
        return invokeInstruction.getOperands();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return invokeInstruction.getResult();
    }

    @Override
    public String toString()
    {
        return String.format("SDGCall[%d: %s.%s, %d args]",
            getId(), getTargetOwner(), getTargetName(), actualIns.size());
    }
}
