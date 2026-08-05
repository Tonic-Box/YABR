package com.tonic.analysis.dataflow;

/**
 * Types of edges in a data flow graph.
 */
public enum DataFlowEdgeType
{
    /**
     * A value flowing straight from the instruction that defines it to an
     * instruction that reads it.
     */
    DEF_USE("Definition-Use", "Value flows from definition to use"),
    /**
     * A value reaching a phi node along one of its incoming control flow
     * edges.
     */
    PHI_INPUT("Phi Input", "Value flows into a phi node"),
    /**
     * A value passed into an invocation as an argument, carrying it into the
     * callee.
     */
    CALL_ARG("Call Argument", "Value passed as method argument"),
    /**
     * A value produced by an invocation and flowing back out to the caller.
     */
    CALL_RETURN("Call Return", "Value returned from method"),
    /**
     * A value written into an instance or static field by a field store.
     */
    FIELD_STORE("Field Store", "Value stored to field"),
    /**
     * A value read out of an instance or static field by a field load.
     */
    FIELD_LOAD("Field Load", "Value loaded from field"),
    /**
     * A value written into an array element by an array store.
     */
    ARRAY_STORE("Array Store", "Value stored to array"),
    /**
     * A value read out of an array element by an array load.
     */
    ARRAY_LOAD("Array Load", "Value loaded from array"),
    /**
     * A value consumed as an operand of a computation, such as an arithmetic
     * or comparison instruction.
     */
    OPERAND("Operand", "Value used as operation operand");

    private final String displayName;
    private final String description;

    DataFlowEdgeType(String displayName, String description)
    {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * @return the display name
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Reports whether taint travels along this edge type.
     *
     * @return true - every data flow edge kind carries taint
     */
    public boolean propagatesTaint()
    {
        // All def-use edges propagate taint
        return true;
    }
}
