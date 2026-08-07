package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.ssa.ir.InvokeType;

import java.util.Objects;

/**
 * A specific location where one method invokes another, with the invoke type and bytecode offset.
 */
public class CallSite
{

    private final MethodReference caller;
    private final MethodReference target;
    private final InvokeType invokeType;
    private final int bytecodeOffset;

    /**
     * Creates a call site with no known bytecode offset.
     * @param caller the invoking method
     * @param target the invoked method
     * @param invokeType the kind of invocation
     */
    public CallSite(MethodReference caller, MethodReference target, InvokeType invokeType)
    {
        this(caller, target, invokeType, -1);
    }

    /**
     * Creates a call site at a known bytecode offset.
     * @param caller the invoking method
     * @param target the invoked method
     * @param invokeType the kind of invocation
     * @param bytecodeOffset the offset of the invoke instruction, or -1 if unknown
     */
    public CallSite(MethodReference caller, MethodReference target, InvokeType invokeType, int bytecodeOffset)
    {
        this.caller = caller;
        this.target = target;
        this.invokeType = invokeType;
        this.bytecodeOffset = bytecodeOffset;
    }

    /**
     * @return the caller
     */
    public MethodReference getCaller()
    {
        return caller;
    }

    /**
     * @return the target
     */
    public MethodReference getTarget()
    {
        return target;
    }

    /**
     * @return the invoke type
     */
    public InvokeType getInvokeType()
    {
        return invokeType;
    }

    /**
     * @return the bytecode offset
     */
    public int getBytecodeOffset()
    {
        return bytecodeOffset;
    }

    /**
     * @return true if this is a virtual/interface call that may have multiple targets
     */
    public boolean isPolymorphic()
    {
        return invokeType == InvokeType.VIRTUAL || invokeType == InvokeType.INTERFACE;
    }

    /**
     * @return true if this is a static call
     */
    public boolean isStatic()
    {
        return invokeType == InvokeType.STATIC;
    }

    /**
     * @return true if this is a special (constructor/super/private) call
     */
    public boolean isSpecial()
    {
        return invokeType == InvokeType.SPECIAL;
    }

    /**
     * @return true if this is an invokedynamic call
     */
    public boolean isDynamic()
    {
        return invokeType == InvokeType.DYNAMIC;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof CallSite)) return false;
        CallSite callSite = (CallSite) o;
        return bytecodeOffset == callSite.bytecodeOffset &&
               Objects.equals(caller, callSite.caller) &&
               Objects.equals(target, callSite.target) &&
               invokeType == callSite.invokeType;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(caller, target, invokeType, bytecodeOffset);
    }

    @Override
    public String toString()
    {
        return caller + " -> " + target + " [" + invokeType + "]";
    }
}
