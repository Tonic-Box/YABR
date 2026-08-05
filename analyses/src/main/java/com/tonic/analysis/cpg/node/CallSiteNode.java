package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;

/**
 * CPG node wrapping an invoke instruction, exposing its resolved target signature.
 */
public class CallSiteNode extends CPGNode
{

    private final InvokeInstruction invoke;
    private final String targetOwner;
    private final String targetName;
    private final String targetDescriptor;

    /**
     * Creates a node for an invoke instruction.
     * @param id the unique node id
     * @param invoke the wrapped invoke instruction
     */
    public CallSiteNode(long id, InvokeInstruction invoke)
    {
        super(id, CPGNodeType.CALL_SITE);
        this.invoke = invoke;
        this.targetOwner = invoke.getOwner();
        this.targetName = invoke.getName();
        this.targetDescriptor = invoke.getDescriptor();

        setProperty("targetOwner", targetOwner);
        setProperty("targetName", targetName);
        setProperty("targetDescriptor", targetDescriptor);
        setProperty("invokeType", invoke.getInvokeType().name());
    }

    /**
     * @return the invoke
     */
    public InvokeInstruction getInvoke()
    {
        return invoke;
    }

    /**
     * @return the target owner
     */
    public String getTargetOwner()
    {
        return targetOwner;
    }

    /**
     * @return the target name
     */
    public String getTargetName()
    {
        return targetName;
    }

    /**
     * @return the target descriptor
     */
    public String getTargetDescriptor()
    {
        return targetDescriptor;
    }

    @Override
    public String getLabel()
    {
        return targetOwner + "." + targetName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying()
    {
        return (T) invoke;
    }

    /**
     * @return the target as owner.name plus descriptor
     */
    public String getFullTarget()
    {
        return targetOwner + "." + targetName + targetDescriptor;
    }

    /**
     * @return the invoke dispatch kind
     */
    public InvokeType getInvokeType()
    {
        return invoke.getInvokeType();
    }

    /**
     * @return whether this is an invokestatic call
     */
    public boolean isStatic()
    {
        return invoke.getInvokeType() == InvokeType.STATIC;
    }

    /**
     * @return whether this is an invokevirtual call
     */
    public boolean isVirtual()
    {
        return invoke.getInvokeType() == InvokeType.VIRTUAL;
    }

    /**
     * @return whether this is an invokeinterface call
     */
    public boolean isInterface()
    {
        return invoke.getInvokeType() == InvokeType.INTERFACE;
    }

    /**
     * @return whether this is an invokespecial call
     */
    public boolean isSpecial()
    {
        return invoke.getInvokeType() == InvokeType.SPECIAL;
    }

    /**
     * @return the number of call arguments
     */
    public int getArgumentCount()
    {
        return invoke.getArguments().size();
    }

    @Override
    public String toString()
    {
        return String.format("CallSiteNode[%d: %s.%s]", getId(), targetOwner, targetName);
    }
}
