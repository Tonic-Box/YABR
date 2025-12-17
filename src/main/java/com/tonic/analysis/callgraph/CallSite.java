package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.ssa.ir.InvokeType;

import java.util.Objects;

/**
 * Represents a call site - a specific location where a method is invoked.
 * Contains the caller, the target, and metadata about the call.
 */
public class CallSite {

    private final MethodReference caller;
    private final MethodReference target;
    private final InvokeType invokeType;
    private final int bytecodeOffset;

    public CallSite(MethodReference caller, MethodReference target, InvokeType invokeType) {
        this(caller, target, invokeType, -1);
    }

    public CallSite(MethodReference caller, MethodReference target, InvokeType invokeType, int bytecodeOffset) {
        this.caller = caller;
        this.target = target;
        this.invokeType = invokeType;
        this.bytecodeOffset = bytecodeOffset;
    }

    public MethodReference getCaller() {
        return caller;
    }

    public MethodReference getTarget() {
        return target;
    }

    public InvokeType getInvokeType() {
        return invokeType;
    }

    public int getBytecodeOffset() {
        return bytecodeOffset;
    }

    /**
     * Checks if this is a virtual/interface call that may have multiple targets.
     */
    public boolean isPolymorphic() {
        return invokeType == InvokeType.VIRTUAL || invokeType == InvokeType.INTERFACE;
    }

    /**
     * Checks if this is a static call.
     */
    public boolean isStatic() {
        return invokeType == InvokeType.STATIC;
    }

    /**
     * Checks if this is a special (constructor/super/private) call.
     */
    public boolean isSpecial() {
        return invokeType == InvokeType.SPECIAL;
    }

    /**
     * Checks if this is an invokedynamic call.
     */
    public boolean isDynamic() {
        return invokeType == InvokeType.DYNAMIC;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallSite)) return false;
        CallSite callSite = (CallSite) o;
        return bytecodeOffset == callSite.bytecodeOffset &&
               Objects.equals(caller, callSite.caller) &&
               Objects.equals(target, callSite.target) &&
               invokeType == callSite.invokeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(caller, target, invokeType, bytecodeOffset);
    }

    @Override
    public String toString() {
        return caller + " -> " + target + " [" + invokeType + "]";
    }
}
