package com.tonic.analysis.dataflow;

/**
 * Types of edges in a data flow graph.
 */
public enum DataFlowEdgeType {
    DEF_USE("Definition-Use", "Value flows from definition to use"),
    PHI_INPUT("Phi Input", "Value flows into a phi node"),
    CALL_ARG("Call Argument", "Value passed as method argument"),
    CALL_RETURN("Call Return", "Value returned from method"),
    FIELD_STORE("Field Store", "Value stored to field"),
    FIELD_LOAD("Field Load", "Value loaded from field"),
    ARRAY_STORE("Array Store", "Value stored to array"),
    ARRAY_LOAD("Array Load", "Value loaded from array"),
    OPERAND("Operand", "Value used as operation operand");

    private final String displayName;
    private final String description;

    DataFlowEdgeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this edge type propagates taint.
     */
    public boolean propagatesTaint() {
        // All def-use edges propagate taint
        return true;
    }
}
