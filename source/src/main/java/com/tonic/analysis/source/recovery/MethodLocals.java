package com.tonic.analysis.source.recovery;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.List;

/**
 * The parameter slot layout of a method: which local slots hold the receiver and parameters, and how those map
 * to parameter indices. Computed once from the method's SSA parameter list - which includes the receiver as the
 * first entry for an instance method - so the {@code long}/{@code double} two-slot accounting lives in exactly
 * one place rather than being re-derived in the recovery context, the slot partition and the method recoverer.
 */
public final class MethodLocals
{

    private final List<SSAValue> parameters;
    private final boolean isStatic;
    private final int[] slotOfParam;
    private final int parameterSlotCount;

    /**
     * Walks the method's SSA parameter list once to assign each entry its local
     * slot, counting long and double as two.
     *
     * @param method the method whose slot layout to compute
     */
    public MethodLocals(IRMethod method)
    {
        this.parameters = method.getParameters();
        this.isStatic = method.isStatic();
        this.slotOfParam = new int[parameters.size()];
        int slot = 0;
        for (int i = 0; i < parameters.size(); i++)
        {
            slotOfParam[i] = slot;
            slot += isWide(parameters.get(i).getType()) ? 2 : 1;
        }
        this.parameterSlotCount = slot;
    }

    /**
     * @return the number of local slots the receiver and parameters occupy, so the first body local's index
     */
    public int parameterSlotCount()
    {
        return parameterSlotCount;
    }

    /**
     * @param value the SSA value to locate
     * @return the local slot the parameter or receiver occupies, or -1 when the value is neither
     */
    public int slotOfParameter(SSAValue value)
    {
        int idx = parameters.indexOf(value);
        return idx < 0 ? -1 : slotOfParam[idx];
    }

    /**
     * @param slot the local slot
     * @return true when the slot starts the receiver or a parameter rather than a body local
     */
    public boolean isParameterOrThisSlot(int slot)
    {
        for (int s : slotOfParam)
        {
            if (s == slot)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The zero-based parameter index for a parameter start {@code slot} (receiver excluded).
     *
     * @param slot the local slot
     * @return the parameter index, or -1 if the slot starts no parameter
     */
    public int parameterIndexForSlot(int slot)
    {
        for (int i = 0; i < slotOfParam.length; i++)
        {
            if (slotOfParam[i] == slot)
            {
                return isStatic ? i : i - 1;
            }
        }
        return -1;
    }

    private static boolean isWide(IRType type)
    {
        return type instanceof PrimitiveType
                && (((PrimitiveType) type) == PrimitiveType.LONG || ((PrimitiveType) type) == PrimitiveType.DOUBLE);
    }
}
