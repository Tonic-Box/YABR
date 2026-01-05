package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import lombok.Getter;

@Getter
public class CallSiteNode extends CPGNode {

    private final InvokeInstruction invoke;
    private final String targetOwner;
    private final String targetName;
    private final String targetDescriptor;

    public CallSiteNode(long id, InvokeInstruction invoke) {
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

    @Override
    public String getLabel() {
        return targetOwner + "." + targetName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying() {
        return (T) invoke;
    }

    public String getFullTarget() {
        return targetOwner + "." + targetName + targetDescriptor;
    }

    public InvokeType getInvokeType() {
        return invoke.getInvokeType();
    }

    public boolean isStatic() {
        return invoke.getInvokeType() == InvokeType.STATIC;
    }

    public boolean isVirtual() {
        return invoke.getInvokeType() == InvokeType.VIRTUAL;
    }

    public boolean isInterface() {
        return invoke.getInvokeType() == InvokeType.INTERFACE;
    }

    public boolean isSpecial() {
        return invoke.getInvokeType() == InvokeType.SPECIAL;
    }

    public int getArgumentCount() {
        return invoke.getArguments().size();
    }

    @Override
    public String toString() {
        return String.format("CallSiteNode[%d: %s.%s]", getId(), targetOwner, targetName);
    }
}
